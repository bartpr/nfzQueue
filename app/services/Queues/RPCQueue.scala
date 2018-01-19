package services.Queues

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ConcurrentMap}

import scala.collection.JavaConverters._
import akka.actor.{ActorRef, ActorSystem}
import com.newmotion.akka.rabbitmq.{BasicProperties, Channel, ChannelActor, CreateChannel, DefaultConsumer, Envelope}
import services.Messages.Message
import services.Messages.Message.Response
import services.Users.{Client, ClientOwner, Doctor, User}
import services.{IdStore, MqRabbitEndpoint}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

case class Ticket(ticketId: Long, clientOwner: ClientOwner)

class RPCQueue(val clinicQueue: ClinicQueue)(implicit system: ActorSystem, connection: ActorRef,
                 exchange: String, executionContext: ExecutionContext ) extends MqRabbitEndpoint {

  val id: Long = Await.result( RPCQueue.idStore.getNew, Duration.Inf)

  override val name: String = s"queue-$id"

  private val queueMap: ConcurrentLinkedQueue[(Long, ClientOwner)] = new ConcurrentLinkedQueue[(Long, ClientOwner)]()

  private val currentPatient: ConcurrentMap[Long, Ticket] = new ConcurrentHashMap[Long, Ticket]()

  private val numbersInQueue = new IdStore()

  private var channelMq: Option[Channel] = None

  def getMyNumber(clientOwner: ClientOwner): Option[Long] = {
    queueMap.iterator().asScala.find(_._2.id == clientOwner.id).map(_._1)
  }

  def getQueueMapWithClient(clientOwner: ClientOwner): Boolean =  {
    queueMap.iterator().asScala.exists(_._2.id == clientOwner.id)
  }

  def getCurrentTicket(doctorOpt: Option[Doctor]): Option[Ticket] = {
    val a = doctorOpt.map(doctor => Option(currentPatient.get(doctor.id)))
      .getOrElse(currentPatient.values().asScala.headOption)
    a
    //todo: when more doctors -> Option to Seq
  }

  def getAllTickets: Seq[Ticket] = queueMap.iterator.asScala.toSeq.map(ticket => Ticket(ticket._1, ticket._2))

  override def setupChannel(channel: Channel, self: ActorRef) {
    //channel.exchangeDeclare(exchange, "direct")
    val queue = channel.queueDeclare(name, false, false, false, null).getQueue
    channel.queueBind(queue, exchange, name)
    val bussinessQueue = this
    val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
        val response: Response = fromBytes(body).asInstanceOf[Message.Request] match {
          case Message.GetNumberMsg(patient) => {
            val number = Await.result(numbersInQueue.getNew, Duration.Inf)
            queueMap.add( number -> patient)
            System.out.println(s"$name New patient get a number")
            Message.YourNumberIs(clinicQueue, number)
          }
          case Message.NextPlease(doctor) =>
            if (queueMap.isEmpty) {
              System.out.println(s"[$name] Empty queue")
              Message.NextPatientIs(clinicQueue, None)
            } else {
              val currElem = queueMap.poll()
              currElem match {
                case (ticket, user: User) =>
                  currentPatient.putIfAbsent(doctor.id, Ticket(ticket, user))
                  currentPatient.replace(doctor.id, Ticket(ticket, user))
                  System.out.println(s"[$name] Doctor get a patient" + user.userId)
                  Message.NextPatientIs(clinicQueue, Some(user.userId))
                case _ => throw new IllegalStateException("only user can go to clinic")
              }
            }
        }
        channelMq = Some(channel)
        channel.basicPublish(exchange, properties.getReplyTo, null, bussinessQueue.toBytes(response))
      }
    }
    channel.basicConsume(queue, true, consumer)
  }

  def close(): Future[Unit] = Future(channelMq.foreach(_.close()))

  private def fromLongBytes(x: Array[Byte]) = new String(x, "UTF-8")

  def putOnTheEnd() = ???

  def getFirst() = ???

  def getAll() = ???

  def putInitialPatients() = ???
}

object RPCQueue {
  private val idStore: IdStore = new IdStore()
}