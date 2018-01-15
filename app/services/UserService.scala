package services

import javax.inject.{Inject, Singleton}
import play.api.db.Database
import services.Users.{Doctor, Patient, User}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class UserService@Inject()(db: Database)(implicit val ec: ExecutionContext){
  def getUser(userId: Long): Future[Option[User]] = Future{
    val conn = db.getConnection()
    try {
      val stmt = conn.createStatement
      val rs = stmt.executeQuery(
        "SELECT \"UserName\", \"Type\", \"Name\", \"Lastname\" FROM public.\"User\" WHERE \"userId\" = '" + userId + "' ;"
      )
      if(rs.next())
        rs.getInt(2) match {
          case 1 =>
            Some(new Patient(rs.getLong(1), rs.getString(3), rs.getString(4)))
          case 2 => Some(new Doctor(rs.getLong(1), rs.getString(3), rs.getString(4)))
          case _ => throw new IllegalStateException()
        }
      else None
    } finally {
      conn.close()
    }
  }
}