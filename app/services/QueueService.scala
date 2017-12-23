package services
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import javax.inject._

import akka.actor.ActorSystem
import services.Queues.{BussinessQueue, PublicQueue}
import services.Users._

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}


@Singleton
class QueueService(){
  import com.newmotion.akka.rabbitmq._
  implicit val system = ActorSystem()
  val factory = new ConnectionFactory()
  implicit val connection = system.actorOf(ConnectionActor.props(factory), "rabbitmq")
  implicit val exchange = "amq.fanout"
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  var QueueMap: mutable.Seq[BussinessQueue] = mutable.Seq.empty[BussinessQueue]

  def getNumber(from: Patient, queueId: Long): Boolean = {
    val queueOpt = QueueMap.find(_.id == queueId)
    queueOpt.foreach(getNumber(from, _))
    queueOpt.isDefined
  }

  def nextNumberToDoc(from: Doctor, queueId: Long): Boolean = {
    val queueOpt = QueueMap.find(_.id == queueId)
    queueOpt.foreach(nextNumberToDoc(from, _))
    queueOpt.isDefined
  }

  def getNumber(patient: Patient, queue: BussinessQueue) = {
    Future{
      new Client(patient)
    }.onComplete {
      case Success(cli) =>
        Future {
          def loop(n: Long) {
            cli.publish_msg(Messages.Message.GetNumberMsg(patient), queue.name)
            Thread.sleep(1000)
            loop(n + 1)
          }
          loop(0)
        }
      case Failure(exp) => throw exp
    }
  }

  def nextNumberToDoc(from: Doctor, queue: BussinessQueue) = Future{
    new Client(from)
  }.onComplete {
    case Success(cli) =>
      Future {
        def loop(n: Long) {
          cli.publish_msg(Messages.Message.NextPlease(from), queue.name)
          Thread.sleep(3000)
          loop(n + 1)
        }
        loop(0)
      }
    case Failure(exp) => throw exp
  }

  def estimateVisitTime() = ???
  def estimateFirstAvailableShift() = ???
  def producePatiencesFromDB() = ???
  def savePatiencesToDB() = ???

  def createNewQueue(): Long = {
    val pq = new PublicQueue()
    QueueMap = QueueMap :+ pq
    pq.id
  }

  def deleteQueue = ???
  private def producePatient() = ???
  private def consumePatient() = ???
}

object QueueService{
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  def main(args: Array[String]): Unit = {
    val service = new QueueService
    System.out.println("Hello")
    val p1 = new Patient(1L)
    val p2 = new Patient(2L)
    val d1 = new Doctor(1L, Seq.empty)
    val d2 = new Doctor(2L, Seq.empty)
    Future {
      val queue1 = service.createNewQueue()
      val queue2 = service.createNewQueue()
      (queue1, queue2)
    }.onComplete{
      case Success(num) =>
        service.getNumber(p1, num._1)
        service.nextNumberToDoc(d2, num._1)
        service.getNumber(p1, num._2)
        service.nextNumberToDoc(d2, num._2)
      case Failure(exp) => throw exp
    }
  }

}