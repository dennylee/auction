package com.d2p.auction

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Signal}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.d2p.auction.models.Bid

/**
 * Lifecycle of an auction:
 *
 * [Empty phase] -- start auction --> [Open phase] -- close auction --> [Close phase]
 */

object AuctionLot {
  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("AuctionLot")

  case class AuctionState(auctionLotId: String, auctionItemId: String, bids: List[Bid], currentHighestBid: Option[Bid], startBidAmount: Double, endTime: Long) {
    def isHighestBid(bid: Bid): Boolean = {
      currentHighestBid match {
        case Some(highestBid) => bid.amount > highestBid.amount
        case None => true
      }
    }
  }

  // commands
  sealed trait Command
  case class StartAuction(auctionItemId: String, startBidAmount: Double, endTime: Long, replyTo: ActorRef[StatusReply[Done]]) extends Command
  case class PlaceBid(bid: Bid, replyTo: ActorRef[StatusReply[Done]]) extends Command
  case class CloseAuction(replyTo: ActorRef[StatusReply[Done]]) extends Command

  // events
  sealed trait Event
  private case class AuctionStarted(auctionLotId: String, auctionItemId: String, startBidAmount: Double, startTime: Long) extends Event
  private case class AuctionStartedLate(auctionLotId: String, auctionItemId: String, startBidAmount: Double, startTime: Long) extends Event
  private case class BidPlaced(bid: Bid, isHighestBidder: Boolean) extends Event
  private case class AuctionClosed() extends Event

  // replies
  sealed trait Reply

  // phases
  sealed trait Phase {
    def onCommand(cmd: Command): ReplyEffect[Event, Phase]
    def onEvent(evt: Event): Phase
  }

  private case class Empty(auctionLotId: String) extends Phase {
    override def onCommand(cmd: Command): ReplyEffect[Event, Phase] =
      cmd match {
        case StartAuction(auctionItemId, startBidAmount, endTime, replyTo) =>
          val now = System.currentTimeMillis()
          if (now >= endTime) {
            // we are passed the end time for this auction, we should go to Closed phase
            Effect.persist(AuctionStartedLate(auctionLotId, auctionItemId, startBidAmount, now))
              .thenReply(replyTo)(_ => StatusReply.error(new IllegalStateException(s"Passed the auction $endTime, will close the auction")))
          } else {
            Effect.persist(AuctionStarted(auctionLotId, auctionItemId, startBidAmount, endTime))
              .thenReply(replyTo)(_ => StatusReply.Ack)
          }
        case _ => Effect.unhandled.thenNoReply()
      }

    override def onEvent(evt: Event): Phase =
      evt match {
        case AuctionStarted(auctionLotId, auctionItemId, startBidAmount, endTime) =>
          Open(AuctionState(auctionLotId, auctionItemId, List[Bid](), None, startBidAmount, endTime))
        case AuctionStartedLate(auctionLotId, auctionItemId, startBidAmount, endTime) =>
          Closed(AuctionState(auctionLotId, auctionItemId, List[Bid](), None, startBidAmount, endTime))
        case _ => throw new IllegalStateException(s"Unable to handle $evt from Empty state")
      }
  }

  private case class Open(auctionState: AuctionState) extends Phase {
    override def onCommand(cmd: Command): ReplyEffect[Event, Phase] =
      cmd match {
        case PlaceBid(bid, replyTo) if bid.timestamp > auctionState.endTime =>
          // passed auction end time, putting auction to close and reject bid
          Effect.persist(AuctionClosed()).thenStop().thenReply(replyTo)(_ => StatusReply.error(new IllegalStateException(s"Auction has closed")))
        case PlaceBid(bid, replyTo) =>
          val isHighestBid = auctionState.isHighestBid(bid)
          val reply = if (isHighestBid) StatusReply.Ack else StatusReply.error(new IllegalArgumentException(s"Bid amount ${bid.amount} is below the current highest bidder ${auctionState.currentHighestBid}"))
          Effect.persist(BidPlaced(bid, isHighestBid)).thenReply(replyTo)(_ => reply)
        case CloseAuction(replyTo) =>
          Effect.persist(AuctionClosed()).thenStop().thenReply(replyTo)(_ => StatusReply.Ack)
      }

    override def onEvent(evt: Event): Phase =
      evt match {
        case BidPlaced(bid, isHighestBid) =>
          val currentHighestBid = if (isHighestBid) Some(bid) else auctionState.currentHighestBid
          val newAuctionState = auctionState.copy(bids = auctionState.bids :+ bid, currentHighestBid = currentHighestBid)
          Open(newAuctionState)
        case AuctionClosed() => Closed(auctionState)
      }
  }

  private case class Closed(auctionState: AuctionState) extends Phase {
    override def onCommand(cmd: Command): ReplyEffect[Event, Phase] =
      cmd match {
        case CloseAuction(replyTo) =>
          Effect.none.thenStop().thenReply(replyTo)(_ => StatusReply.Ack)
        case _ =>
          Effect.unhandled.thenStop().thenNoReply()
      }

    override def onEvent(evt: Event): Phase =
      evt match {
        case _ =>
          Closed(auctionState)
      }
  }

  private def signalHandler(implicit ctx: ActorContext[_]): PartialFunction[(Phase, Signal), Unit] = {
    case (state, signal) =>
      System.err.println(s"Received signal '${signal}' in auction lot actor '${ctx.self.path.name}' with state '$state'")
      ctx.log.info(s"Received signal '${signal}' in auction lot actor '${ctx.self.path.name}' with state '$state'")
  }

  def apply(id: String): Behavior[Command] = {
    Behaviors.setup { implicit ctx =>
      EventSourcedBehavior.withEnforcedReplies[Command, Event, Phase](
        PersistenceId.ofUniqueId(id),
        Empty(id),
        (phase, cmd) => phase.onCommand(cmd),
        (phase, evt) => phase.onEvent(evt)
      ).receiveSignal(signalHandler(ctx))
    }
  }

  def initClusterSharding(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] = {
    // todo: try with creating customer message extractor instead
    val entity = Entity(TypeKey)(entityContext => {
      AuctionLot(entityContext.entityId)
    })
      .withRole("auction-lot-role")

    ClusterSharding(system).init(entity)
  }
}
