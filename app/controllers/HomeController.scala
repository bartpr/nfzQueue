package controllers

import java.util.concurrent.TimeUnit
import javax.inject._

import akka.actor._
import com.rabbitmq.client.Channel
import play.api.mvc._

import scala.concurrent.Future

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  def publish(channel: Channel) {
    channel.basicPublish("", "queue_name", null, "Hello world rabbit".getBytes)
  }

  def setupChannel(channel: Channel, self: ActorRef) {
    channel.queueDeclare("queue_name", false, false, false, null)
  }




  def index = Action {
    import com.newmotion.akka.rabbitmq._
    implicit val system = ActorSystem()
    val factory = new ConnectionFactory()
    val connection = system.actorOf(ConnectionActor.props(factory), "rabbitmq")
    val exchange = "amq.fanout"


    def setupPublisher(channel: Channel, self: ActorRef) {
      val queue = channel.queueDeclare().getQueue
      channel.queueBind(queue, exchange, "")
    }
    connection ! CreateChannel(ChannelActor.props(setupPublisher), Some("publisher"))


    def setupSubscriber(channel: Channel, self: ActorRef) {
      val queue = channel.queueDeclare().getQueue
      channel.queueBind(queue, exchange, "")
      val consumer = new DefaultConsumer(channel) {
        override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
          println("received: " + fromBytes(body))
        }
      }
      channel.basicConsume(queue, true, consumer)
    }
    connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("subscriber"))

    import scala.concurrent.ExecutionContext.global
    Future {
      def loop(n: Long) {
        val publisher = system.actorSelection("/user/rabbitmq/publisher")

        def publish(channel: Channel) {
          channel.basicPublish(exchange, "", null, toBytes(n))
        }
        publisher ! ChannelMessage(publish, dropIfNoChannel = false)

        Thread.sleep(1000)
        loop(n + 1)
      }
      loop(0)
    }(global)

    def fromBytes(x: Array[Byte]) = new String(x, "UTF-8")
    def toBytes(x: Long) = x.toString.getBytes("UTF-8")


    System.out.println("Hello world")
    Ok(views.html.index("Your new application is ready."))
  }

}
