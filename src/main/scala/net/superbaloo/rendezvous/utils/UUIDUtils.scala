package net.superbaloo.rendezvous
package utils

import java.util.UUID
import scala.util.control.Exception.catching

object UUIDUtils {
  def readUuid(data: String): Option[UUID] = catching(classOf[IllegalArgumentException]).opt(UUID.fromString(data))
}




