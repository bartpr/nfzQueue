package services
import java.util.concurrent.TimeUnit
import javax.inject._

import akka.actor.{ActorRef, ActorSystem}
import services.Queues.{BussinessQueue, PublicQueue}
import services.Users._

import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}


@Singleton
class QueueService(){
  import com.newmotion.akka.rabbitmq._
  implicit val system = ActorSystem()
  val factory = new ConnectionFactory()
  val temp = system.actorOf(ConnectionActor.props(factory), "rabbitmq")

  implicit val connection: ActorRef = Await.result(
    system.actorSelection(temp.path.toStringWithoutAddress).resolveOne(FiniteDuration.apply(1, TimeUnit.HOURS)),
    Duration(10, TimeUnit.SECONDS)
  )
  implicit val exchange: String = "amq.fanout"
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

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
    val cli = new Client(patient)
    cli.createChannel().onComplete {
      case Success(_) =>
        Future {
          cli.publish_msg(Messages.Message.GetNumberMsg(patient), queue.name)
        }
      case Failure(exp) => throw exp
    }
  }

  def nextNumberToDoc(from: Doctor, queue: BussinessQueue) = {
    val cli = new Client(from)
    cli.createChannel().onComplete {
      case Success(_) =>
        Future {
          cli.publish_msg(Messages.Message.NextPlease(from), queue.name)
        }
      case Failure(exp) => throw exp
    }
  }

  def estimateVisitTime() = ???
  def estimateFirstAvailableShift() = ???
  def producePatiencesFromDB() = ???
  def savePatiencesToDB() = ???

  def createNewQueue(): Future[PublicQueue] = Future {
    val pq = new PublicQueue()
    QueueMap = QueueMap :+ pq
    pq
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
    val queue1 = service.createNewQueue().map(_.id)
    val queue2 = service.createNewQueue().map(_.id)
      Future.sequence(Seq(queue1, queue2)).onComplete{
      case Success(num) =>
        service.getNumber(p1, num(0))
        service.nextNumberToDoc(d2, num(0))
        service.getNumber(p1, num(1))
        service.nextNumberToDoc(d2, num(1))
      case Failure(exp) => throw exp
    }
  }

}