package services.Users

import akka.actor.{ActorRef, ActorSystem}

class Doctor(val userId: Long, val name: String, val surname: String, specializationIdSeq: Seq[String] = Seq.empty)
  extends User {

  import services.Users.Perm._
  override def permSeq: Seq[Perm] = Seq(creatingUsers, creatingQueue(specializationIdSeq))

}