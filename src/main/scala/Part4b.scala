import org.apache.spark.sql.SparkSession

object Part4b {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("HW2-SparkSQL-part2")
      .master("local[*]")
      .config("spark.driver.bindAddress", "127.0.0.1")
      .config("spark.sql.session.timeZone", "UTC") // dates were stored as UTC midnight
      .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    val df = spark.read.format("mongodb")
      .option("connection.uri", Config.uri)
      .option("database", "university")
      .option("collection", "students")
      .load()
    df.createOrReplaceTempView("students")

    // Print the SQL, then its output
    def run(title: String, sql: String): org.apache.spark.sql.DataFrame = {
      println(s"\n=== $title ===")
      println(sql.trim)
      val r = spark.sql(sql)
      r.show(200, truncate = false)
      r
    }

    // ---------- 4.2 ----------
    println("\n\n########## 4.2 Working with the array column ##########")

    spark.sql(
      """CREATE OR REPLACE TEMP VIEW enrollments AS
        |SELECT _id AS student_id, firstName, lastName, gpa, explode(courseIds) AS course_id
        |FROM students""".stripMargin)
    run("4.2a enrollments (first rows)",
      "SELECT * FROM enrollments ORDER BY student_id, course_id LIMIT 10")
    println(s"enrollment rows: ${spark.sql("SELECT * FROM enrollments").count()}")

    val courseSummary = run("4.2b Per-course summary",
      """SELECT course_id,
        |       COUNT(*)            AS num_students,
        |       ROUND(AVG(gpa), 2)  AS avg_gpa,
        |       MIN(gpa)            AS min_gpa,
        |       MAX(gpa)            AS max_gpa
        |FROM enrollments
        |GROUP BY course_id
        |ORDER BY num_students DESC, course_id""".stripMargin)

    run("4.2c Students by number of courses",
      """SELECT size(courseIds) AS num_courses, COUNT(*) AS num_students
        |FROM students
        |GROUP BY size(courseIds)
        |ORDER BY num_courses""".stripMargin)

    // ---------- 4.3 ----------
    println("\n\n########## 4.3 Aggregations and window functions ##########")

    run("4.3a By birth year",
      """SELECT year(dateOfBirth) AS birth_year,
        |       COUNT(*)           AS num_students,
        |       ROUND(AVG(gpa), 2) AS avg_gpa
        |FROM students
        |GROUP BY year(dateOfBirth)
        |ORDER BY birth_year""".stripMargin)

    run("4.3b Top 3 students by GPA per course (DENSE_RANK)",
      """SELECT course_id, rnk, student_id, firstName, lastName, gpa
        |FROM (
        |  SELECT course_id, student_id, firstName, lastName, gpa,
        |         DENSE_RANK() OVER (PARTITION BY course_id ORDER BY gpa DESC) AS rnk
        |  FROM enrollments
        |) t
        |WHERE rnk <= 3
        |ORDER BY course_id, rnk, lastName, firstName""".stripMargin)

    run("4.3c Students sharing the same first and last name",
      """SELECT a.firstName, a.lastName,
        |       a._id AS id1, to_date(a.dateOfBirth) AS dob1,
        |       b._id AS id2, to_date(b.dateOfBirth) AS dob2
        |FROM students a
        |JOIN students b
        |  ON a.firstName = b.firstName AND a.lastName = b.lastName AND a._id < b._id
        |ORDER BY a.lastName, a.firstName""".stripMargin)

    run("4.3d GPA above the average of EVERY course the student is enrolled in",
      """WITH course_avg AS (
        |  SELECT course_id, AVG(gpa) AS avg_gpa
        |  FROM enrollments
        |  GROUP BY course_id
        |)
        |SELECT e.student_id AS id, e.firstName, e.lastName, e.gpa
        |FROM enrollments e
        |JOIN course_avg c ON e.course_id = c.course_id
        |GROUP BY e.student_id, e.firstName, e.lastName, e.gpa
        |HAVING MIN(e.gpa - c.avg_gpa) > 0
        |ORDER BY id""".stripMargin)

    // ---------- 4.4 ----------
    println("\n\n########## 4.4 Writing results back to MongoDB ##########")

    courseSummary.write.format("mongodb")
      .mode("overwrite")
      .option("connection.uri", Config.uri)
      .option("database", "university")
      .option("collection", "course_stats")
      .save()
    println("course_stats written.")

    val back = spark.read.format("mongodb")
      .option("connection.uri", Config.uri)
      .option("database", "university")
      .option("collection", "course_stats")
      .load()
    println("\n=== Read back from university.course_stats ===")
    back.printSchema()
    back.orderBy(back("num_students").desc, back("course_id")).show(truncate = false)
    println(s"Rows in course_stats: ${back.count()}")

    spark.stop()
  }
}