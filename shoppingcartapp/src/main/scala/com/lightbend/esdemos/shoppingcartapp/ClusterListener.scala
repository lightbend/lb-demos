package com.lightbend.esdemos.shoppingcartapp

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

object ClusterListener {
  def props(): Props = Props(new ClusterListener())

  case class ClusterMembers(members: Set[String])

  case object GetMembers

}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)

  private var members: Set[String] = Set()

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
    )
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case GetMembers =>
      sender ! ClusterMembers(members)
    case MemberUp(member) =>
      log.warning(s"member is Up: {}", member.address)
      members += member.uniqueAddress.address.host.get
    case UnreachableMember(member) =>
      log.warning(s"member unreachable: {}", member)
      members -= member.uniqueAddress.address.host.get
    case MemberRemoved(member, previousStatus) =>
      log.warning(s"member is removed: {} after {}", member.address, previousStatus)
      members -= member.uniqueAddress.address.host.get
    case _: MemberEvent =>
    // ignore
  }

}
