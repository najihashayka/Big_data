import com.mongodb.client.MongoClients
import com.mongodb.client.model.{Filters, Sorts}
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.json.JsonWriterSettings

import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.util.Try

object Part3 {

  // Returns None if the user just presses Enter
  def readOptional(prompt: String): Option[String] = {
    val s = StdIn.readLine(prompt)
    if (s == null) { println("No input available."); sys.exit(1) }
    if (s.trim.isEmpty) None else Some(s.trim)
  }

  @annotation.tailrec
  def askCourseId(): Option[Int] =
    readOptional("Course ID (integer, press Enter to skip): ") match {
      case None => None
      case Some(s) =>
        Try(s.toInt).toOption match {
          case Some(v) => Some(v)
          case None =>
            println(s"Invalid input '$s': the course ID must be an integer.")
            askCourseId()
        }
    }

  @annotation.tailrec
  def askMinGpa(): Option[Double] =
    readOptional("Minimum GPA (0.0 - 4.0, press Enter to skip): ") match {
      case None => None
      case Some(s) =>
        Try(s.toDouble).toOption.filter(g => g >= 0.0 && g <= 4.0) match {
          case Some(v) => Some(v)
          case None =>
            println(s"Invalid input '$s': the GPA must be a number between 0.0 and 4.0.")
            askMinGpa()
        }
    }

  // Collect every index name that appears anywhere in the winning plan
  def indexNames(d: Document): List[String] = {
    val own = Option(d.getString("indexName")).toList
    val inner = d.keySet.asScala.toList.flatMap { k =>
      d.get(k) match {
        case sub: Document => indexNames(sub)
        case _             => Nil
      }
    }
    own ++ inner
  }

  def main(args: Array[String]): Unit = {
    val courseOpt = askCourseId()
    val gpaOpt = askMinGpa()

    val conditions: List[Bson] =
      courseOpt.map(c => Filters.eq("courseIds", c): Bson).toList ++
        gpaOpt.map(g => Filters.gt("gpa", g): Bson).toList   // gt = strictly greater

    if (conditions.isEmpty) {
      println("Please provide at least a course ID or a minimum GPA.")
      sys.exit(1)
    }

    val filter = Filters.and(conditions.asJava)
    val sort = Sorts.orderBy(Sorts.descending("gpa"), Sorts.ascending("lastName", "firstName"))

    val client = MongoClients.create(Config.uri)
    try {
      val coll = client.getDatabase("university").getCollection("students")

      val results = coll.find(filter).sort(sort).asScala.toList

      val desc = List(
        courseOpt.map(c => s"course = $c"),
        gpaOpt.map(g => s"GPA > $g")
      ).flatten.mkString(" AND ")
      println(s"\nStudents with $desc (sorted by GPA descending):")
      println("%-5s %-25s %s".format("ID", "Name", "GPA"))
      results.foreach { d =>
        val id: Int = d.getInteger("_id")
        val name = s"${d.getString("firstName")} ${d.getString("lastName")}"
        val gpa: Double = d.getDouble("gpa")
        println("%-5d %-25s %.1f".format(id, name, gpa))
      }
      println(s"\nNumber of matches: ${results.size}")

      // Which index did the planner choose?
      val explain = coll.find(filter).sort(sort).explain()
      val winningPlan = explain.get("queryPlanner", classOf[Document]).get("winningPlan", classOf[Document])
      val used = indexNames(winningPlan).distinct
      println(
        if (used.nonEmpty) s"Index used by the winning plan: ${used.mkString(", ")}"
        else "No index used (collection scan)."
      )
    } finally client.close()
  }
}

