import com.mongodb.client.MongoClients
import com.mongodb.client.model.Indexes
import org.bson.Document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.{LocalDate, ZoneOffset}
import java.util.Date
import scala.jdk.CollectionConverters._

object Part1 {

  // Parse one CSV line: first 4 fields, last field = GPA, everything in between = course list
  def parseLine(line: String): Document = {
    val p = line.split(",", -1).map(_.trim)
    val id = p(0).toInt
    val firstName = p(1)
    val lastName = p(2)
    val dob = Date.from(LocalDate.parse(p(3)).atStartOfDay(ZoneOffset.UTC).toInstant)
    val gpa = p.last.toDouble

    val coursesRaw = p.slice(4, p.length - 1).mkString(",")
    val courses: List[Integer] = coursesRaw
      .stripPrefix("[").stripSuffix("]")
      .split(",").map(_.trim).filter(_.nonEmpty)
      .map(s => Int.box(s.toInt)).toList

    new Document("_id", id)
      .append("firstName", firstName)
      .append("lastName", lastName)
      .append("dateOfBirth", dob)
      .append("courseIds", courses.asJava)
      .append("gpa", gpa)
  }

  def main(args: Array[String]): Unit = {
    val lines = Files.readAllLines(Paths.get("hw2-data.csv"), StandardCharsets.UTF_8)
      .asScala.toList.drop(1).filter(_.trim.nonEmpty)

    val docs = lines.map(parseLine)

    // Save to students.json (one JSON document per line)
    Files.write(Paths.get("students.json"), docs.map(_.toJson).asJava, StandardCharsets.UTF_8)
    println(s"Wrote ${docs.size} documents to students.json")

    val client = MongoClients.create(Config.uri)
    try {
      val coll = client.getDatabase("university").getCollection("students")

      coll.drop() // safe to re-run
      coll.insertMany(docs.asJava)

      coll.createIndex(Indexes.ascending("courseIds")) // multikey (courseIds is an array)
      coll.createIndex(Indexes.ascending("gpa"))       // single-field

      println(s"Inserted documents: ${coll.countDocuments()}")
      println("Indexes:")
      coll.listIndexes().asScala.foreach(ix => println("  " + ix.toJson))
    } finally client.close()
  }
}