package services.Users

import akka.actor.{ActorRef, ActorSystem}

class Database extends ClientOwner{
  override def id: Long = -1
}