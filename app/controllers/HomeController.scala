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

  def getMockQueue(patient: Patient): Future[Seq[(ClinicQueue, Option[Ticket], Doctor)]] = {
    val queue = PublicQueue(1, DateTime.now().minusHours(1), DateTime.now(), Seq(1))
    val ticket = Some(Ticket(1, new Patient(7, "John", "Doe")))
    val doctor = new Doctor(6, "Doctor", "House")
    Future(Seq((queue, ticket, doctor)))
  }

  def getNumber(queueId: String) =
    Action.async { request =>
    request.session.get("userId").map { user =>
      for{
        client <- userService.getUser(user.toLong)
      } yield {
        client match {
          case Some(patient: Patient) =>
            service.getNumber(patient, queueId.toLong).map( _ =>
              Redirect(routes.LoginController.passwordChecker)
            )
          case Some(doctor: Doctor) => Future(Ok("Po co doctorowi numerek?"))
          case _ => Future(Ok("Nie jesteś zalogowany"))
        }
      }
    }.getOrElse(Future(Future(Ok("Nie jesteś zalogowany")))).flatten

  }

  //Option in none - queue empty
  def getFirstPatientInQueue(doctor: Doctor, queueId: Long): Future[Option[Long]] = {
    service.nextNumberToDoc(doctor, queueId)
  }

  def closeChannel(queueId: Long): Future[Unit] = {
    service.close(queueId)
  }

//  def queues = Action.async {
////    userService.getUser()
//    val patient = new Patient(2, "Foo", "Bar")
//    val queues = getMockQueue(patient)
//    queues.map(q =>
//      Ok(views.html.queues(q)))
//  }

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
          print(service.nextNumberToDoc(d2, num(0)))
          print(service.getNumber(p1, num(1)))
          print(service.nextNumberToDoc(d2, num(1)))
          print(service.getAllPublicQueues)
        }
      case Failure(exp) => throw exp
    }

    Ok(views.html.index("Your new application is ready."))
  }

}
