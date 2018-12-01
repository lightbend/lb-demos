name := "shoppingcartapp"

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

lazy val compileOptions = Seq(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

lazy val commonDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVer,
  "com.typesafe.akka" %% "akka-cluster" % akkaVer,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVer,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVer,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVer,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVer,
  "com.typesafe.akka" %% "akka-remote" % akkaVer,
  "com.typesafe.akka" %% "akka-stream" % akkaVer,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVer,
  "com.lightbend.akka" %% "akka-split-brain-resolver" % akkaSBRVer,
  Cinnamon.library.cinnamonAkka,
  Cinnamon.library.cinnamonAkkaHttp,
  Cinnamon.library.cinnamonPrometheusHttpServer,
  Cinnamon.library.cinnamonJvmMetricsProducer,
  "io.spray" %% "spray-json" % sprayJsonVer,
  "ch.qos.logback" % "logback-classic" % logbackVer,
  "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParserVer,
  "org.scalatest" %% "scalatest" % scalaTestVer % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVer % Test
)

lazy val buildSettings = Seq(
  organization in ThisBuild := "com.lightbend",
  version in ThisBuild := "0.1",

  scalaVersion in ThisBuild := scalaVer,
  scalacOptions in ThisBuild ++= compileOptions,

  javacOptions in ThisBuild += "-Xlint:unchecked",

  parallelExecution in ThisBuild := false,
  parallelExecution in ThisBuild in Test := false,
  logBuffered in ThisBuild in Test := false
)

lazy val sharedSettings = Seq(
  cinnamon in run := true,
  annotations := Map(
    // enable scraping
    "prometheus.io/scrape" -> "true",
    // set scheme - defaults to "http"
    "prometheus.io/scheme" -> "http",
    // set path - defaults to "/metrics"
    "prometheus.io/path" -> "/metrics",
    // set port - defaults to "9001"
    "prometheus.io/port" -> "9001"
  ),

  // declare container ports that should be exposed
  endpoints ++= List(
    TcpEndpoint("app", 8080, PortIngress(8080)),
    TcpEndpoint("cinnamon", 9001, None)
  ),

  // cluster bootstrap deployment settings
  enableAkkaClusterBootstrap := true,
  deployMinikubeAkkaClusterBootstrapContactPoints := 2,

  // k8s deployment conf
  prependRpConf := "minikube-application.conf"
)

lazy val commonSettings = Seq(
  unmanagedSourceDirectories in Compile := List((scalaSource in Compile).value),
  unmanagedSourceDirectories in Test := List((scalaSource in Test).value),
  libraryDependencies ++= commonDependencies,
)

lazy val shoppingcartapp = (project in file("."))
  .settings(commonSettings: _*)
	.settings(sharedSettings: _*)
	.enablePlugins(Cinnamon, SbtReactiveAppPlugin)
	.settings(buildSettings: _*)

val akkaVer = "2.5.18"
val akkaHttpVer = "10.1.5"
val akkaSBRVer = "1.1.5"
val logbackVer = "1.2.3"
val scalaVer = "2.12.7"
val scalaParserVer = "1.1.1"
val scalaTestVer = "3.0.5"
val sprayJsonVer = "1.3.5"

