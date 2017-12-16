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

  def getNumber(from: Patient, queue: BussinessQueue) = {
    val msg = Messages.Message.GetNumberMsg(from)

//    System.out.println(msg)
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(byteArrayOutputStream)
    oos.writeObject(msg)
    oos.close()
    val tab = byteArrayOutputStream.toByteArray
//    System.out.println(tab)
    val input = new ByteArrayInputStream(tab)
    val ois = new ObjectInputStream(input)
    val msgOut = ois.readObject.asInstanceOf[Messages.Message]
//    System.out.println(msgOut.from.asInstanceOf[Patient].userId)
    ois.close
    Future{
      new Client(from)
    }.onComplete {
      case Success(cli) =>
        Future {
          def loop(n: Long) {
            cli.publish_msg(n)
            Thread.sleep(1000)
            loop(n + 1)
          }
          loop(0)
        }
      case Failure(exp) => throw exp
    }
  }

  def nextNumberToDoc() = ???
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
    Future {
      service.createNewQueue()
    }.onComplete{
      case Success(num) => service.getNumber(p1, num)
      case Failure(exp) => throw exp
    }
  }

}