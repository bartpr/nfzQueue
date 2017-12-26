package services.Queues

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq.{BasicProperties, Channel, ChannelActor, CreateChannel, DefaultConsumer, Envelope}
import services.Messages.Message
import services.Users.ClientOwner
import services.{IdStore, MqRabbitEndpoint}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

abstract class BussinessQueue()(implicit system: ActorSystem,
                                connection: ActorRef,
                                exchange: String ) extends MqRabbitEndpoint{
  val id: Long = Await.result( BussinessQueue.idStore.getNew, Duration.Inf)
  override val name: String = s"queue-$id"

  private var queueMap: ConcurrentLinkedQueue[(Long, ClientOwner)] = new ConcurrentLinkedQueue[(Long, ClientOwner)]()
  private val numbers = new IdStore()

  private def setupSubscriber(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, name)
    val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
        fromBytes(body) match {
          case Message.GetNumberMsg(patient) => {
            queueMap.add(Await.result(numbers.getNew, Duration.Inf) -> patient)
            System.out.println("new patient get a number")
          }
          case Message.NextPlease(doctor) =>
            if(!queue.isEmpty)
              System.out.println("doctor get a patient" + queueMap.poll())
        }
      }
    }
    channel.basicConsume(queue, true, consumer)
  }

  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some(name))


  private def fromLongBytes(x: Array[Byte]) = new String(x, "UTF-8")

  def putOnTheEnd()

  def getFirst()

  def getAll()

  def putInitialPatients()
}

object BussinessQueue {
  private val idStore: IdStore = new IdStore()
}