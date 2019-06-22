package com.seglo

import java.time.Duration
import java.util.Properties

import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.collection.JavaConverters._

object RawKafkaClient extends App {
  val conf = ConfigFactory.load()

  val kafkaHost = conf.getString("kafkaHost")
  val topic = conf.getString("topic")
  val partitions = conf.getInt("partitions")
  val consumerGroup = conf.getString("consumerGroup")
  val clientId = conf.getString("clientId")
  val maxPollRecords = conf.getInt("maxPollRecords")
  val pausePartitions = conf.getBoolean("pausePartitions")

  val allPartitions = (0 until partitions).toSet
  val consumerProps: Properties = {
    val p = new Properties()
    p.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId)
    p.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup)
    p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHost)
    p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords.toString)
    p
  }

  val consumer = new KafkaConsumer(consumerProps, new ByteArrayDeserializer, new ByteArrayDeserializer)
  consumer.subscribe(List(topic).asJava)

  def pollF(): ConsumerRecords[Array[Byte], Array[Byte]] = {
    val resumedPartitionNum = scala.util.Random.nextInt(partitions)
    val pausedPartitionsNums = allPartitions.filter(_ != resumedPartitionNum)
    val pausedPartitions = pausedPartitionsNums.map(p => new TopicPartition(topic, p)).asJava

    val assignment = consumer.assignment().asScala.map(_.partition()).toSet
    val pause = pausePartitions && assignment == allPartitions

    if (pause) {
      println(s"Partitions to leave resumed: $resumedPartitionNum")
      println(s"Partitions to pause: $pausedPartitionsNums")

      consumer.pause(pausedPartitions)
    }

    val records = consumer.poll(Duration.ofMillis(1000))

    if (pause)
      consumer.resume(pausedPartitions)

    records
  }

  Stream.continually(pollF())
    .takeWhile(_ ne null)
    .foreach { records =>
      println(s"Polled ${records.count()} records from partitions ${records.partitions()}.")
    }
}
