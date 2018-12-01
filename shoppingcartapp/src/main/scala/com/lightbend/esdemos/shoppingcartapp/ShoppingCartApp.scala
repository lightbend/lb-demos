package com.lightbend.esdemos.shoppingcartapp

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Properties

object ShoppingCartApp {
  def main(args: Array[String]): Unit = {
    // config file for k8s deployment defined in build.sbt with the `prependRpConf` key.  When running
    // locally, that key will not take effect, so load dev config instead
    val config = Properties.envOrNone("RP_PLATFORM") match {
      case Some(_) => ConfigFactory.load()
      case None => ConfigFactory.load("dev-application.conf")
    }

    val system: ActorSystem = ActorSystem("ShoppingCartApp", config)

    val app: ShoppingCartApp = new ShoppingCartApp(system)
    app.run()
  }
}

class ShoppingCartApp(system: ActorSystem) {
  val clusterListener: ActorRef = createClusterListener()
  val catalog: ActorRef = createProductCatalog()
  val cartManager: ActorRef = createShoppingCartManager(catalog)
  val trafficGenerator: ActorRef = createTrafficGenerator(clusterListener)
  val appServer: AppHttpServer = createAppServer(catalog, cartManager)

  def run(): Unit = {
    Await.ready(system.whenTerminated, Duration.Inf)
  }

  private def createClusterListener(): ActorRef = system.actorOf(ClusterListener.props())

  /** create http server */
  private def createAppServer(catalog: ActorRef, cartManager: ActorRef): AppHttpServer = new AppHttpServer(catalog, cartManager, trafficGenerator)(system)

  /** create product catalog actor */
  private def createProductCatalog(): ActorRef = system.actorOf(Catalog.props())

  /** create shopping cart manager actor */
  private def createShoppingCartManager(catalog: ActorRef): ActorRef = system.actorOf(ShoppingCartManager.props(catalog))

  /** create traffic generator */
  private def createTrafficGenerator(clusterListener: ActorRef): ActorRef = {

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = TrafficGenerator.props(clusterListener),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system) // .withRole("client")
    ), "traffic-generator-actor")
    system.actorOf(
      ClusterSingletonProxy.props("/user/traffic-generator-actor", ClusterSingletonProxySettings(system)),
      "traffic-generator-actor-proxy"
    )
  }

}
