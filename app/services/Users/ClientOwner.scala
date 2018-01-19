package services.Users

//marker For possible Clients owners
@SerialVersionUID(448276L)
trait ClientOwner extends Serializable {
  def id: Long
}