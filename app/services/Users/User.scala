package services.Users

trait User extends ClientOwner{
  def userId: Long
  def permSeq: Seq[Perm]
  def name: String
  def surname: String
  def hasPerm(perm: Perm): Boolean = {
    permSeq.contains(perm)
  }
}