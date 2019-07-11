#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
kubectl apply -f $DIR/simple-topic-KAFKA-240-SNAPSHOT.yaml -n lightbend
kubectl apply -f $DIR/kafka-consumer-tests-KAFKA-240-SNAPSHOT.yaml -n seglo
kubectl apply -f $DIR/producer-KAFKA-240-SNAPSHOT.yaml -n seglo
