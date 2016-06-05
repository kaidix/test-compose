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

import scalaj.http.Http
import scalaj.http.HttpOptions

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
      if(prediction>0){
            val result = Http("https://stark-everglades-26570.herokuapp.com/update").postData("""{"tickersymbol":"%s","sfid":"%s"}""".format(point(0), point(1)))
                          .header("Content-Type", "application/json")
                          .header("Charset", "UTF-8")
                          .option(HttpOptions.readTimeout(10000)).asString
            (point(0), point(1), prediction, result)
        }
    else{
        val result = "a"
      (point(0), point(1), prediction, result)
    }
    }


    // labelAndPreds.foreach{_.foreach{pp => handleOutput(pp._1,pp._2,pp._3)}}
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
