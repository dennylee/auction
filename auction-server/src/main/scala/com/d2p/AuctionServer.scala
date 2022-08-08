package com.d2p

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.management.scaladsl.AkkaManagement
import akka.{Done, actor}
import com.d2p.auction.{AuctionLot, AuctionManager}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object AuctionServer {

  def start(name: String, interface: String, port: Int, routes: Route, system: ActorSystem[_]): Unit = {
    import akka.actor.typed.scaladsl.adapter._

    val shutdown = CoordinatedShutdown(system.toClassic)

    registerHttpServer(system, name, interface, port, routes, shutdown)
    registerAkkaManagement(system, shutdown)
    registerActors(system)
  }

  private def registerHttpServer(system: ActorSystem[_], name: String, interface: String, port: Int, routes: Route, coordinatedShutdown: CoordinatedShutdown): Unit = {
    import akka.actor.typed.scaladsl.adapter._
    import scala.concurrent.duration._

    implicit val ec: ExecutionContextExecutor = system.executionContext
    implicit val classicSystem: actor.ActorSystem = system.toClassic

    val httpServer = Http().newServerAt(interface, port).bindFlow(routes)

    httpServer.onComplete {
      case Success(binding) => {
        val address = binding.localAddress
        system.log.info(s"'$name' on http://${address.getHostString}:${address.getPort}/ is online.")

        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "http-graceful-terminate") { () =>
          binding.terminate(10.seconds).map { _ =>
            system.log.info(s"'$name' on http://${address.getHostString}:${address.getPort}/ gracefully shutdown and offline.")
            Done
          }
        }
      }
      case Failure(ex) => {
        system.log.error(s"Failed to bind HTTP endpoint, terminating '$name'.'", ex)
        system.terminate()
      }
    }
  }

  private def registerAkkaManagement(system: ActorSystem[_], coordinatedShutdown: CoordinatedShutdown): Unit = {
    implicit val ec: ExecutionContextExecutor = system.executionContext

    val akkaManagement = AkkaManagement(system)
    akkaManagement.start().onComplete {
      case Success(uri) => system.log.info(s"Akka Management on ${uri.toString()} is online.")
      case Failure(e) => system.log.error("Failed to start Akka Management.", e)
    }

    coordinatedShutdown.addTask(CoordinatedShutdown.PhaseBeforeClusterShutdown, "akka-management-shutdown") { () =>
      val future = akkaManagement.stop()
      future.onComplete {
        case Success(_) => system.log.info("Akka management stopped.")
        case Failure(_) => system.log.error("Failed to stop Akka Management.")
      }

      future
    }
  }

  private def registerActors(system: ActorSystem[_]): Unit = {
    AuctionManager.initClusterSingleton(system, 10000)
    AuctionLot.initClusterSharding(system)
  }
}
