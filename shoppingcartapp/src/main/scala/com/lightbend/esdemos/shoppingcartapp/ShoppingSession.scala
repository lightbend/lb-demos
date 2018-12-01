package com.lightbend.esdemos.shoppingcartapp

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.http.scaladsl.model.StatusCodes
import com.lightbend.cinnamon.akka.CinnamonMetrics
import com.lightbend.cinnamon.metric.{Counter, GaugeLong, Recorder}
import com.lightbend.esdemos.shoppingcartapp.ShoppingCart._

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

object ShoppingSession {
  def props(userId: String, clusterListener: ActorRef): Props = Props(new ShoppingSession(userId, clusterListener))

  case class UpdateCatalog(products: List[Catalog.Product])

  /** represents possible shopping actions */
  object ShoppingAction extends Enumeration {
    val ClearCart, QueryProductCatalog, QueryShoppingCart, AddToCart, CommitCart, Unknown = Value
  }

  case object GetClusterNodes

  case object ScheduleNextAction

}

class ShoppingSession(userId: String, clusterListener: ActorRef) extends Actor with ActorLogging {

  import ShoppingSession._
  import context.dispatcher

  val DurationMean: FiniteDuration = context.system.settings.config.getInt("userSim.session.duration-mean-seconds").seconds
  val DurationStdDev: FiniteDuration = context.system.settings.config.getInt("userSim.session.duration-stddev-seconds").seconds
  val CartItemsMean: Int = context.system.settings.config.getInt("userSim.session.cart-items-mean")
  val CartItemsStdDev: Int = context.system.settings.config.getInt("userSim.session.cart-items-stddev")
  val ClusterConnectInterval: FiniteDuration = context.system.settings.config.getInt("userSim.session.cluster-connect-interval-seconds").seconds
  val Port: Int = context.system.settings.config.getInt("es.demos.http.server.port")

  val CartCommitLatency: Recorder = CinnamonMetrics(context).createRecorder("cartCommitLatency")
  val SuccessfulSessionMetric: Counter = CinnamonMetrics(context).createCounter("successfulSessions")
  val ActiveSessionsMetric: Counter = CinnamonMetrics(context).createCounter("activeSessions")
  var failedSessionsMetricTable: Map[(ShoppingAction.Value, String), Counter] = Map()

  var shoppingClient: ShoppingHttpClient = _
  var shoppingPlan: List[(ShoppingAction.Value, FiniteDuration)] = initShoppingPlan()
  var catalog: IndexedSeq[Catalog.Product] = IndexedSeq()
  var currentScheduler: Option[Cancellable] = None
  var cartItems: Map[String, Int] = Map()
  var cartTimestamp: Long = 0

  override def preStart(): Unit = {
    ActiveSessionsMetric.increment()
    context.system.scheduler.scheduleOnce(ClusterConnectInterval, self, GetClusterNodes)
  }

  override def postStop(): Unit = {
    currentScheduler.foreach(_.cancel())
    ActiveSessionsMetric.decrement()
  }

  /**
    * initialize simulation session.  the session template is mapped out as follows
    * 0) clear shopping cart
    * 1) query product catalog
    * 2) add cart items // this step can be repeated multiple times depending on the numbe of items
    * 3) query cart
    * 4) commit cart
    */
  def initShoppingPlan(): List[(ShoppingAction.Value, FiniteDuration)] = {
    val sessionDuration = math.max(0.0, Random.nextGaussian() * DurationStdDev.toSeconds + DurationMean.toSeconds)
    val remainingCartItems = math.max(1, (Random.nextGaussian() * CartItemsStdDev + CartItemsMean).toInt)

    // calculate random intervals between remaining actions and scale them to the session duration
    // number of remaining actions is number of items to add plus query cart action plus commit cart action
    val numRemainingActions = remainingCartItems + 2
    val intervals = (1 to numRemainingActions).map(_ => Random.nextFloat())
    val scaleFactor = sessionDuration / intervals.sum
    val scaledIntervals = intervals.map(i => (i * scaleFactor * 1000.0).millis).toList

    // construct actions
    val remainingActions = (1 to remainingCartItems)
      .map(_ => ShoppingAction.AddToCart) ++ Seq(ShoppingAction.QueryShoppingCart, ShoppingAction.CommitCart)

    (ShoppingAction.ClearCart, 0.seconds) ::
      (ShoppingAction.QueryProductCatalog, 0.seconds) ::
      remainingActions.zip(scaledIntervals).toList
  }

  def receive: Receive = initSession

  def initSession: Receive = {
    case GetClusterNodes =>
      clusterListener ! ClusterListener.GetMembers
    case ClusterListener.ClusterMembers(members) =>
      if (members.nonEmpty) {
        val randomHost = members.toIndexedSeq(util.Random.nextInt(members.size))
        log.debug(s"shopping session created to talk to $randomHost:$Port")
        shoppingClient = new ShoppingHttpClient(randomHost, Port)(context.system)
        context.become(startSession)
        scheduleNextAction()
      } else {
        context.system.scheduler.scheduleOnce(ClusterConnectInterval, self, GetClusterNodes)
      }
  }

