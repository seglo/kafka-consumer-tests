package com.seglo

import java.time.Duration
import java.util.Properties

import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.collection.JavaConverters._

object RawKafkaClient extends App {
  val conf = ConfigFactory.load()

  val kafkaHost = conf.getString("kafkaHost")
  val topic = conf.getString("topic")
  val consumerGroup = conf.getString("consumerGroup")
  val clientId = conf.getString("clientId")
  val consumeMessagePerSecond = conf.getInt("consumeMessagePerSecond")
  val commitGroupSize = conf.getInt("commitGroupSize")
  val partitions = 10

  val consumerProps: Properties = {
    val p = new Properties()
    p.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup)
    p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHost)
    p
  }

  val consumer = new KafkaConsumer(consumerProps, new ByteArrayDeserializer, new ByteArrayDeserializer)
  consumer.subscribe(List(topic).asJava)

  val pollF = () => {
    Thread.sleep(1000) // 1s

    val resumedPartitionNum = scala.util.Random.nextInt(partitions)
    val pausedPartitionsNums = (0 until partitions).filter(_ != resumedPartitionNum).toSet
    val resumedPartition = Set(new TopicPartition(topic, resumedPartitionNum)).asJava
    val pausedPartitions = pausedPartitionsNums.map(p => new TopicPartition(topic, p)).asJava

    println(s"Partition to resume: $resumedPartitionNum")
    println(s"Partitions to pause: $pausedPartitionsNums")

    consumer.pause(pausedPartitions)
    consumer.resume(resumedPartition)

    consumer.poll(Duration.ofMillis(1000))
  }

  Stream.continually(consumer.poll(Duration.ofMillis(1000)))
    .takeWhile(_ ne null)
    .filterNot(_.isEmpty)
    .foreach { records =>
      println(s"Polled ${records.count()} records from partitions ${records.partitions()}.")
    }
}
