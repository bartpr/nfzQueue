package controllers

import javax.inject._

import play.api.data._
import play.api.data.Form
import play.api.data.Forms._
import play.api.db._
import play.api.mvc._
import services.Queues.{ClinicQueue, Ticket}
import services.{QueueService, UserService}
import services.Users.{Doctor, Patient}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success




case class LoginInfo(login: String, pass: String)

case class QueueInfo(hours: Int)

@Singleton
class LoginController @Inject()(db: Database, cc: ControllerComponents, userServ: UserService, service: QueueService)(implicit exec: ExecutionContext, messagesAction: MessagesActionBuilder) extends AbstractController(cc) {

  def createNewPublicQueue =
    messagesAction.async { implicit request: MessagesRequest[AnyContent] =>
      queueTicketForm.bindFromRequest.fold(
        formWithErrors => {
          Future(BadRequest(views.html.doctor_no_queue(formWithErrors)))
        },
        queueInfo =>
          request.session.get("userId").map { user =>
            for {
              client <- userServ.getUser(user.toLong)
            } yield {
              client match {
                case Some(patient: Patient) =>
                  Future(Ok("Nie jestes doktorem"))
                case Some(doctor: Doctor) =>
                  service.getNewQueue(queueInfo.hours, Seq(doctor.userId)).flatMap(queue =>
                    service.createNewQueue(queue).map(_.id)
                  ).map(_ => Redirect(routes.LoginController.passwordChecker))
                case _ => Future(Ok("Nie jesteś zalogowany"))
              }
            }
          }.getOrElse(Future(Future(Ok("Nie jesteś zalogowany")))).flatten
      )
    }

  //option - none = no patient in doctor
  def getAllPublicQueues: Future[Seq[(ClinicQueue, Option[Ticket], Doctor)]] = {
    for{
      queues <- service.getAllPublicQueues
      user <- Future.sequence(queues.map(elem => userServ.getUser(elem._1.doctorsIds.head).map(_.map(_.asInstanceOf[Doctor]).get)))
    } yield {
      (queues zip user).map( zipped =>
        (zipped._1._1, zipped._1._2, zipped._2)
      )
    }
  }

  def getMyQueues(patient: Patient): Future[Seq[(ClinicQueue, Option[Ticket], Doctor, Option[Long])]] = {
    for {
      queues <- service.getMyPublicQueues(patient)
      user <- Future.sequence(queues.map(elem => userServ.getUser(elem._1.doctorsIds.head).map(_.map(_.asInstanceOf[Doctor]).get)))
    } yield {
      (queues zip user).map( zipped =>
        (zipped._1._1, zipped._1._2, zipped._2, zipped._1._3)
      )
    }
  }

  def doctorView(doctor: Doctor)(implicit request: MessagesRequest[AnyContent]): Future[Result] = {
    def getAllPatientsInQueue(queueId: Long): Future[Seq[Ticket]] = {
      for {
        queues <- service.getAllPatientsIds(queueId)
      } yield queues
    }

    def getCurrentPatient(queueId: Long, doctor: Doctor):Future[Option[Ticket]] = {
      service.getCurrentPatient(queueId, doctor)
    }

    for {
      queueId <- service.getMyQueue(doctor)
      allPatients <- queueId.map( id => getAllPatientsInQueue(id)).getOrElse(Future(Seq.empty))
      currentPatient <- queueId.map( id => getCurrentPatient(id, doctor)).getOrElse(Future(None))
      allPatientsData <- Future.sequence(allPatients.map(tick => userServ.getUser(tick.clientOwner.id)))
      currentPatientData <- currentPatient.map(tick => userServ.getUser(tick.clientOwner.id)).getOrElse(Future(None))
    } yield {
        val ticketDataPair = allPatients zip allPatientsData
        val flaternDataPair = ticketDataPair.filter(_._2.isDefined).map(old => (old._1.ticketId, old._2.get))
      queueId.map(id => Ok(views.html.doctor_view(id)(currentPatientData)(flaternDataPair)))
        .getOrElse(Ok(views.html.doctor_no_queue(queueTicketForm)))
    }
  }

  val queueTicketForm = Form(
    mapping(
      "hours" -> number
    )(QueueInfo.apply)(QueueInfo.unapply)
  )

  val userForm = Form(
    mapping(
      "login" -> nonEmptyText,
      "pass" -> nonEmptyText
    )(LoginInfo.apply)(LoginInfo.unapply)
  )

  private def resolvingUserQuery(username: String, password: String): Future[Option[Long]] = Future{
    val conn = db.getConnection()
    try {
      val stmt = conn.createStatement
      val rs = stmt.executeQuery(
        "SELECT \"userId\" FROM public.\"User\" WHERE \"UserName\" = '" + username + "' AND \"Password\" = '" + password + "' ;"
      )
      if(rs.next())
        Some(rs.getLong(1))
      else None
    } finally {
      conn.close()
    }
  }

  def userPost ={
    def passwordCheck(id: String, pass: String): Future[Result] = {
      val loggedId = resolvingUserQuery(id, pass)
      loggedId.map {
        _.map(userId =>
          Redirect(routes.LoginController.passwordChecker).withSession(
            "userId" -> userId.toString
          )
        ).getOrElse(Ok(s"Hasło nie prawidłowe"))
      }
    }
      messagesAction.async { implicit request: MessagesRequest[AnyContent] =>
        userForm.bindFromRequest.fold(
          formWithErrors => {
            Future(BadRequest(views.html.login("Login")(formWithErrors)))
          },
          newUser => {
            passwordCheck(newUser.login, newUser.pass)
          }
        )
      }
    }

  def passwordChecker =
    messagesAction.async { implicit request: MessagesRequest[AnyContent] =>
      request.session.get("userId").map { user =>
        for{
          client <- userServ.getUser(user.toLong)
          q <- getAllPublicQueues
        } yield
        client match {
          case Some(patient: Patient) => getMyQueues(patient).map(myQ=> Ok(views.html.queues(myQ)(q)))
          case Some(doctor: Doctor) => doctorView(doctor)
          case _ => Future(Ok("Nie jesteś zalogowany"))
        }
      }.getOrElse(Future(Future(Ok("Nie jesteś zalogowany")))).flatten
    }


  def message = messagesAction { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.login("Login")(userForm))
    //resolvingUserQuery("testo", "testo").map(str => Ok(str.map(_.toString).getOrElse("None")))
  }


}