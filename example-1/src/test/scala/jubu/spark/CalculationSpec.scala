package jubu.spark

import jubu.spark.Calculation._
import munit.FunSuite
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.spark.internal.Logging
import org.testcontainers.kafka.KafkaContainer

import java.io.File
import java.time.Duration
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.reflect.io.Directory
import scala.util.Try

class CalculationSpec extends FunSuite with Logging {

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val kafkaContainer = new KafkaContainer("apache/kafka:4.0.0")
    .withEnv("KAFKA_NUM_PARTITIONS", "1")
    .withEnv("AUTO_CREATE_TOPICS_ENABLE", "true")

  private def getKafkaProducer = {
    val properties = new java.util.Properties()
    properties.put("bootstrap.servers", kafkaContainer.getBootstrapServers)
    properties.put("key.serializer", classOf[StringSerializer])
    properties.put("value.serializer", classOf[StringSerializer])

    new KafkaProducer[String, String](properties)
  }

  private def getKafkaConsumer = {
    // Configuration
    val props = new java.util.Properties()
    import ConsumerConfig._
    props.put(BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers)
    props.put(GROUP_ID_CONFIG, "scala-consumer-group")
    props.put(KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
    props.put(VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
    props.put(AUTO_OFFSET_RESET_CONFIG, "earliest")
    val c = new KafkaConsumer[String, String](props)
    c.subscribe(java.util.Collections.singletonList("output-topic"))
    c
  }

  // Create a SparkSession
  private def getSpark = org.apache.spark.sql.SparkSession
    .builder()
    .appName("CalculationSpec")
    .master("local[3]")
    .config("spark.streaming.stopGracefullyOnShutdown", "true")
    .config("spark.sql.streaming.forceDeleteTempCheckpointLocation", "true")
    .getOrCreate()

  override def beforeAll(): Unit = {
    kafkaContainer.start()
  }

  override def afterAll(): Unit = {
    kafkaContainer.stop()
    Try(new Directory(new File("/tmp/checkpoint")).deleteRecursively())
  }

  val topic = "test-topic"

  test("calculation should process data from Kafka") {

    val msg        = "Ahoj, ako sa mas"
    val iterations = 100

    // create producer and consumer and spark session
    val kafkaProducer = getKafkaProducer
    val kafkaConsumer = getKafkaConsumer
    val spark         = getSpark

    // buil streaming query
    val query = build(spark, kafkaContainer.getBootstrapServers, topic).start()

    try {

      // async send an events to kafka topic
      Future {
        1 to iterations foreach { i =>
          kafkaProducer.send(new ProducerRecord[String, String](topic, i.toString, msg)).get()
          Thread.sleep(50)
        }
        kafkaProducer.close()
      }

      val res = {

        // consume events from kafka to validate
        @scala.annotation.tailrec
        def rec(time: Long, acc: Map[String, Int] = Map.empty): Boolean = {
          log.debug(s"acc: $acc")
          if (System.currentTimeMillis() - time > 15000) {
            log.error("Timeout occurred ...")
            false
          } else if (acc.values.sum != iterations * splitter(msg).size) {
            val events = kafkaConsumer.poll(Duration.ofMillis(1000)).asScala.map(r => (r.key(), r.value().toInt))
            rec(time, acc ++ events)
          } else {
            true
          }
        }

        rec(System.currentTimeMillis())
      }

      assert(res, "The streaming query completed) successfully.")

    } finally {
      // close all resources
      Try(kafkaConsumer.close())
      Try(query.stop())
      Try(query.awaitTermination())
      Try(spark.close())
    }
  }
}
