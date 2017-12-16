package services.Messages

import services.Users.{Client, ClientOwner}

@SerialVersionUID(276448L)
sealed trait Message extends Product with Serializable {
  def from: ClientOwner

}
object Message {

  final case class GetNumberMsg(from: ClientOwner) extends Message

  final case class NextPlease(from: ClientOwner) extends Message

}