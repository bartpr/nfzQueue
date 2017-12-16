package services.Queues

import akka.actor.{ActorRef, ActorSystem}

class PublicQueue(implicit system: ActorSystem,
                  connection: ActorRef,
                  exchange: String ) extends BussinessQueue() {



  override def putOnTheEnd(): Unit = ???

  override def getFirst(): Unit = ???

  override def getAll(): Unit = ???

  override def putInitialPatients(): Unit = ???
}