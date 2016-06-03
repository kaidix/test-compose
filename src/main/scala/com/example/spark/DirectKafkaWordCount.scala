package com.example.spark

import kafka.serializer.StringDecoder
import org.apache.spark.{TaskContext, SparkConf}
import org.apache.spark.streaming.kafka.{OffsetRange, HasOffsetRanges, KafkaUtils}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.mllib.linalg.Vectors

import org.apache.spark.mllib.tree.DecisionTree
import org.apache.spark.mllib.tree.model.DecisionTreeModel
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.regression.LabeledPoint

object DirectKafkaWordCount {
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      System.err.println(s"""
        |Usage: DirectKafkaWordCount <brokers> <topics>
        |  <brokers> is a list of one or more Kafka brokers
        |  <topics> is a list of one or more kafka topics to consume from
        |
        """.stripMargin)
      System.exit(1)
    }

    val Array(brokers, topics) = args

    // Create context with 10 second batch interval
    val sparkConf = new SparkConf().setAppName("DirectKafkaWordCount")
    val ssc = new StreamingContext(sparkConf, Seconds(10))

    // Create direct kafka stream with brokers and topics
    val topicsSet = topics.split(",").toSet
    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)
    val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](
      ssc, kafkaParams, topicsSet)

    // Get the lines, split them into words, count the words and print
    val lines = messages.map { line =>
      val parts = line._2.split(' ')
      parts
    }

    val model = DecisionTreeModel.load(ssc.sparkContext, "/decisiontree")
    val labelAndPreds = lines.map { point =>
      val prediction = model.predict(LabeledPoint(0.0, Vectors.dense(point.slice(1,2).map(x => x.toDouble))).features)
      (point(0), prediction)
    }
    labelAndPreds.map {point => 
        if(point._2>0){
            print("Captured abnormal heartbeat rate:" + point._2 + "Device: " + point._1)
        }
        else{
            print("Normal data:")
            print(point)
        }
        point
    }
    labelAndPreds.print()
    // val prediction = model.predict(words.map(lambda x: x.features))

    // val wordCounts = words.map(x => (x, 1L)).reduceByKey(_ + _)
    // wordCounts.print()

    // Start the computation
    ssc.start()
    ssc.awaitTermination()
  }
  def parseInput(line:String){
      val parts = line.split(' ')
  }
}
