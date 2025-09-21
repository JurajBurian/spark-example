package jubu.spark

import org.apache.spark.sql.streaming.DataStreamWriter
import org.apache.spark.sql.{DataFrame, Dataset, Row, SparkSession}

object Calculation {

  def source(spark: SparkSession, brokers: String, topics: String): Dataset[String] = {
    import spark.implicits._
    val df: DataFrame = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("subscribe", topics)
      .option("startingOffsets", "earliest")
      .load()

    df.as[String]
  }

  def aggregation(spark: SparkSession, dataset: Dataset[String]): DataFrame = {
    import spark.implicits._
    dataset.flatMap(splitter).groupBy("value").count()
  }

  def sink(dataset: DataFrame, brokers: String): DataStreamWriter[Row] = {
    // dataset.writeStream.outputMode("complete").format("console")
    dataset
      .selectExpr("CAST(value AS STRING) AS key", "CAST(count AS STRING) AS value")
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("topic", "output-topic")
      .option("checkpointLocation", "/tmp/checkpoint")
      .outputMode("complete")
  }

  def build(spark: SparkSession, brokers: String, topics: String): DataStreamWriter[Row] = {
    val src    = source(spark, brokers, topics)
    val result = aggregation(spark, src)
    sink(result, brokers)
  }
}
