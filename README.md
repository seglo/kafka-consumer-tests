# kafka-consumer-tests

A simple project used to recreate various Kafka Consumer scenarios.

## Creating a producer

Use `kafka-producer-perf-test` from to produce data.

i.e.)

```
# produce 100 msg/s off messages 100 bytes in size.  
$ sudo docker run -it strimzi/kafka producer --command -- /opt/kafka/bin/kafka-producer-perf-test.sh \
--topic simple-topic \
--num-records 1000000 \
--record-size 100 \
--throughput 1000 \
--producer-props bootstrap.servers=pipelines-strimzi-kafka-bootstrap.lightbend.svc.cluster.local:9092

501 records sent, 100.2 records/sec (0.01 MB/sec), 4.1 ms avg latency, 284.0 max latency.
502 records sent, 100.3 records/sec (0.01 MB/sec), 1.5 ms avg latency, 7.0 max latency.
500 records sent, 100.0 records/sec (0.01 MB/sec), 2.1 ms avg latency, 44.0 max latency.
501 records sent, 100.0 records/sec (0.01 MB/sec), 2.2 ms avg latency, 16.0 max latency.
501 records sent, 100.0 records/sec (0.01 MB/sec), 2.7 ms avg latency, 21.0 max latency.
501 records sent, 100.2 records/sec (0.01 MB/sec), 1.3 ms avg latency, 28.0 max latency.
...
```

## Exporting metrics

Add the following JVM flags to expose the JVM metrics.

```
-Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false
```

## Build Docker Image

Login to docker

```
docker login -u seglo
```

Push image

```
sbt docker:publish
```


## Run on K8s

```
# (Optional) Upgrade existing Strimzi install to watch a namespace
helm upgrade \
--reuse-values \
--set watchNamespaces="{seglo}" \
pipelines-strimzi lightbend-helm-charts/strimzi-kafka-operator

# (Optional) Create a Kafka CR
kubectl apply -f simple-strimzi.yaml -n seglo

# Create a topic CR
kubectl apply -f simple-topic.yaml

# Run a producer perf test to start populating the topic
kubectl apply -f producer.yaml -n seglo

# Deploy kafka slow consumer
kubectl apply -f kafka-slow-consumer.yaml -n seglo
```

Port-forward to Prometheus Endpoint to see if it's reporting metrics

```
kubectl port-forward kafka-slow-consumer-7d58ff95f-tsxf6 8080:8080 -n seglo
```

Cleanup

```
kubectl delete deployment producer -n seglo
kubectl delete deployment kafka-slow-consumer -n seglo
kubectl delete kafkatopic simple-topic -n lightbend
```