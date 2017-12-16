package services.Users

//stub for permissions

sealed trait Perm extends Product with Serializable

object Perm {

  final case class creatingQueue(specialization: Seq[String]) extends Perm

  final case object creatingUsers extends Perm

}