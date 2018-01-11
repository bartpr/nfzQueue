package services
import java.util.concurrent.TimeUnit
import javax.inject._

import akka.actor.{ActorRef, ActorSystem}
import org.joda.time.DateTime
import services.Messages.Message.{Request, Response}
import services.Queues.{ClinicQueue, PublicQueue, RPCQueue, Ticket}
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
  val temp: ActorRef = system.actorOf(ConnectionActor.props(factory), "rabbitmq")
  val MqRabbitPublicQueueIdStore: IdStore = new IdStore()

  implicit val connection: ActorRef = Await.result(
    system.actorSelection(temp.path.toStringWithoutAddress).resolveOne(FiniteDuration.apply(1, TimeUnit.HOURS)),
    Duration(10, TimeUnit.SECONDS)
  )

  implicit val exchange: String = "amq.direct"
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  var RPCQueueMap: mutable.Seq[RPCQueue] = mutable.Seq.empty[RPCQueue]

  def getNumber(from: Patient, queueId: Long): Future[Long] = {
    val queueOpt = RPCQueueMap.find(_.id == queueId)
    queueOpt.map(getNumber(from, _)) match {
      case None => throw new IllegalStateException("No queue with these number present")
      case Some(value) => value
    }
  }

  def nextNumberToDoc(from: Doctor, queueId: Long): Future[Option[Long]] = {
    val queueOpt = RPCQueueMap.find(_.id == queueId)
    queueOpt.map(nextNumberToDoc(from, _)) match {
      case None => throw new IllegalStateException("No queue with these number present")
      case Some(value) => value
    }
  }

  def getNumber(patient: Patient, queue: RPCQueue): Future[Long] = {
    val client = new Client(patient)
    getResponse(Messages.Message.GetNumberMsg(patient), client, queue).map {
      case msg: Messages.Message.YourNumberIs =>
        msg.number
    }
  }

  def nextNumberToDoc(from: Doctor, queue: RPCQueue): Future[Option[Long]] = {
    val client = new Client(from)
    getResponse(Messages.Message.NextPlease(from), client, queue).map {
        case msg: Messages.Message.NextPatientIs =>
          msg.patientId
      }
  }


  private def getResponse(req: Request, cli: Client, queue: RPCQueue): Future[Response] = {
    cli.createChannel().flatMap( _ =>
      cli.responseWaiter(req, queue.name)
    )
  }

  private def filterPublicMap(allQueueSeq: Seq[RPCQueue]): Seq[RPCQueue] = {
    allQueueSeq.filter(_.clinicQueue match {
      case _: PublicQueue => true
      case _ => false
    })
  }

  def getAllPublicQueues: Future[Seq[(ClinicQueue, Option[Ticket])]] = Future {
    filterPublicMap(
      collection.immutable.Seq(RPCQueueMap: _*)
    ).map( RpcQueue =>
      (RpcQueue.clinicQueue, RpcQueue.getCurrentTicket(None))
    )
  }

  def getMyPublicQueues(patient: Patient): Future[Seq[ClinicQueue]] = Future {
    filterPublicMap(
      collection.immutable.Seq(
        RPCQueueMap.filter(_.getQueueMapWithClient(patient)): _*
      )
    ).map(_.clinicQueue)
  }

  def getCurrentPatient(queueId: Long, doctor: Doctor) = {
    RPCQueueMap.find(_.clinicQueue.id == queueId).map(
      queue => queue.getCurrentTicket(Some(doctor))
    )
  }

  def estimateVisitTime() = ???
  def estimateFirstAvailableShift() = ???
  def producePatiencesFromDB() = ???
  def savePatiencesToDB() = ???

  /* method only because of all queue's lifecycle is in MQRabbit
  todo: creating new entity for planned queue
  */
  def getNewQueue(durationInHours: Int, doctors: Seq[Long], specializationId: Option[Long] = None): Future[PublicQueue] = {
    if(specializationId.isDefined)
      ??? //If specialization ID then private queue
    else {
      val now = new DateTime()
      MqRabbitPublicQueueIdStore.getNew.map(
        PublicQueue(
          _,
          now,
          now.plusHours(durationInHours),
          doctors
        )
      )
    }
  }


  def createNewQueue(clinicQueue: ClinicQueue): Future[RPCQueue] = {
    val pq = new RPCQueue(clinicQueue)
    RPCQueueMap = RPCQueueMap :+ pq
    pq.createChannel.map(_ => pq)
  }

  def deleteQueue = ???
  private def producePatient() = ???
  private def consumePatient() = ???
}

/* object QueueService{
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  def main(args: Array[String]): Unit = {
    def print(future: Future[Any]): Unit ={
      println(Await.result(future, FiniteDuration(2, TimeUnit.SECONDS)))
    }
    val service = new QueueService
    System.out.println("Hello")
    val p1 = new Patient(1L)
    val p2 = new Patient(2L)
    val d1 = new Doctor(1L, Seq.empty)
    val d2 = new Doctor(2L, Seq.empty)
    val queue1 = service.createNewQueue(PublicQueue(1)).map(_.id)
    val queue2 = service.createNewQueue(PublicQueue(2)).map(_.id)
      Future.sequence(Seq(queue1, queue2)).onComplete{
      case Success(num) =>
        print(service.getNumber(p1, num(0)))
        print(service.getNumber(p2, num(0)))
        print(service.nextNumberToDoc(d2, num(1)))
        print(service.getNumber(p1, num(1)))
        print(service.nextNumberToDoc(d2, num(1)))
      case Failure(exp) => throw exp
    }
  }

}*/