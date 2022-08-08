package com.d2p.auction.routes.akktHttp

import com.d2p.auction.models.AuctionItem
import spray.json.DefaultJsonProtocol

case class StartAuction(auctionLotId: String, auctionItemId: String, startBidAmount: Double, endTime: Long)
case class PlaceBid(bidderId: String, bidAmount: Double)

trait AuctionJsonFormats extends DefaultJsonProtocol {

  implicit val auctionItemJsonFormat = jsonFormat4(AuctionItem)
  implicit val createAuctionLotJsonFormat = jsonFormat4(StartAuction)
  implicit val bidAuctionLotJsonFormat = jsonFormat2(PlaceBid)
}
