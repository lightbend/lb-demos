package com.lightbend.esdemos.shoppingcartapp

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object Catalog {
  /** props for product catalog */
  def props() = Props(new Catalog())

  /**
    * simple representation of product
    *
    * @param id          product id
    * @param name        product name
    * @param description product description
    * @param category    product category
    * @param unitPrice   price per unit
    */
  case class Product(id: String, name: String, description: String, category: String, unitPrice: Float)

  /** product catalog */
  case class ProductCatalog(products: List[Product])

  /**
    * validate product ids
    *
    * @param  productIds ids to validate
    * @return returns messages of type
    *         [ProductIdsValid] - if all product ids are valid
    *         [InvalidIds]      - if one or more ids invalid
    */
  case class ValidateProductIds(productIds: Set[String])

  /** returned if one ore more product ids invalid */
  case class InvalidIds(productIds: Set[String])

  /** json support for catalog */
  object CatalogJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val ProductFormats: RootJsonFormat[Product] = jsonFormat5(Product.apply)
    implicit val ProductCatalogFormats: RootJsonFormat[ProductCatalog] = jsonFormat1(ProductCatalog.apply)
  }

  /** message to retrieve product catalog */
  case object GetCatalog

  /** returned if all product ids are valid */
  case object ProductIdsValid

}

class Catalog extends Actor with ActorLogging {

  import Catalog._

  // hard code product catalog.  In a real implementation, this would be in a database
  val productCatalog = List(
    Product("1", "Snow Crash", "book by Neal Stephenson", "book", 13.13f),
    Product("2", "Blindsight", "book by Peter Watts", "book", 10.43f),
    Product("3", "Artemis", "book by Andy Weir", "book", 12.78f),
    Product("4", "Super Wings - Transforming Astra Toy Figure", "Spaceshipt Bot, 5\" scale", "toy", 19.99f),
    Product("5", "Super Wings - Transforming Agent Chase Toy Figure", "Spaceshipt Bot, 5\" scale", "toy", 19.99f),
    Product("6", "Octopath Traveller", "Choose your fate.  Eight stories await", "video game", 59.99f),
    Product("7", "WOMAGE Faux Leather Men Quartz Write Watch", "Oversized Red", "watch", 6.83f),
    Product("8", "Loweryeah Metal Strap Female Watch", "Rhine stone strap", "watch", 5.40f),
    Product("9", "Samsung 960 EVO Series - 500GB NVMe - M.2", "High speed solid state memory", "memory", 168.95f),
    Product("10", "Intel Core i7-8700K Desktop Processor", "6 Cores up to 5.7GHz", "cpu", 347.00f)
  )
  val productIds: Set[String] = productCatalog
    .map(_.id)
    .toSet

  override def receive: Receive = {
    case GetCatalog =>
      sender ! ProductCatalog(productCatalog)
    case ValidateProductIds(ids) =>
      val diff = ids -- productIds
      sender ! (
        if (diff.isEmpty) ProductIdsValid
        else InvalidIds(diff)
        )
  }
}
