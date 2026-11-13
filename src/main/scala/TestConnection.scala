import com.mongodb.client.MongoClients
import org.bson.Document

object TestConnection {
  def main(args: Array[String]): Unit = {
    val client = MongoClients.create(Config.uri)
    try {
      val res = client.getDatabase("admin").runCommand(new Document("ping", 1))
      println(s"Ping result: ${res.toJson}")
    } finally client.close()
  }
}
