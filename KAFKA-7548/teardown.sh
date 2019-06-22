#!/usr/bin/env bash
kubectl delete deployment kafka-consumer-tests-kafka-240-snapshot -n seglo
kubectl delete deployment producer-kafka-240-snapshot -n seglo
kubectl delete kafkatopic simple-topic-kafka-240-snapshot -n lightbend
kubectl delete deployment kafka-consumer-tests-kafka-221 -n seglo
kubectl delete deployment producer-kafka-221 -n seglo
kubectl delete kafkatopic simple-topic-kafka-221 -n lightbend