package jubu.spark
import org.apache.spark.sql._
object Main {

  def main(args: Array[String]): Unit = {
    import Calculation._
    if (args.length < 2) {
      System.err.println(s"""
           |Usage: Main <brokers> <groupId> <topics>
           |  <brokers> is a list of one or more Kafka brokers
           |  <topics> is a list of one or more kafka topics to consume from""".stripMargin)
      System.exit(1)
    }
    val Array(brokers, topics) = args
    val spark                  = SparkSession
      .builder()
      .appName("Spark 4.0 Example1")
      .config("spark.master", "local[*]")
      .getOrCreate()
    val query = build(spark, brokers, topics).start()
    query.awaitTermination()
  }
}
