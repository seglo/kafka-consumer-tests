package com.seglo.latencymetrics
import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit

import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerInterceptor, ConsumerRecords, OffsetAndMetadata}
import org.apache.kafka.common.{MetricNameTemplate, TopicPartition}
import org.apache.kafka.common.metrics._
import org.apache.kafka.common.metrics.stats.{Avg, Max, Value}
import org.apache.kafka.common.utils.Time

import collection.JavaConverters._

class LatencyMetricsInterceptor[K, V] extends ConsumerInterceptor[K, V] {
  private val JMX_PREFIX = "lightbend.kafka.consumer"
  private val GROUP_PREFIX = "consumer"
  private val GROUP_NAME = GROUP_PREFIX + "-fetch-manager-metrics"
  private val CLIENT_ID_METRIC_TAG = "client-id"
  private val CLIENT_TAGS = Set("client-id")
  private val PARTITION_TAGS = CLIENT_TAGS ++ Set("topic", "partition")

  private var recordsLatencySensor: Sensor = _

  private val recordsLatencyMax = new MetricNameTemplate(
    "records-latency-max",
    GROUP_NAME,
    "The maximum latency of any partition in this sample window for this client",
    CLIENT_TAGS.asJava
  )

  private val partitionRecordsLatency = new MetricNameTemplate(
    "records-latency",
    GROUP_NAME,
    "The latest latency of the partition",
    PARTITION_TAGS.asJava
  )
  private val partitionRecordsLatencyMax = new MetricNameTemplate(
    "records-latency-max",
    GROUP_NAME,
    "The max latency of the partition",
    PARTITION_TAGS.asJava
  )
  private val partitionRecordsLatencyAvg = new MetricNameTemplate(
      "records-latency-avg",
    GROUP_NAME,
    "The average latency of the partition",
    PARTITION_TAGS.asJava
  )

  private var metrics: Metrics = _

  override def onConsume(records: ConsumerRecords[K, V]): ConsumerRecords[K, V] = {
    for {
      tp <- records.partitions().asScala
      lastRecord <- records.records(tp).asScala.lastOption
    } recordPartitionLatency(tp, lastRecord.timestamp())

    records
  }

  override def onCommit(offsets: util.Map[TopicPartition, OffsetAndMetadata]): Unit = ()

  override def close(): Unit = {
    metrics.close()
  }

  override def configure(configs: util.Map[String, _]): Unit = {
    metrics = buildMetrics(configs, Time.SYSTEM)
    recordsLatencySensor = metrics.sensor("records-latency");
    recordsLatencySensor.add(metrics.metricInstance(recordsLatencyMax), new Max())
  }

  private def recordPartitionLatency(tp: TopicPartition, timestamp: Long): Unit = {
    val latency = System.currentTimeMillis - timestamp

    if (latency < 0) return

    recordsLatencySensor.record(latency)

    val name = partitionLatencyMetricName(tp)
    var partitionRecordsLatencySensor = metrics.getSensor(name)
    if (partitionRecordsLatencySensor == null) {
      val metricTags = Map(
        "topic" -> tp.topic.replace('.', '_'), // for compatibility with internal Kafka partition metrics
        "partition" -> tp.partition.toString
      ).asJava

      partitionRecordsLatencySensor = metrics.sensor(name)

      partitionRecordsLatencySensor.add(metrics.metricInstance(partitionRecordsLatency, metricTags), new Value)
      partitionRecordsLatencySensor.add(metrics.metricInstance(partitionRecordsLatencyMax, metricTags), new Max)
      partitionRecordsLatencySensor.add(metrics.metricInstance(partitionRecordsLatencyAvg, metricTags), new Avg)
    }

    partitionRecordsLatencySensor.record(latency)
  }

  private def partitionLatencyMetricName(tp: TopicPartition) = tp + ".records-latency"

  private def buildMetrics(configs: util.Map[String, _],
                           time: Time) = {
    val config = new ConsumerConfig(configs.asScala.mapValues(_.asInstanceOf[AnyRef]).asJava)
    val clientId = config.getString(ConsumerConfig.CLIENT_ID_CONFIG)
    val metricsTags = Collections.singletonMap(CLIENT_ID_METRIC_TAG, clientId)
    val metricConfig = new MetricConfig()
      .samples(config.getInt(ConsumerConfig.METRICS_NUM_SAMPLES_CONFIG))
      .timeWindow(config.getLong(ConsumerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG), TimeUnit.MILLISECONDS)
      .recordLevel(Sensor.RecordingLevel.forName(config.getString(ConsumerConfig.METRICS_RECORDING_LEVEL_CONFIG)))
      .tags(metricsTags)
    val reporters = List[MetricsReporter](new JmxReporter(JMX_PREFIX)).asJava
    new Metrics(metricConfig, reporters, time)
  }
}
