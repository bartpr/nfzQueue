package services

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.global
class IdStore(){

  implicit val ec: ExecutionContext = IdStore.executionContext

  private var id: Long = 0L

  def getNew: Future[Long] = Future{
    this.synchronized{
      val curr = id
      id += 1
      curr
    }
  }
}

object IdStore{
  val executionContext: ExecutionContext = global

}