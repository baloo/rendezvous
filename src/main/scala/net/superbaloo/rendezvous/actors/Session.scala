package net.superbaloo.rendezvous
package actors

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Terminated
import akka.actor.PoisonPill
import akka.actor.Props
import akka.util.ByteString

import messages.UUIDLookup
import messages.Payload
import messages.Identify
import messages.SessionFrom
import messages.Side

import java.util.UUID

import scala.collection.mutable


/*
 * This actor will handle connection between both sides
 * This will forward content from one side to the other
 */
class Session(uuid: UUID) extends Actor {

  // Keep track of Sides actor ref
  var sides = mutable.Map[Side, ActorRef]()

  // And buffer input for each sides
  var outputBuffers = mutable.Map[Side, ByteString]()

  def receive = {
    // Someone nasty try to communicate from the same side with the same uuid
    // Bad Bad people, please burn in hell, with love
    case Identify(side, sideActor) if sides.get(side).isDefined => sideActor ! PoisonPill

    // A side want to register, let register him ! :)
    case Identify(side, sideActor) =>
      println(s"${side.toString} identified with actor : $sideActor")
      sides += (side -> sideActor)
      outputBuffers.get(side) foreach { buffer =>
        println("Do have buffer !")
        sideActor ! SessionFrom(buffer)
        outputBuffers -= side
      }

    // We receive payload from one side but the other side is not ready yet :(
    // Buffer !
    case Payload(side, data) if ! sides.get(side.other).isDefined =>
      println(s"content from ${side.toString} but other side not known")
      val buffer = outputBuffers.get(side.other) getOrElse {
        ByteString.empty
      }

      outputBuffers += (side.other -> (buffer ++ data))

    // We receive payload from one side we should replicate on the other !
    case Payload(side, data) =>
      println(s"content from ${side.toString} and forwarding")
      val other = sides.get(side.other)
      other.foreach { a =>
        println(s"forward to $other")
        a ! SessionFrom(data)
      }
  }
}


/**
 * Session manager will keep track of each sessions and create them if needed
 */
class SessionManager extends Actor {
  val sessions = mutable.Map[UUID, ActorRef]()

  //val system = this.context.system

  def receive = {
    // a side asks us the address of "uuid"
    // we will respond to him
    case UUIDLookup(side, uuid) =>
      val ar = sessions.get(uuid) getOrElse {
        // UUID not found we will create a new actor
        val ar = context.actorOf(Props(new Session(uuid)), name=uuid.toString)

        // And register it in our map
        sessions += (uuid -> ar)

        // Register a watcher of child
        context.watch(ar)

        ar
      }

      // Hey sender ! this is the actor you asked the address for ;)
      sender ! ar

    // Oops :( for some reason the session disconnected, it does not exists anymore !
    case Terminated(actor) => 
      sessions.filter{
        case (_, v) if v == actor => true
        case _ => false
      }
  }
}

