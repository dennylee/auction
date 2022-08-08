package com.d2p

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory

object Main {
  def main(args: Array[String]): Unit = {
    val defaultConfig = ConfigFactory.load();
    val serverName = defaultConfig.getString("auction-server.name")
    val httpPort = defaultConfig.getInt("auction-server.port")

    ActorSystem[Nothing](Guardian(serverName, "localhost", httpPort), "auction-actor-system", ConfigFactory.load())
  }
}
