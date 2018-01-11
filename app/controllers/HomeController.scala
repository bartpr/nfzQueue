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

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */

object HomeController{

}
@Singleton
class HomeController @Inject()(cc: ControllerComponents, service: QueueService)(implicit exec: ExecutionContext) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

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

  def getAllPublicQueues: Future[Seq[(ClinicQueue, Option[Ticket])]] = {
    service.getAllPublicQueues
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
        print(service.getNumber(p1, num(0)))
        print(service.getNumber(p2, num(0)))
        print(service.nextNumberToDoc(d2, num(1)))
        print(service.getNumber(p1, num(1)))
        print(service.nextNumberToDoc(d2, num(1)))
      case Failure(exp) => throw exp
    }

    Ok(views.html.index("Your new application is ready."))
  }

}
