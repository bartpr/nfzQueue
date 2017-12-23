package services

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import services.Messages.Message

trait MqRabbitEndpoint{
  def fromBytes(byteArray: Array[Byte]): Message = {
    val input = new ByteArrayInputStream(byteArray)
    val ois = new ObjectInputStream(input)
    val msgOut = ois.readObject.asInstanceOf[Messages.Message]
    ois.close
    msgOut
  }

  def toBytes(msg: Message): Array[Byte] = {
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(byteArrayOutputStream)
    oos.writeObject(msg)
    oos.close()
    byteArrayOutputStream.toByteArray
  }

}