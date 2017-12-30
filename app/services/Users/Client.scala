package services.Users

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSelection, ActorSystem}
import akka.pattern.Patterns
import akka.util.Timeout
import com.newmotion.akka.rabbitmq.{BasicProperties, Channel, ChannelActor, ChannelMessage, CreateChannel, DefaultConsumer, Envelope}
import com.rabbitmq.client.AMQP.BasicProperties
import services.Messages.Message
import services.{IdStore, MqRabbitEndpoint}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}

class Client(val clientOwner: ClientOwner)
                     (implicit system: ActorSystem,
                      connection: ActorRef,
                      exchange: String,
                      ec: ExecutionContext) extends MqRabbitEndpoint{


  val id: Long = Client.getNewNumber

  override val name: String = typeName + "-" + id.toString

  def publish_msg(msg: Message, queueName: String): Unit = {
    val publisher: ActorSelection = system.actorSelection("/user/rabbitmq/" + name)


    def publish(channel: Channel) = {
      channel.basicPublish(exchange, queueName, new BasicProperties.Builder().replyTo(name).build(), toBytes(msg))
    }
    publisher ! ChannelMessage(publish, dropIfNoChannel = false)
  }

  override def setupChannel(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, "")
//    val consumer = new DefaultConsumer(channel) {
//      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
//        println(fromBytes(body) + "lelele")
//      }
//    }
//    channel.basicConsume(queue, true, consumer)
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