import com.mongodb.client.MongoClients
import com.mongodb.client.model.{Filters, Sorts}
import org.bson.Document
import org.bson.json.JsonWriterSettings

import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.util.Try

object Part2 {

  // Ask until the user gives a valid integer
  @annotation.tailrec
  def askCourseId(): Int = {
    val s = StdIn.readLine("Enter course ID (integer): ")
    if (s == null) { println("No input available."); sys.exit(1) }
    Try(s.trim.toInt).toOption match {
      case Some(v) => v
      case None =>
        println(s"Invalid input '$s': the course ID must be an integer.")
        askCourseId()
    }
  }

  def main(args: Array[String]): Unit = {
    // Course ID can be passed as an argument, otherwise we prompt for it
    val courseId = args.headOption.flatMap(a => Try(a.trim.toInt).toOption).getOrElse(askCourseId())

    val client = MongoClients.create(Config.uri)
    try {
      val coll = client.getDatabase("university").getCollection("students")

      val filter = Filters.eq("courseIds", courseId)   // matches arrays that contain courseId
      val sort = Sorts.ascending("lastName", "firstName")

      val results = coll.find(filter).sort(sort).asScala.toList

      println(s"\nStudents registered in course $courseId:")
      println("%-5s %-25s %s".format("ID", "Name", "GPA"))
      results.foreach { d =>
        val id: Int = d.getInteger("_id")
        val name = s"${d.getString("firstName")} ${d.getString("lastName")}"
        val gpa: Double = d.getDouble("gpa")
        println("%-5d %-25s %.1f".format(id, name, gpa))
      }
      println(s"\nNumber of matches: ${results.size}")

      // explain()
      val explain = coll.find(filter).sort(sort).explain()
      val winningPlan = explain.get("queryPlanner", classOf[Document]).get("winningPlan", classOf[Document])
      val json = winningPlan.toJson(JsonWriterSettings.builder().indent(true).build())

      println("\n--- explain(): winning plan ---")
      println(json)
      val usedIndex = json.contains("IXSCAN")
      println(s"\nIXSCAN in winning plan: $usedIndex")
      println(
        if (usedIndex) "=> The courseIds index (multikey) WAS used."
        else "=> The courseIds index was NOT used (collection scan)."
      )
    } finally client.close()
  }
}