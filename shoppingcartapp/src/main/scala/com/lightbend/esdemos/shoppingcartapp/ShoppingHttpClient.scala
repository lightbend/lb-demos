package com.lightbend.esdemos.shoppingcartapp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.lightbend.esdemos.shoppingcartapp.ShoppingCart._

import scala.concurrent.Future

class ShoppingHttpClient(host: String, port: Int)(implicit system: ActorSystem) {

  import system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /** retrieve product catalog */
  def queryProductCatalog(): Future[List[Catalog.Product]] = {
    import Catalog.CatalogJsonSupport._

    Http()
      .singleRequest(HttpRequest(uri = s"http://$host:$port/catalog"))
      .flatMap { response =>
        Unmarshal(response.entity).to[List[Catalog.Product]]
      }
  }

  /**
    * retrieve shopping cart
    *
    * @return contents of shopping cart
    */
  def queryShoppingCart(userId: String): Future[ShoppingCartContent] = {
    import ShoppingCart.ShoppingCartJsonSupport._
    Http()
      .singleRequest(HttpRequest(uri = s"http://$host:$port/cart/$userId"))
      .flatMap { response =>
        Unmarshal(response.entity).to[ShoppingCartContent]
      }
  }

  /**
    * adds products to cart
    *
    * @return http response
    */
  def addToCart(userId: String, productIds: List[String]): Future[HttpResponse] = {
    import ShoppingCart.ShoppingCartJsonSupport._
    for {
      request <- Marshal(productIds).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(
        HttpMethods.POST,
        s"http://$host:$port/cart/$userId/add",
        entity = request
      ))
    } yield response
  }

  /**
    * commit shopping cart
    *
    * @return http response
    */
  def commitCart(cartId: CartId): Future[HttpResponse] = {
    import ShoppingCart.ShoppingCartJsonSupport._
    for {
      request <- Marshal(cartId).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(
        HttpMethods.POST,
        s"http://$host:$port/cart/${cartId.userId}/commit",
        entity = request
      ))
    } yield response
  }

  /**
    * clears shopping cart
    */
  def clearCart(userId: String): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(uri = s"http://$host:$port/cart/$userId/clear", method = HttpMethods.POST))
  }
}
