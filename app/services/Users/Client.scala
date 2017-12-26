package services.Users

import akka.actor.{ActorRef, ActorSelection, ActorSystem}
import com.newmotion.akka.rabbitmq.{Channel, ChannelActor, ChannelMessage, CreateChannel}
import services.Messages.Message
import services.{IdStore, MqRabbitEndpoint}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class Client(val clientOwner: ClientOwner)
                     (implicit system: ActorSystem,
                      connection: ActorRef,
                      exchange: String ) extends MqRabbitEndpoint{

  val id: Long = Client.getNewNumber

  override val name: String = typeName + "-" + id.toString

  locally {
    connection ! CreateChannel(ChannelActor.props(setupPublisher), Some(name))
  }

  def publish_msg(msg: Message, queueName: String): Unit = {
    val publisher: ActorSelection = system.actorSelection("/user/rabbitmq/" + name)

    def publish(channel: Channel) = {
      channel.basicPublish(exchange, queueName, null, toBytes(msg))
    }
    publisher ! ChannelMessage(publish, dropIfNoChannel = false)
  }

  def setupPublisher(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, "")
  }





  private def typeName: String =
    clientOwner match {
      case _: Doctor => "doctor"
      case _: Patient => "patient"
      case _: Database => "database"
      case _ => ???
    }

  private def toBytes(x: Long): Array[Byte] = x.toString.getBytes("UTF-8")
}

object Client {
  private val idStore: IdStore = new IdStore()
  def getNewNumber: Long = Await.result(idStore.getNew, Duration.Inf)
}