package services.Queues

import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq.{BasicProperties, Channel, ChannelActor, CreateChannel, DefaultConsumer, Envelope}
import services.IdStore

import scala.concurrent.Await
import scala.concurrent.duration.Duration

abstract class BussinessQueue()(implicit system: ActorSystem,
                                connection: ActorRef,
                                exchange: String ) {
  val id: Long = Await.result( BussinessQueue.idStore.getNew, Duration.Inf)


  private def setupSubscriber(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, "")
    val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
        println("received: " + fromBytes(body))
      }
    }
    channel.basicConsume(queue, true, consumer)
  }

  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("subscriber"))


  private def fromBytes(x: Array[Byte]) = new String(x, "UTF-8")

  def putOnTheEnd()

  def getFirst()

  def getAll()

  def putInitialPatients()
}

object BussinessQueue {
  private val idStore: IdStore = new IdStore()
}