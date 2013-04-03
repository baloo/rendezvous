package net.superbaloo.rendezvous
package actors

import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.PoisonPill
import akka.actor.Terminated
import akka.pattern.ask
import akka.util.ByteString
import akka.util.Timeout
import akka.io.IO
import akka.io.Tcp
import akka.io.Tcp.{Bind, Bound, Connected, Received, Register, ConnectionClosed, Write, NoAck}


import java.net.InetSocketAddress

import messages.UUIDLookup
import messages.Identify
import messages.Payload
import messages.SessionFrom
import messages.Side

import utils.UUIDUtils

object CheckIdentified

/**
 * This actor will be used for each connection.
 * At first, we will aggregate 32 bytes, until we can have a valid uuid.
 * Once we have 32 characters, we will validate the uuid, and lookup for the session actor handling this uuid.
 */
class SideConn(side: Side, proxy: ActorRef, localAddress: InetSocketAddress, remoteAddress: InetSocketAddress) extends Actor {
  var inputBuffer = ByteString.empty
  var uuidRead = false
  var sessionActor: Option[ActorRef] = None
  //var connectionActor: Option[ActorRef] = None

  val system = this.context.system

  override def preStart = {
    // Hacky, easy execution context !
    implicit val ec = this.context.dispatcher

    // Let 15 seconds to user to identify itself, after what we will kill him
    system.scheduler.scheduleOnce(15.seconds, self, CheckIdentified)
  }


  def receive = {
    case CheckIdentified =>
      // We have to check user has identified itself
      // otherwise, poison pill !
      sessionActor.getOrElse {
        self ! PoisonPill
      }

    case SessionFrom(data) =>
      println(s"$side received from session")
      println(s"sending to $proxy")
      proxy ! Write(data, NoAck)

    case Received(data) if ! sessionActor.isDefined =>
      // Okay actor is not initialized, we need 38 bytes to get a valid uuid
      inputBuffer = inputBuffer ++ data

      // We haven't read the uuid yet from input, 
      if ( (! uuidRead) && inputBuffer.length >= 38) {
        uuidRead = true
        val uuidStr = inputBuffer.take(36).utf8String

        // uuid + \r\n
        inputBuffer = inputBuffer.drop(36+2)

        val uuid = UUIDUtils.readUuid(uuidStr)

        println("uuid: " + uuid)

        uuid.map { uuid =>
          // We now have a valid uuid !
          // Lets lookup a session actor for it !
          val sessionManagerActor = system.actorFor("/user/session")
          val futSessionActor = sessionManagerActor.ask(UUIDLookup(side, uuid))(new Timeout(5, SECONDS))

          // Hacky, easy execution context !
          implicit val ec = this.context.dispatcher

          futSessionActor.foreach{ 
            case sa: ActorRef =>
              println("ACTOR REF: " + self)
              self ! SessionFrom(ByteString("rendezvous\r\n"))
              // Identify to session actor
              sa ! Identify(side, self)
              sessionActor = Some(sa)

              // Watch lifecycle of session actor
              context.watch(sa)
            case e =>
              println("Session manager returned unhandled message: " + e)
          }
          // Too bad :( time is running out
          futSessionActor.onFailure{
            case e: Throwable =>
              println("no session actor :( : " +e)
             self ! PoisonPill // Suicide !
          }
        } getOrElse {
          // Ho ! a bad uuid
          // Bad UUID is bad, we should suicide !
          self ! PoisonPill 
        }
      }
      // Else continue

    // We do have content in input buffer that was waiting 
    // to transmit
    case Received(data) if inputBuffer.length >= 0 =>
      val input = inputBuffer
      inputBuffer = ByteString.empty

      sessionActor.map { actor =>
        actor ! Payload(side, input)
        actor ! Payload(side, data)
      }

    // We do have content in input, we have to transmit
    case Received(data) =>
      sessionActor.map { actor =>
        actor ! Payload(side, data)
      }

    case Terminated(a) =>
      sessionActor match {
        case Some(sa) if sa == a => self ! PoisonPill
        case _ => 
      }
    case _: ConnectionClosed =>
      println("external actor disconnected")
      sessionActor.foreach( _ ! PoisonPill )
      self ! PoisonPill
  }

}

class SideListener(side: Side, localAddress: InetSocketAddress) extends Actor {
  override def preStart = {
    implicit val system = this.context.system

    IO(Tcp) ! Bind(self, localAddress)
  }

  def receive = {
    case Bound =>
    case Connected(remoteAddress, localAddress) =>
      val connectionActor = sender
      val listener = context.actorOf(Props(new SideConn(side, connectionActor, localAddress, remoteAddress)),
        name=remoteAddress.toString.replace("/",""))
      connectionActor ! Register(listener)
    case e => println("Message e: " + e + " is not handled by ExternalListener actor")
  }
}

class SideManager(side: Side, localAddresses: Seq[InetSocketAddress]) extends Actor {
  override def preStart = {
    implicit val system = this.context.system

    val actors = localAddresses.map { localAddress =>

      val name = localAddress.toString.replace("/", "")
      localAddress -> context.actorOf(Props(new SideListener(side, localAddress)), name=name)
    }.toMap

  }

  def receive = {
    case e => println("Message e: " + e + " is not handled by External actor")
  }

}



