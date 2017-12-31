package services.Messages

import java.util.Optional

import services.Queues.{ClinicQueue, RPCQueue}
import services.Users.{Client, ClientOwner}
@SerialVersionUID(276448L)
sealed trait Message extends Product with Serializable {
  def from: Any
}

object Message {

  @SerialVersionUID(644827L)
  sealed trait Request extends Message with Product with Serializable {
    override def from: ClientOwner
  }

  @SerialVersionUID(764482L)
  sealed trait Response extends Message with Product with Serializable {
    override def from: ClinicQueue
  }

  final case class GetNumberMsg(from: ClientOwner) extends Request

  final case class YourNumberIs(from: ClinicQueue, number: Long) extends Response

  final case class NextPlease(from: ClientOwner) extends Request

  final case class NextPatientIs(from: ClinicQueue, patientId: Option[Long]) extends Response

}