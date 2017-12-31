package services.Queues

import akka.actor.{ActorRef, ActorSystem}
@SerialVersionUID(112448276L)
sealed trait ClinicQueue extends Product with Serializable {
  def id: Long
}

case class PublicQueue(id: Long) extends ClinicQueue