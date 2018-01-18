package controllers

import java.util.concurrent.TimeUnit
import javax.inject._

import akka.actor._
import com.rabbitmq.client.Channel
import org.joda.time.DateTime
import play.api.db.Database
import play.api.mvc._
import services.{QueueService, UserService}
import services.Queues.{ClinicQueue, PublicQueue, Ticket}
import services.Users.{Doctor, Patient, User}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration.FiniteDuration


object HomeController{

}
@Singleton
class HomeController @Inject()(db: Database, cc: ControllerComponents, service: QueueService, userService: UserService)
                              (implicit exec: ExecutionContext) extends AbstractController(cc) {

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
    for{
      queues <- service.getAllPublicQueues
      user <- Future.sequence(queues.map(elem => userService.getUser(elem._1.doctorsIds.head).map(_.asInstanceOf[Doctor])))
    } yield {
      (queues zip user).map( zipped =>
        (zipped._1._1, zipped._1._2, zipped._2)
      )
    }
  }

  //option - none = no patient in doctor
  def getMyQueues(patient: Patient): Future[Seq[(ClinicQueue, Option[Ticket], Doctor)]] = {
    for {
    queues <- service.getMyPublicQueues(patient)
    user <- Future.sequence(queues.map(elem => userService.getUser(elem._1.doctorsIds.head).map(_.asInstanceOf[Doctor])))
    } yield {
      (queues zip user).map( zipped =>
        (zipped._1._1, zipped._1._2, zipped._2)
      )
    }
  }

  def getMockQueue(patient: Patient): Future[Seq[(ClinicQueue, Option[Ticket], Doctor)]] = {
    val queue = PublicQueue(1, DateTime.now().minusHours(1), DateTime.now(), Seq(1, 2, 3))
    val ticket = Some(Ticket(1, new Patient(7, "John", "Doe")))
    val doctor = new Doctor(6, "Doctor", "House")
    Future(Seq((queue, ticket, doctor)))
  }

  def getAllPatientsInQueue(queueId: Long): Future[Seq[Ticket]] = {
    for {
      queues <- service.getAllPatientsIds(queueId)
    } yield queues
  }

  def getCurrentPatient(queueId: Long, doctor: Doctor):Future[Option[Ticket]] = {
    service.getCurrentPatient(queueId, doctor) //todo: getPatientDataFormDatabase
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

  def queues = Action.async {
    val patient = new Patient(2, "Foo", "Bar")
    val queues = getMockQueue(patient)
    queues.map(q => Ok(views.html.queues(q)))
  }

  def index = Action {
    implicit val system = ActorSystem()

    def print(future: Future[Any]): Unit ={
      println(Await.result(future, FiniteDuration(2, TimeUnit.SECONDS)))
    }

    System.out.println("Hello")
    val p1 = new Patient(1L, "imie", "nazwisko")
    val p2 = new Patient(2L, "imie", "nazwisko")
    val d1 = new Doctor(1L, "imie", "nazwisko")
    val d2 = new Doctor(2L, "imie", "nazwisko")
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
          print(service.getAllPublicQueues)
        }.onComplete {
         case Success(_) => closeChannel(0L)
         case Failure(_) => ()
        }
      case Failure(exp) => throw exp
    }

    Ok(views.html.index("Your new application is ready."))
  }

}
