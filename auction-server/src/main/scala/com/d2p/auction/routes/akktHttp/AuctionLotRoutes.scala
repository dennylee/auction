package com.d2p.auction.routes.akktHttp

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.d2p.auction.AuctionLot
import com.d2p.auction.models.Bid

import scala.concurrent.duration._
import scala.util.{Failure, Success}


class AuctionLotRoutes()(implicit system: ActorSystem[_]) extends AuctionJsonFormats with SprayJsonSupport {

  private val clusterSharding = ClusterSharding(system)

  private implicit val askTimeout: Timeout = Timeout(5.seconds)
  implicit val ec = system.executionContext

  val routes: Route =
    startAuction() ~
      placeBid() ~
      closeAuction()

  private def startAuction(): Route = path("auction") {
    post {
      entity(as[StartAuction]) { startAuction =>
        val auctionLotRef = clusterSharding.entityRefFor(AuctionLot.TypeKey, startAuction.auctionLotId)
        val status = auctionLotRef.askWithStatus(replyTo => AuctionLot.StartAuction(startAuction.auctionItemId, startAuction.startBidAmount, System.currentTimeMillis() + 25000L, replyTo))
        onComplete(status) {
          case Success(_) => complete(StatusCodes.OK)
          case Failure(e) => complete(StatusCodes.BadRequest, e.getMessage)
        }
      }
    }
  }

  private def placeBid(): Route = path("auction" / Segment) { auctionLotId =>
    post {
      entity(as[PlaceBid]) { placeBid =>
        val auctionLotRef = clusterSharding.entityRefFor(AuctionLot.TypeKey, auctionLotId)
        val status = auctionLotRef.askWithStatus(replyTo => AuctionLot.PlaceBid(Bid(placeBid.bidderId, placeBid.bidAmount, System.currentTimeMillis()), replyTo))
        onComplete(status) {
          case Success(_) => complete(StatusCodes.OK)
          case Failure(e) => complete(StatusCodes.BadRequest, e.getMessage)
        }
      }
    }
  }

  private def closeAuction(): Route = path("auction" / Segment) { auctionLotId =>
    post {
      val auctionLotRef = clusterSharding.entityRefFor(AuctionLot.TypeKey, auctionLotId)
      auctionLotRef.tell(AuctionLot.CloseAuction(system.ignoreRef))
      complete(StatusCodes.Accepted)
    }
  }
}


