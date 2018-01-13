package controllers

import javax.inject._

import play.api.db._
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LoginController @Inject()(db: Database, cc: ControllerComponents)(implicit exec: ExecutionContext) extends AbstractController(cc) {
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

  def message = Action.async {
    resolvingUserQuery("testo", "testo").map(str => Ok(str.map(_.toString).getOrElse("None")))
  }


}