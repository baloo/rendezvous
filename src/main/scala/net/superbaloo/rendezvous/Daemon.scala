package net.superbaloo.rendezvous

import akka.actor.ActorSystem
import akka.actor.TypedActor
import akka.actor.TypedProps
import akka.actor.Props

//import actors.Internal
import actors.SideManager
import actors.SessionManager


import messages.Side


import java.net.InetSocketAddress


object Daemon extends App {
  val system = ActorSystem("rendezvous")

  val externalAddresses = Seq(new InetSocketAddress("127.0.0.1", 3333))
  val externalActor = system.actorOf(Props(new SideManager(Side.external, externalAddresses)), name="external")

  val internalAddresses = Seq(new InetSocketAddress("127.0.0.1", 3334))
  val internalActor = system.actorOf(Props(new SideManager(Side.internal, internalAddresses)), name="internal")

  val sessionActor = system.actorOf(Props(new SessionManager()), name="session")

  println(externalActor.path)
  println(sessionActor.path)

  println("Hello, world!")
}


