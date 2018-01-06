package services.Users

import java.util.concurrent.{ArrayBlockingQueue, ConcurrentLinkedQueue}

import akka.actor.{ActorRef, ActorSelection, ActorSystem}
import com.newmotion.akka.rabbitmq.{BasicProperties, Channel, ChannelActor, ChannelMessage, CreateChannel, DefaultConsumer, Envelope}
import com.rabbitmq.client.AMQP.BasicProperties
import services.Messages.Message
import services.Messages.Message.{Request, Response}
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

  val responseQueue = new ConcurrentLinkedQueue[Response]

  def responseWaiter(req: Request, queueName: String): Future[Response] = {
    publish_msg(req, queueName)
    Future {
      def loop(): Response = {
        if(!responseQueue.isEmpty)
          responseQueue.poll()
        else
          loop()
      }
      loop()
    }
  }

  def publish_msg(msg: Message, queueName: String) = {
    val publisher: ActorSelection = system.actorSelection("/user/rabbitmq/" + name)


    def publish(channel: Channel) = {
      channel.basicPublish(exchange, queueName, new BasicProperties.Builder().replyTo(name).build(), toBytes(msg))
    }

    publisher ! ChannelMessage(publish, dropIfNoChannel = false)
  }

  override def setupChannel(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare(name, false, false, false, null).getQueue
    channel.queueBind(queue, exchange, name)
    val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
        val clientStr = clientOwner match {
          case doc: Doctor => s"doctor-${doc.userId}"
          case patient: Patient => s"patient-${patient.userId}"
          case database: Database => s"database"
        }
        val response = fromBytes(body).asInstanceOf[Response]
        response match {
          case Message.NextPatientIs(_, patientId) =>
            println(s"[$name, $clientStr] Next Patient is ${patientId.getOrElse("freeTime")}")
          case Message.YourNumberIs(_, number) =>
            println(s"[$name, $clientStr] Your number is $number")
        }
        responseQueue.add(response)
      }
    }
    channel.basicConsume(name, true, consumer)
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