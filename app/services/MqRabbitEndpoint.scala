package services

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.Patterns
import akka.util.Timeout
import com.newmotion.akka.rabbitmq.{Channel, ChannelActor, CreateChannel}
import services.Messages.Message

import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait MqRabbitEndpoint{

  def setupChannel(channel: Channel, actorRef: ActorRef): Any
  def createChannel()(implicit connection: ActorRef): Future[AnyRef] =
    Patterns.ask(connection, CreateChannel(ChannelActor.props(setupChannel), Some(name)), new Timeout(Duration.apply(10, TimeUnit.SECONDS)))

  def fromBytes(byteArray: Array[Byte]): Message = {
    val input = new ByteArrayInputStream(byteArray)
    val ois = new ObjectInputStream(input)
    val msgOut = ois.readObject.asInstanceOf[Messages.Message]
    ois.close
    msgOut
  }

  def toBytes(msg: Message): Array[Byte] = {
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(byteArrayOutputStream)
    oos.writeObject(msg)
    oos.close()
    byteArrayOutputStream.toByteArray
  }

  def name: String
//  def publish_msg: Unit


}