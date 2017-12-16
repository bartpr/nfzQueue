package services.Users

trait User extends ClientOwner{
  def userId: Long
  def permSeq: Seq[Perm]
  def hasPerm(perm: Perm): Boolean = {
    permSeq.contains(perm)
  }
}