import org.apache.spark.sql.SparkSession

object Part4 {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("HW2-SparkSQL")
      .master("local[*]")
      .config("spark.driver.bindAddress", "127.0.0.1")
      .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    // Option A: read the collection with the MongoDB Spark Connector
    val df = spark.read.format("mongodb")
      .option("connection.uri", Config.uri)
      .option("database", "university")
      .option("collection", "students")
      .load()

    println("=== Schema ===")
    df.printSchema()
    println(s"Rows loaded: ${df.count()}")

    df.createOrReplaceTempView("students")

    // 4.1a Students registered in course 103
    println("\n=== 4.1a Students in course 103 ===")
    val q1 = spark.sql(
      """SELECT _id AS id, firstName, lastName, gpa
        |FROM students
        |WHERE array_contains(courseIds, 103)
        |ORDER BY lastName, firstName""".stripMargin)
    q1.show(100, truncate = false)
    println(s"Matches: ${q1.count()}  (Part 2 gave 36)")

    // 4.1b Students with GPA > 3.5, sorted by GPA descending
    println("\n=== 4.1b Students with GPA > 3.5 ===")
    val q2 = spark.sql(
      """SELECT _id AS id, firstName, lastName, gpa
        |FROM students
        |WHERE gpa > 3.5
        |ORDER BY gpa DESC, lastName, firstName""".stripMargin)
    q2.show(100, truncate = false)
    println(s"Matches: ${q2.count()}  (Part 3 gave 49)")

    spark.stop()
  }
}