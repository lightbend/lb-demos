package com.lightbend.esdemos.shoppingcartapp

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.Success

object ShoppingCartManager {
  /** props for ShoppingCartManager */
  def props(catalog: ActorRef): Props = Props(new ShoppingCartManager(catalog))

}

/**
  * entry point for managing shopping carts
  *
  * @param  catalog product catalog
  *
  */
class ShoppingCartManager(catalog: ActorRef) extends Actor with ActorLogging {

  import ShoppingCart._
  import context.dispatcher

  val NumberOfShards: Int = context.system.settings.config.getInt("es.demos.shoppingCartManager.number-of-shards")
  val NumberOfRetries: Int = context.system.settings.config.getInt("es.demos.shoppingCartManager.num-retries")

  val RetryInterval: FiniteDuration =
    context.system.settings.config.getInt("es.demos.shoppingCartManager.retry-interval-millis").millis

  val RequestTimeout: FiniteDuration =
    context.system.settings.config.getInt("es.demos.http.server.request-timeout").millis

  val cluster = Cluster(context.system)
  implicit val timeout: Timeout = Timeout(RequestTimeout)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: ShoppingCartMessage => (msg.userId, msg)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: ShoppingCartMessage => (math.abs(msg.userId.hashCode) % NumberOfShards).toString
  }

  /** transaction authenticator */
  val authenticator = context.actorOf(TransactionAuthenticator.props)

  /** shopping cart region def */
  val shoppingCartRegion: ActorRef = ClusterSharding(context.system).start(
    typeName = classOf[ShoppingCart].getName,
    entityProps = ShoppingCart.props(authenticator),
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  override def receive: Receive = {
    case msg: AddToCart =>
      validateAndAddProducts(msg)
    case msg: GetCartContent =>
      handleGetCartContent(msg)
    case msg: ShoppingCartMessage =>
      // forward message to appropriate shopping cart
      shoppingCartRegion.tell(msg, sender)
    case msg =>
      log.warning(s"unexpected message: {}", msg)
  }

  /** validates and add products to cart */
  def validateAndAddProducts(msg: AddToCart): Unit = {
    val originalSender = sender
    (catalog ? Catalog.ValidateProductIds(msg.products.toSet)).onComplete {
      case Success(Catalog.ProductIdsValid) =>
        shoppingCartRegion.tell(msg, originalSender)
      case Success(Catalog.InvalidIds(invalidIds)) =>
        // remove invalid product ids, we still want to add the valid ones
        val filteredIds = msg.products.filterNot(id => invalidIds.contains(id))
        shoppingCartRegion.tell(AddToCart(msg.userId, filteredIds), originalSender)
      case other =>
        log.error("unexpected response from ProductCatalog: {}", other)
    }
  }

  /** handles get cart */
  def handleGetCartContent(msg: GetCartContent): Unit = {
    shoppingCartRegion.tell(msg, sender)
  }
}
