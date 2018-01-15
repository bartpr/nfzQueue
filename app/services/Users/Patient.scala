package services.Users

class Patient(val userId: Long, val name: String, val surname: String) extends User {

  override def permSeq: Seq[Perm] = Seq.empty

}