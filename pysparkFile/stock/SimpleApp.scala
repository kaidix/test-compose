import java.io.File
import java.util.logging.Logger

import kafka.serializer.StringDecoder
import org.apache.spark.TaskContext
import org.apache.spark.streaming.kafka.{OffsetRange, HasOffsetRanges, KafkaUtils}
import org.apache.spark.streaming.{Seconds, StreamingContext}

import scalaj.http.Http
import scalaj.http.HttpOptions

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions._
import org.apache.spark.sql
import org.apache.spark.sql._
import org.apache.spark.mllib.classification.LogisticRegressionWithLBFGS
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.feature.StandardScalerModel
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.regression.LinearRegressionModel
import org.apache.spark.mllib.regression.LinearRegressionWithSGD
import org.apache.spark.mllib.regression.StreamingLinearRegressionWithSGD

import org.apache.commons.io.FileUtils

object SimpleApp {
  case class DailyStockInfo(date: Double, open: Double, high: Double, low: Double, close: Double)

  case class TransformedStockRow(date: Double, close: Double, ema: Double, ema_diff: Double, future_ema: Double, high_diff: Double, low_diff: Double)

  val conf = new SparkConf().setAppName("Simple Application")
  val sc = new SparkContext(conf)
  val sqlContext = new org.apache.spark.sql.SQLContext(sc)
  import sqlContext.implicits._


  val MODEL_PATH = "SparkModel"
  val SCALER_FILE = MODEL_PATH+"/scaler.obj"

  def main(args: Array[String]) {
      // sample code; not needed 
      /* 
      val logFile = "README.md" // Should be some file on your system
      val conf = new SparkConf().setAppName("Simple Application")
      val sc = new SparkContext(conf)
      val logData = sc.textFile(logFile, 2).cache()
      val numAs = logData.filter(line => line.contains("a")).count()
      val numBs = logData.filter(line => line.contains("b")).count()
      println("Lines with a: %s, Lines with b: %s".format(numAs, numBs))
      */

      if (args.length < 1) {
        println("Options: train | evaluate")
      } else if (args(0).equals("train")) {
        train();
      } else if (args(0).equals("evaluate")) {
        println(evaluate());
      } else if (args(0).equals("kafkaEvaluate")) {
        kafkaEvaluate(args);
      }

  }

  def train() {
    // initialization, read input
    val csv = sc.textFile("crm_stock_data_1yr.csv")
    //val header = csv.first()
    //val headerlessCsv = csv.filter(line => line.equals(header)).collect()
    //println(header)
    val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd")

    // process raw data
    //headerlessCsv.map(line => line.split(",")).map(s => myLogger.info(s(0)))
    val stockRows = csv.map(line => line.split(",")).map(s => DailyStockInfo(dateFormat.parse(s(0)).getTime().toDouble, s(1).toDouble, s(2).toDouble, s(3).toDouble, s(4).toDouble))
    val dsDf = stockRows.toDF()

    val closeValuesArr = dsDf.select("date", "close").orderBy("date").map(row => row(1)).map(x => x match { case i: java.lang.Number => i.doubleValue() }).collect()
    val lowPriceArr = dsDf.select("date", "low").orderBy("date").map(row => row(1)).map(x => x match { case i: java.lang.Number => i.doubleValue() }).collect()
    val highPriceArr = dsDf.select("date", "high").orderBy("date").map(row => row(1)).map(x => x match { case i: java.lang.Number => i.doubleValue() }).collect()

    // transform set of data to something useful
    val numRecords = closeValuesArr.length
    val emaArr = new Array[Double](numRecords)
    val emaDiffArr = new Array[Double](numRecords)
    val futureEmaArr = new Array[Double](numRecords)
    val highDiffArr = new Array[Double](numRecords)
    val lowDiffArr = new Array[Double](numRecords)

    val sma = dsDf.select(avg("close")).collect()(0).getDouble(0)
    val multiplier = 2.0 / (numRecords + 1)

    // Calculate high/low diffs, ema, future ema
    for (i <- 0 to numRecords - 1) {
      emaArr(i) = ema(closeValuesArr, sma, i)
      emaDiffArr(i) = closeValuesArr(i) - emaArr(i)
      highDiffArr(i) = highPriceArr(i) - closeValuesArr(i)
      lowDiffArr(i) = lowPriceArr(i) - closeValuesArr(i)

      if (i > 0) {
        futureEmaArr(i - 1) = emaArr(i)
      }
    }


    // turn arrays into a DF, w/o the first 15 nor last one (used for running prediction model on)
    val allDateArr = dsDf.select("date").orderBy("date").map(row => row(0)).map(x => x match { case i: java.lang.Number => i.doubleValue() }).collect()
    val slicedDateArr = allDateArr.slice(14, numRecords - 2)
    val slicedCloseArr = closeValuesArr.slice(14, numRecords - 2)
    val slicedEmaArr = emaArr.slice(14, numRecords - 2)
    val slicedEmaDiffArr = emaDiffArr.slice(14, numRecords - 2)
    val slicedFutureEmaArr = futureEmaArr.slice(14, numRecords - 2)
    val slicedHighDiffArr = highDiffArr.slice(14, numRecords - 2)
    val slicedLowDiffArr = lowDiffArr.slice(14, numRecords - 2)
    val slicedNumRecords = numRecords - 15 - 1

    // use this in the model to determine future val
    println("*************************** Copy this into the eval section ************************")
    val mostRecentVals = Vectors.dense(closeValuesArr(closeValuesArr.length - 1), anyToDouble(emaArr(numRecords - 1)))
    println(mostRecentVals)
    println("*************************** Copy this into the eval section ************************")

    val slicedRowsArr = List.tabulate(slicedNumRecords)(i => TransformedStockRow(slicedDateArr(i), slicedCloseArr(i), slicedEmaArr(i), slicedEmaDiffArr(i), slicedFutureEmaArr(i), slicedHighDiffArr(i), slicedLowDiffArr(i)))
    val slicedDf = slicedRowsArr.toDF()
    slicedDf.cache()

    // create LabeledPoints
    val dataset = slicedDf.select("date", "close", "ema", "future_ema").orderBy("date").map(row => LabeledPoint(anyToDouble(row(3)), Vectors.dense(anyToDouble(row(1)), anyToDouble(row(2))))).cache()

    // create model
    val numIterations = 2000
    val stepSize = 0.2
    val algorithm = new LinearRegressionWithSGD()

    algorithm.setIntercept(true)
    algorithm.optimizer
      .setNumIterations(numIterations)
      .setStepSize(stepSize)

    val scaler = new StandardScaler(withMean = true, withStd = true).fit(dataset.map(x => x.features))
    val scaledData = dataset
                    .map(x => 
                    LabeledPoint(x.label, 
                       scaler.transform(Vectors.dense(x.features.toArray)))).cache()
      
    val splits = scaledData.randomSplit(Array(0.8, 0.2), seed = 11L)
    val trainingData = splits(0).cache()
    val testingData = splits(1).cache()

    val model = algorithm.run(trainingData)    
    val modelFileDirectory = new File(MODEL_PATH)
     
    if (modelFileDirectory.exists())  FileUtils.deleteDirectory(modelFileDirectory)
    
     // save the trained model
     model.save(sc, MODEL_PATH)
     // save the scaler
     sc.parallelize(Seq(scaler), 1).saveAsObjectFile(SCALER_FILE)
  }

