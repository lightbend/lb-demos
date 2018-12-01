package com.lightbend.esdemos.shoppingcartapp

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}

object TrafficGenerator {
  def props(clusterListener: ActorRef): Props = Props(new TrafficGenerator(clusterListener))

  case class Scale(numSessions: Int)
}

class TrafficGenerator(clusterListener: ActorRef) extends Actor with ActorLogging {
  import TrafficGenerator._

  var sessions: Map[String, ActorRef] = Map()

  override def preStart(): Unit = {
    val numSessions: Int = context.system.settings.config.getInt("userSim.concurrent-users")

    log.info(s"creating $numSessions client sessions")
    (1 to numSessions).foreach(_ => startSession())
  }

  def receive: Receive = {
    case Terminated(lastSession) =>
      context.unwatch(lastSession)
      startSession(Some(lastSession))
    case Scale(numSessions) =>
      scaleSessions(numSessions)
    case msg =>
      log.warning(s"unexpected message: {}", msg)
  }

  /** generates session name based on session count */
  def genSessionName(index: Int = sessions.size): String = s"shopping-session-actor-$index"

  /** create a new shopping session */
  def startSession(lastSession: Option[ActorRef] = None): Unit = {
    val sessionName = lastSession match {
      case Some(prevSession) =>
        context.unwatch(prevSession)
        prevSession.path.name
      case None =>
        genSessionName()
    }

    val session = context.actorOf(ShoppingSession.props(sessionName, clusterListener), sessionName)
    context.watch(session)
    sessions = sessions.updated(sessionName, session)
  }

  /** pops the last session created */
  def popSession(): Unit = {
    val lastSessionName = genSessionName(sessions.size - 1)
    sessions.get(lastSessionName) match {
      case Some(session) =>
        context.unwatch(session)
        session ! PoisonPill
        sessions = sessions - lastSessionName
      case None =>
        // no-op
    }
  }

  /** scales the number of sessions */
  def scaleSessions(numSessions: Int): Unit = {
    log.info(s"scaling client sessions from ${sessions.size} to $numSessions")
    if (numSessions > sessions.size) {
      (0 until (numSessions - sessions.size)).foreach(_ => startSession())
    } else if (numSessions < sessions.size) {
      (0 until (sessions.size - numSessions)).foreach(_ => popSession())
    }
  }
}
