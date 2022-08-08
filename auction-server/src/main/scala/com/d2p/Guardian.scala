package com.d2p

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.server.Route
import com.d2p.auction.routes.akktHttp.AuctionLotRoutes
import com.d2p.health.HealthCheckRoutes

/**
 * Root actor bootstrapping the application
 */
object Guardian {
  def apply(name: String, interface: String, httpPort: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    AuctionServer.start(name, interface, httpPort, routes(context.system), context.system)
    Behaviors.empty
  }

  def routes(implicit system: ActorSystem[_]): Route = {
    import akka.http.scaladsl.server.Directives._

    HealthCheckRoutes.routes ~
      new AuctionLotRoutes().routes
  }
}
