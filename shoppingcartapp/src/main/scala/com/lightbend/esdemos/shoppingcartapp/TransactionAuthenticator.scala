package com.lightbend.esdemos.shoppingcartapp

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.Random

object TransactionAuthenticator {
  /** props for TransactionAuthenticator */
  def props: Props = Props(new TransactionAuthenticator)

  /** message to request authentication for cart id */
  case class Authenticate(userId: String)

  /** authenticated response */
  case class Authenticated(userId: String)

  /** not authenticated response */
  case class NotAuthenticated(userId: String) extends RuntimeException(s"$userId is not authenticated")

  /** authentication failure */
  case class AuthenticationFailure(ex: Throwable)
}

/**
  * models transaction authentication
  */
class TransactionAuthenticator extends Actor with ActorLogging {
  import TransactionAuthenticator._

  val TransactionMean: FiniteDuration = context.system.settings.config.getLong("es.demos.transactionAuthenticator.transaction-mean-millis").millis
  val TransactionStdDev: FiniteDuration = context.system.settings.config.getLong("es.demos.transactionAuthenticator.transaction-std-dev-millis").millis

  /** random transaction duration */
  protected def txDuration: FiniteDuration =
    math.max(0, Random.nextGaussian() * TransactionStdDev.toMillis + TransactionMean.toMillis).toLong.millis

  override def receive: Receive = {
    case Authenticate(userId) =>
      handleAuthenticate(userId)
    case msg =>
      log.warning(s"unexpected message: {}", msg)
  }

  /** handles authenticate message */
  protected def handleAuthenticate(userId: String): Unit = {
   try {
     // simulate authentication latency
     Thread.sleep(txDuration.toMillis)
     sender ! Authenticated(userId)
   } catch {
     case ex: Throwable =>
       sender ! AuthenticationFailure(ex)
   }
  }
}
