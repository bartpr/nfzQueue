package services.Users

class Patient(val userId: Long) extends User {

  override def permSeq: Seq[Perm] = Seq.empty

}