package services.Queues

import akka.actor.{ActorRef, ActorSystem}
import org.joda.time.DateTime
@SerialVersionUID(112448276L)
sealed trait ClinicQueue extends Product with Serializable {
  def id: Long
  def start: DateTime
  def end: DateTime
  def doctorsIds: Seq[Long]
}

case class PublicQueue(id: Long, start: DateTime, end: DateTime, doctorsIds: Seq[Long]) extends ClinicQueue