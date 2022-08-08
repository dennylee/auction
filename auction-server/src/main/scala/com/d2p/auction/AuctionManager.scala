package com.d2p.auction

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import com.d2p.auction.models.Auction

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
 * ClusterSingleton may not be the recommended choice as it presents some pitfalls:
 * https://doc.akka.io/docs/akka/current/typed/cluster-singleton.html#introduction
 *
 * But it'll be sufficient for now, until we notice issues around this.
 */
object AuctionManager {
  private var auctionMap: Map[String, Auction] = _

  // commands
  sealed trait Command
  case class InitializeAuctionManager(auctions: List[Auction]) extends Command
  case class CreateAuction(auction: Auction) extends Command
  private case class StartAuction(auction: Auction) extends Command
  private case class EndAuction(auction: Auction) extends Command

  def apply(stashBufferSize: Int): Behavior[Command] = {
    Behaviors.setup { implicit ctx =>
      Behaviors.withTimers { implicit timer =>
        Behaviors.withStash(stashBufferSize) { implicit stashBuffer =>
          // initialize the actors from database auction records
          ctx.log.info("Auction Manager is initialized.")
          initAuctionManager()
          initializing()
        }
      }
    }
  }

  private def initializing()(implicit ctx: ActorContext[Command], timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] =
    Behaviors.receiveMessage {
      case InitializeAuctionManager(auctions) => onInitializeAuctionManager(auctions)
      case cmd =>
        stashBuffer.stash(cmd)
        Behaviors.same
    }

  private def running()(implicit ctx: ActorContext[Command], timer: TimerScheduler[Command]): Behavior[Command] =
    Behaviors.receiveMessage {
      case createAuction: CreateAuction => onCreateAuction(createAuction)
      case startAuction: StartAuction => onStartAuction(startAuction)
      case endAuction: EndAuction => onEndAuction(endAuction)
    }

  def initClusterSingleton(system: ActorSystem[_], stashBufferSize: Int): ActorRef[Command] = {
    val supervisorBehavior = Behaviors.supervise(AuctionManager(stashBufferSize)).onFailure[Exception](SupervisorStrategy.restart)
    ClusterSingleton(system).init(SingletonActor(supervisorBehavior, "auction-manager"))
  }

  private def initAuctionManager()(implicit ctx: ActorContext[Command]): Unit = {
    ctx.pipeToSelf(Future.successful {
      val now = System.currentTimeMillis()
      List(
        Auction("abc", "123", now + 5000L, now + 25000L, 1.0d),
        Auction("xyz", "456", now + 3000L, now + 60000L, 1.0d),
        Auction("fgh", "789", now - 1000L, now - 25000L, 1.0d)
      )
    }) {
      case Success(auctions) => InitializeAuctionManager(auctions)
      case Failure(e) => throw e
    }
  }

  private def onInitializeAuctionManager(auctions: List[Auction])(implicit ctx: ActorContext[Command], timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    auctionMap = auctions
      .filter(auction => auction.endTime - System.currentTimeMillis() > 0L)
      .map(auction => {
        createTimers(auction) // side effect to create timers
        (auction.auctionLotId -> auction)
      })
      .toMap

    stashBuffer.unstashAll(running())
  }

  private def onCreateAuction(createAuction: CreateAuction)(implicit timer: TimerScheduler[Command]): Behavior[Command] = {
    createTimers(createAuction.auction)
    auctionMap += (createAuction.auction.auctionLotId -> createAuction.auction)
    Behaviors.same
  }

  private def onStartAuction(startAuction: StartAuction)(implicit ctx: ActorContext[Command]): Behavior[Command] = {
    val entityRef = ClusterSharding(ctx.system).entityRefFor(AuctionLot.TypeKey, startAuction.auction.auctionLotId)
    entityRef ! AuctionLot.StartAuction(startAuction.auction.auctionItemId, startAuction.auction.startBidAmount, startAuction.auction.endTime, ctx.system.ignoreRef)
    Behaviors.same
  }

  private def onEndAuction(endAuction: EndAuction)(implicit ctx: ActorContext[Command]): Behavior[Command] = {
    val entityRef = ClusterSharding(ctx.system).entityRefFor(AuctionLot.TypeKey, endAuction.auction.auctionLotId)
    entityRef ! AuctionLot.CloseAuction(ctx.system.ignoreRef)
    auctionMap -= endAuction.auction.auctionLotId
    // todo: remove from db as well
    Behaviors.same
  }

  private def createTimers(auction: Auction)(implicit timer: TimerScheduler[Command]): Unit = {
    val endTime = auction.endTime - System.currentTimeMillis()
    val startTime = auction.startTime - System.currentTimeMillis()
    timer.startSingleTimer(s"end-auction-${auction.auctionLotId}", EndAuction(auction), FiniteDuration(endTime, TimeUnit.MILLISECONDS))

    if (startTime > 0L) {
      timer.startSingleTimer(s"start-auction-${auction.auctionLotId}", StartAuction(auction), FiniteDuration(startTime, TimeUnit.MILLISECONDS))
    }
  }
}
