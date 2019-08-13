
# Lightbend Demos
This repository contains several applications used to demonstrate the utility of of the
[Lightbend Platform](https://www.lightbend.com/lightbend-platform) for distributed development visibility, and production class concerns, such as telemetry and monitoring, fault tolerance, etc.  

##  Lightbend Commercial License
All of these projects will require you to have a [Lightbend Platform Subscription](https://www.lightbend.com/lightbend-platform-subscription) and [configure Bintray credentials](https://developer.lightbend.com/docs/lightbend-platform/introduction/getting-started/subscription-and-credentials.html) on your workstation.

##  Demo Applications 
- [ShoppingCartApp](shoppingcartapp/README.md): A shopping cart web service that models client shopping sessions, where 
customers browse the product catalog, add products to their shopping carts, and ultimately commits the shopping 
transaction.  It is a [Scala](https://www.scala-lang.org/)/[sbt](https://www.scala-sbt.org/) project that uses
[akka-http](https://doc.akka.io/docs/akka-http/current/), [akka-cluster](https://doc.akka.io/docs/akka/current/cluster-usage.html), 
[cluster-sharding](https://doc.akka.io/docs/akka/current/cluster-sharding.html), 
[cluster-singleton](https://doc.akka.io/docs/akka/current/cluster-singleton.html), 
[Lightbend Telemetry](https://developer.lightbend.com/docs/cinnamon/current/getting-started/start.html), and the
[Lightbend Console](https://developer.lightbend.com/docs/console/current/index.html). 
- [DroneTracker](drone-tracker/readme.md): An IOT web application that processes data from simulated drones in a
distributed manner,enriching and aggregating the data for later query.  It is a Java/[maven](https://maven.apache.org/)
project that uses the [Play Framework](https://www.playframework.com/), 
[akka-streams](https://doc.akka.io/docs/akka/current/stream/), 
[akka-cluster](https://doc.akka.io/docs/akka/current/cluster-usage.html), 
[cluster-sharding](https://doc.akka.io/docs/akka/current/cluster-sharding.html), 
[cluster-singleton](https://doc.akka.io/docs/akka/current/cluster-singleton.html), 
[Lightbend Telemetry](https://developer.lightbend.com/docs/cinnamon/current/getting-started/start.html), and the
[Lightbend Console](https://developer.lightbend.com/docs/console/current/index.html). 
