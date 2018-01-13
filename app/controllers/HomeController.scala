package controllers

import java.util.concurrent.TimeUnit
import javax.inject._

import akka.actor._
import com.rabbitmq.client.Channel
import org.joda.time.DateTime
import play.api.mvc._
import services.QueueService
import services.Queues.{ClinicQueue, PublicQueue, Ticket}
import services.Users.{Doctor, Patient}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration.FiniteDuration


object HomeController{

}
@Singleton
class HomeController @Inject()(cc: ControllerComponents, service: QueueService)(implicit exec: ExecutionContext) extends AbstractController(cc) {

  def publish(channel: Channel) {
    channel.basicPublish("", "queue_name", null, "Hello world rabbit".getBytes)
  }

  def setupChannel(channel: Channel, self: ActorRef) {
    channel.queueDeclare("queue_name", false, false, false, null)
  }

  def createNewPublicQueue(durationInHours: Int, doctor: Doctor): Future[Long] = {
    service.getNewQueue(durationInHours, Seq(doctor.userId)).flatMap( queue =>
      service.createNewQueue(queue).map(_.id)
    )
  }

  //option - none = no patient in doctor
  def getAllPublicQueues: Future[Seq[(ClinicQueue, Option[Ticket], Doctor)]] = {
    service.getAllPublicQueues.map(_.map(elem => (elem._1, elem._2, new Doctor(elem._1.id, Seq.empty))))
  } //todo: getDoctorData from base

  //option - none = no patient in doctor
  def getMyQueues(patient: Patient): Future[Seq[(ClinicQueue, Option[Ticket], Doctor)]] ={
    service.getMyPublicQueues(patient).map(_.map(elem => (elem._1, elem._2, new Doctor(elem._1.id, Seq.empty))))
  } //todo: getDoctorData from base

  def getAllPatientsInQueue(queueId: Long): Future[Seq[(Ticket, Patient)]] = {
    service.getAllPatientsIds(queueId).map(_.map( ticket =>
      (ticket, new Patient(1L)) //todo: getPatientDataFormDatabase
    ))
  }

  def getCurrentPatient(queueId: Long, doctor: Doctor):Future[Option[Ticket]] = {
    service.getCurrentPatient(queueId, doctor)
  }

  def getNumber(patient: Patient, queueId: Long): Future[Long] = {
    service.getNumber(patient, queueId)
  }

  //Option in none - queue empty
  def getFirstPatientInQueue(doctor: Doctor, queueId: Long): Future[Option[Long]] = {
    service.nextNumberToDoc(doctor, queueId)
  }

  def closeChannel(queueId: Long): Future[Unit] = {
    service.close(queueId)
  }

  def index = Action {
    import com.newmotion.akka.rabbitmq._
    implicit val system = ActorSystem()

    def print(future: Future[Any]): Unit ={
      println(Await.result(future, FiniteDuration(2, TimeUnit.SECONDS)))
    }

    System.out.println("Hello")
    val p1 = new Patient(1L)
    val p2 = new Patient(2L)
    val d1 = new Doctor(1L, Seq.empty)
    val d2 = new Doctor(2L, Seq.empty)
    val queue1 = createNewPublicQueue(2, d1)
    val queue2 = createNewPublicQueue(2, d2)
    Future.sequence(Seq(queue1, queue2)).onComplete{
      case Success(num) =>
        Future {
          print(service.getNumber(p1, num(0)))
          print(service.getNumber(p2, num(0)))
          print(service.nextNumberToDoc(d2, num(1)))
          print(service.getNumber(p1, num(1)))
          print(service.nextNumberToDoc(d2, num(1)))
        }.onComplete {
         case Success(_) => closeChannel(0L)
         case Failure(_) => ()
        }
      case Failure(exp) => throw exp
    }

    Ok(views.html.index("Your new application is ready."))
  }

}
