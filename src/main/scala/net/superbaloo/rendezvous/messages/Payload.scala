package net.superbaloo.rendezvous
package messages

import akka.util.ByteString

case class Payload(side: Side, data: ByteString)

