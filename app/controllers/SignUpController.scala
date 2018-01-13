package controllers

import javax.inject._

import akka.actor.ActorSystem
import play.api.db.Database
import play.api.mvc._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

@Singleton
class SignUpController @Inject()(db: Database, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends AbstractController(cc) {

  def message = Action.async {
    getFutureMessage(1.second).map { msg => Ok(msg) }
  }

  private def createPatientQuery(username: String,  password: String, name: String, lastname: String): String = {
    "INSERT INTO public.\"User\"( \"UserName\", \"Type\", \"Password\", \"Name\", \"Lastname\") VALUES " + s"('$username', 1, '$password', '$name', '$lastname');"
  }

  private def createDoctorQuery(username: String, password: String, name: String, lastname: String): String = {
    "INSERT INTO public.\"User\"( \"UserName\", \"Type\", \"Password\", \"Name\", \"Lastname\") VALUES " + s"('$username', 2, '$password', '$name', '$lastname');"
  }

  def createPatient(username: String, password: String, name: String, lastname: String, patient: Boolean ) = {
    val conn = db.getConnection()
    try {
      val stmt = conn.createStatement
      stmt.execute(
        if(patient) createPatientQuery(username, password, name, lastname) else createDoctorQuery(username, password, name, lastname)
      )
    } finally {
      conn.close()
    }
  }


  private def getFutureMessage(delayTime: FiniteDuration): Future[String] = {
    createPatient("anna", "mikolaj", "Anna", "Bytnar", patient = true)
    val promise: Promise[String] = Promise[String]()
    actorSystem.scheduler.scheduleOnce(delayTime) {
      promise.success("Hijajaja!")
    }(actorSystem.dispatcher) // run scheduled tasks using the actor system's dispatcher
    promise.future
  }

}
