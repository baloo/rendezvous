package net.superbaloo.rendezvous
package messages

import akka.actor.ActorRef


case class Identify(side: Side, sender: ActorRef)

