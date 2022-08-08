package com.d2p.health

import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.Route

object HealthCheckRoutes {

  val routes: Route =
    healthCheck()

  private def healthCheck(): Route = (get & path("health-check")) {
    complete("OK")
  }
}
