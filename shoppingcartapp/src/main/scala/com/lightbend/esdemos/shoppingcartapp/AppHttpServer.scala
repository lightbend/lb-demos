package com.lightbend.esdemos.shoppingcartapp

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.lightbend.esdemos.shoppingcartapp.TransactionAuthenticator.NotAuthenticated
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * http server for shopping cart app
  *
  * @param catalog     product catalog actor
  * @param cartManager shopping cart manager actor
  * @param system      actor system
  */
class AppHttpServer(
  catalog: ActorRef,
  cartManager: ActorRef,
  trafficGenerator: ActorRef
)(implicit system: ActorSystem) {

  import Catalog._
  import ShoppingCart._
  import TrafficGenerator._

  val logger: Logger = LoggerFactory.getLogger(classOf[AppHttpServer])

  val Host: String = system.settings.config.getString("es.demos.http.server.host")
  val Port: Int = system.settings.config.getInt("es.demos.http.server.port")
  val RequestTimeout: FiniteDuration = system.settings.config.getInt("es.demos.http.server.request-timeout") millis

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(RequestTimeout)

  val route: Route =
    // matches /catalog
    path("catalog") {
      import Catalog.CatalogJsonSupport._
      onComplete(catalog ? Catalog.GetCatalog) {
        case Success(ProductCatalog(cat)) => complete(cat)
        case Success(other) => failWith(new Exception(s"unexpected response from Catalog: $other"))
        case Failure(ex) => failWith(ex)
      }
    } ~
    // matches /cart/<userId>
    path("cart" / Segment) { userId =>
      import ShoppingCart.ShoppingCartJsonSupport._
      get {
        onComplete(cartManager ? ShoppingCart.GetCartContent(userId)) {
          case Success(cartContent: ShoppingCartContent) => complete(cartContent)
          case Success(other) => failWith(new Exception(s"unexpected response from ShoppingCartManager: $other"))
          case Failure(ex) => failWith(ex)
        }
      }
    } ~
    // matches /cart/<userId>/add
    path("cart" / Segment / "add") { userId =>
      import ShoppingCart.ShoppingCartJsonSupport._
      post {
        entity(as[List[String]]) { entity =>
          cartManager ! ShoppingCart.AddToCart(userId, entity)
          complete(StatusCodes.Accepted)
        }
      }
    } ~
    // matches /cart/<userId>/commit
    path("cart" / Segment / "commit") { userId =>
      import ShoppingCart.ShoppingCartJsonSupport._
      post {
        entity(as[CartId]) { cartId =>
          onComplete(cartManager ? ShoppingCart.CommitCart(userId, cartId.timestamp)) {
            case Success(CartCommitted) => complete(StatusCodes.OK, "cart committed")
            case Success(CartOutOfDate) => complete(StatusCodes.Conflict, "cart out of date")
            case Success(CartEmpty) => complete(StatusCodes.Conflict, "cart is empty")
            case Success(other) => failWith(new Exception(s"unexpected response from ShoppingCartManager: $other"))
            case Failure(ex: NotAuthenticated) => complete(StatusCodes.Unauthorized, ex.toString)
            case Failure(ex) => failWith(ex)
          }
        }
      }
    } ~
    // matches /cart/<userId>/commit
    path("cart" / Segment / "clear") { userId =>
      post {
        onComplete(cartManager ? ShoppingCart.ClearCart(userId)) {
          case Success(CartCleared) => complete(StatusCodes.OK, "cart cleared")
          case Success(other) => failWith(new Exception(s"unexpected response from ShoppingCartManager: $other"))
          case Failure(ex) => failWith(ex)
        }
      }
    } ~
    path("client-sessions" / "scale") {
      post {
        parameters('numSessions.as[Int]) { (numSessions) =>
          trafficGenerator ! Scale(numSessions)
          complete(StatusCodes.OK, "ok")
        }
      }
    }

  Http().bindAndHandle(route, Host, Port)
}
