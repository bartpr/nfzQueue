package controllers

import javax.inject._

import play.api.data._
import play.api.data.Form
import play.api.data.Forms._
import play.api.db._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

case class LoginInfo(login: String, pass: String)

@Singleton
class LoginController @Inject()(db: Database, cc: ControllerComponents)(implicit exec: ExecutionContext, messagesAction: MessagesActionBuilder) extends AbstractController(cc) {

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

  def userPost =
    messagesAction { implicit request: MessagesRequest[AnyContent] =>
    userForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.login("Login")(formWithErrors))},
      newUser => {
        Redirect(routes.LoginController.keke(newUser.login, newUser.pass))
      })
    }




  def keke(id: String, pass: String) = Action { Ok(s"id: $id, pass: $pass") }

  def message = messagesAction { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.login("Login")(userForm))
    //resolvingUserQuery("testo", "testo").map(str => Ok(str.map(_.toString).getOrElse("None")))
  }


}