  def startSession: Receive = {
    case UpdateCatalog(products) =>
      catalog = products.toIndexedSeq
    case ScheduleNextAction =>
      scheduleNextAction()
    case action: ShoppingAction.Value =>
      handleShoppingAction(action)
    case msg =>
      log.warning(s"unexpected message: {}", msg)
  }

  /** schedules next shopping action */
  def scheduleNextAction(): Unit = {
    if (shoppingPlan.nonEmpty) {
      val (nextAction, waitTime) = shoppingPlan.head
      currentScheduler = Some(context.system.scheduler.scheduleOnce(waitTime, self, nextAction))
      shoppingPlan = shoppingPlan.tail
    } else {
      // should never encounter this.  log error and shut down actor just in case
      log.error("unexpected end of shopping plan, shutting session down")
      failedSessionMetric(ShoppingAction.Unknown, "empty shopping plan").increment()
      context.stop(self)
    }
  }

  /** executes shopping action */
  def handleShoppingAction(action: ShoppingAction.Value): Unit = action match {
    case ShoppingAction.ClearCart =>
      shoppingClient.clearCart(userId).onComplete {
        case Success(response) =>
          if (response.status.intValue == StatusCodes.OK.intValue) {
            self ! ScheduleNextAction
          } else {
            failSession(ShoppingAction.ClearCart, s"error clearing cart with status: ${response.status}")
          }
        case Failure(ex) =>
          failSession(ShoppingAction.ClearCart, "error clearing cart", Some(ex))
      }
    case ShoppingAction.QueryProductCatalog =>
      shoppingClient.queryProductCatalog().onComplete {
        case Success(products) =>
          self ! UpdateCatalog(products)
          self ! ScheduleNextAction
        case Failure(ex) =>
          failSession(ShoppingAction.QueryProductCatalog, "error querying product catalog", Some(ex))
      }
    case ShoppingAction.AddToCart =>
      // pick product and update local cart
      val productId = catalog(Random.nextInt(catalog.size)).id
      cartItems = cartItems.get(productId) match {
        case Some(count) => cartItems.updated(productId, count + 1)
        case None => cartItems.updated(productId, 1)
      }

      // update server
      shoppingClient.addToCart(userId, List(productId)).onComplete {
        case Success(response) =>
          if (response.status.intValue == StatusCodes.Accepted.intValue) {
            self ! ScheduleNextAction
          } else {
            failSession(ShoppingAction.AddToCart, s"error adding product to cart with status: ${response.status}")
          }
        case Failure(ex) =>
          failSession(ShoppingAction.AddToCart, "error adding product to cart", Some(ex))
      }
    case ShoppingAction.QueryShoppingCart =>
      shoppingClient.queryShoppingCart(userId).onComplete {
        case Success(cartContent) =>
          if (cartContent.items == cartItems) {
            cartTimestamp = cartContent.timestamp
            self ! ScheduleNextAction
          } else {
            failSession(ShoppingAction.QueryShoppingCart, s"shopping cart doesn't match")
          }
        case Failure(ex) =>
          failSession(ShoppingAction.QueryShoppingCart, "error querying cart", Some(ex))
      }
    case ShoppingAction.CommitCart =>
      val commitStart = System.currentTimeMillis()
      shoppingClient.commitCart(CartId(userId, cartTimestamp)).onComplete {
        case Success(response) =>
          CartCommitLatency.record(System.currentTimeMillis() - commitStart)
          if (response.status.intValue == StatusCodes.OK.intValue) {
            SuccessfulSessionMetric.increment()
            context.stop(self)
          } else if (response.status.intValue == StatusCodes.Conflict.intValue) {
            failSession(ShoppingAction.CommitCart, "cart timestamp out of date or empty cart")
          } else {
            failSession(ShoppingAction.CommitCart, s"error committing to cart with status: ${response.status}")
          }
        case Failure(ex) =>
          failSession(ShoppingAction.CommitCart, "error committing cart", Some(ex))
      }
  }

  /** fails session */
  def failSession(stage: ShoppingAction.Value, message: String, ex: Option[Throwable] = None): Unit = {
    failedSessionMetric(stage, message).increment()
    ex match {
      case Some(e) => log.error(e, message)
      case None => log.error(message)
    }
    context.stop(self)
  }

  /** retrieves the failed session metric for the class of failure if available, creates one if not */
  def failedSessionMetric(stage: ShoppingAction.Value, reason: String): Counter = {
    failedSessionsMetricTable.get((stage, reason)) match {
      case Some(counter) =>
        counter
      case None =>
        val counter = CinnamonMetrics(context)
          .createCounter("failedSessions", Map("stage" -> stage.toString, "reason" -> reason))
        failedSessionsMetricTable = failedSessionsMetricTable.updated((stage, reason), counter)
        counter
    }
  }
}
