lazy val buildSettings = Seq(
  organization := "com.d2p",
  scalaVersion := "2.13.8",
  name := "auction",
  version := "0.1"
)

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-unused:imports",
  "-encoding", "UTF-8"
)

lazy val commonJavacOptions = Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

lazy val commonSettings = Seq(
  Compile / scalacOptions ++= commonScalacOptions,
  Compile / javacOptions ++= commonJavacOptions,
  run / javaOptions ++= Seq("-Xms128m", "-Xmx1024m"),
  run / fork := false,
  Global / cancelable := false,
//  licenses := Seq(
//    ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
//  )
)

lazy val `auction-server` = project
  .in(file("auction-server"))
  .settings(commonSettings)
  .settings(
    mainClass in (Compile, run) := Some("com.d2p.Main"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % "2.6.19",
      "com.typesafe.akka" %% "akka-serialization-jackson" % "2.6.19",
      "com.typesafe.akka" %% "akka-distributed-data" % "2.6.19",
      "com.typesafe.akka" %% "akka-persistence-typed" % "2.6.19",
      "com.typesafe.akka" %% "akka-slf4j" % "2.6.19",
      "com.typesafe.akka" %% "akka-http" % "10.1.11",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.9",
      "com.lightbend.akka.management" %% "akka-management" % "1.1.3",
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % "1.1.3",
      "ch.qos.logback" % "logback-classic" % "1.2.9")
  )


//addCommandAlias("start-auction-server-1", "runMain com.d2p.Main 2551")


//val AkkaVersion = "2.6.19"
//val AkkaHttpVersion = "10.1.11"
//val LogbackVersion = "1.2.9"
//
//
//
//
//lazy val `killrweather-fog` = project
//  .in(file("killrweather-fog"))
//  .settings(commonSettings)
//  .settings(
//    mainClass in (Compile, run) := Some("sample.killrweather.fog.Fog"),
//    libraryDependencies ++= Seq(
//      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
//      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
//      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
//      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
//      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
//      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
//      "ch.qos.logback" % "logback-classic" % LogbackVersion
//    )
//  )
//
//// Startup aliases for the first two seed nodes and a third, more can be started.
//addCommandAlias("sharding1", "runMain sample.killrweather.KillrWeather 2551")
//addCommandAlias("sharding2", "runMain sample.killrweather.KillrWeather 2552")
//addCommandAlias("sharding3", "runMain sample.killrweather.KillrWeather 0")

