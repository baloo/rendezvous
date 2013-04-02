package net.superbaloo.rendezvous
package messages


sealed trait Side {
  val other: Side
}

object Side {
  val external = External
  val internal = Internal
}

object External extends Side {
  val other = Internal
  override def toString = "external"
}
object Internal extends Side {
  val other = External
  override def toString = "internal"
}

