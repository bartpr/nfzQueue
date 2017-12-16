package services.Users

import akka.actor.{ActorRef, ActorSystem}

class Doctor(val userId: Long, specializationIdSeq: Seq[String])
  extends User {

  import services.Users.Perm._
  override def permSeq: Seq[Perm] = Seq(creatingUsers, creatingQueue(specializationIdSeq))

}