  /**
  * csv input format:
  * <close>,<ema>
  *
  */
  def evaluate() : Unit = {

    // val ssc = new StreamingContext(sc, Seconds(10))
    // load the model
    val model = LinearRegressionModel.load(sc, MODEL_PATH)
    
    // load the scaler
    val scaler = sc.objectFile[StandardScalerModel](SCALER_FILE).first()
    
    // load input data
    val todayCsv = sc.textFile("input.csv")

    /*
    val close = 82.550003
    val ema = 74.06524795767696
    val todayVector = Vectors.dense(82.550003, 74.06524795767696)
    */
    // use the CSV as RDD stream
    val todayVector = todayCsv.map(line => line.split(","))
    // val ema = todayVector(1)
    val labelAndPreds = todayVector.map { s =>
      val prediction = model.predict( scaler.transform(Vectors.dense(s(0).toDouble, s(1).toDouble)))
      if(prediction > s(1).toDouble){ // if stock price is going up
            val result = Http("https://stark-everglades-26570.herokuapp.com/update").postData("""{"tickersymbol":"%s 1","sfid":"%s"}""".format(s(0), "001B000000C8E2xIAF"))
                          .header("Content-Type", "application/json")
                          .header("Charset", "UTF-8")
                          .option(HttpOptions.readTimeout(10000)).asString
            (s(0), s(1), prediction, result) //also printing the result for debug purpose
        }
      else{
          val result = "no"
        (s(0), s(1), prediction, result) //also printing the result for debug purpose
      }
    }

    // output the RDD so it will be evaluated.
    labelAndPreds.foreach(println)
  }


  /**
  *
  * kafka input format:
  * <stock_symbol> <close> <ema>
  * use <space> as delimiter
  * e.g: CRM 11.0 11.0
  */
  def kafkaEvaluate(args: Array[String]){
    if (args.length < 3) {
      System.err.println(s"""
        |Usage: kafkaEvaluate <brokers> <topics>
        |  <brokers> is a list of one or more Kafka brokers
        |  <topics> is a list of one or more kafka topics to consume from
        |
        """.stripMargin)
      System.exit(1)
    }

    // Create context with 10 second batch interval
    val Array(brokers, topics) = args.slice(1,3)
    val ssc = new StreamingContext(sc, Seconds(10))

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
    // load the model
    val model = LinearRegressionModel.load(sc, MODEL_PATH)
    
    // load the scaler
    val scaler = sc.objectFile[StandardScalerModel](SCALER_FILE).first()
    // val ema = 74.06524795767696 //hardcode for now
    val labelAndPreds = lines.map { s =>
      val prediction = model.predict( scaler.transform(Vectors.dense(s(1).toDouble, s(2).toDouble)))
      if(prediction > s(1).toDouble){
            val result = Http("https://stark-everglades-26570.herokuapp.com/update").postData("""{"tickersymbol":"%s 1","sfid":"%s"}""".format(s(0), "001B000000C8E2xIAF"))
                          .header("Content-Type", "application/json")
                          .header("Charset", "UTF-8")
                          .option(HttpOptions.readTimeout(10000)).asString
            (s(0), s(1), prediction, result) //also printing the result for debug purpose
        }
      else{
          val result = "no"
        (s(0), s(1), prediction, result) //also printing the result for debug purpose
      }
    }
    // RDD must be output (or call foreach), otherwise it will not be evaluate
    labelAndPreds.print()

    ssc.start()
    ssc.awaitTermination()

  }

  /** Calculates EMA */
  def ema(closeValues : Array[Double], sma : Double, index : Int) : Double = {
    if (index == 0) {
        return sma
    }
    val prevEma = ema(closeValues, sma, index - 1)

    return (closeValues(index) - prevEma )* (2.0 / (closeValues.length + 1)) + prevEma
  }

  def anyToDouble: (Any) => Double = { case i: Int => i case f: Float => f case d: Double => d }
}
