from pyspark.mllib.linalg import Vectors
from pyspark.ml.classification import LogisticRegression
from pyspark.ml.param import Param, Params

from pyspark.sql import SQLContext
from pyspark.sql.types import *

sqlContext = SQLContext(sc)

schema = StructType ([ \
StructField("Open", DoubleType(), True), \
StructField("High", DoubleType(), True), \
StructField("Low", DoubleType(), True), \
StructField("Close", DoubleType(), True), \
StructField("Volume", DoubleType(), True), \
StructField("Adj Close", DoubleType(), True), \
StructField("Favorable", StringType(), True)])


training = sqlContext.read \
.format('com.databricks.spark.csv') \
.option("header", "true") \
.load('/Users/dvelisetti/Downloads/yahooCRM4.csv', schema=schema)

test = sqlContext.read \
.format('com.databricks.spark.csv') \
.option("header", "true") \
.load('/Users/dvelisetti/Downloads/yahooCRM5.csv', schema=schema)

from pyspark.ml.feature import StringIndexer
from pyspark.ml.feature import VectorAssembler

numeric_cols = ["Open", "High", "Low", "Close", "Volume", "Adj Close"]
label_indexer = StringIndexer(inputCol = 'Favorable', outputCol = 'label')

assembler = VectorAssembler(
inputCols = numeric_cols,
outputCol = 'features'
)

from pyspark.ml.feature import StringIndexer
from pyspark.ml.feature import VectorAssembler

numeric_cols = ["Open", "High", "Low", "Close", "Volume", "Adj Close"]
label_indexer = StringIndexer(inputCol = 'Favorable', outputCol = 'label')

assembler = VectorAssembler(
inputCols = numeric_cols,
outputCol = 'features'
)

lr = LogisticRegression(maxIter=10, regParam=0.01)

from pyspark.ml import Pipeline
pipeline = Pipeline(stages=[label_indexer, assembler, lr])

model1 = pipeline.fit(training)

paramMap = {lr.maxIter: 20}
paramMap[lr.maxIter] = 30 # Specify 1 Param, overwriting the original maxIter.
paramMap.update({lr.regParam: 0.1, lr.threshold: 0.55}) # Specify multiple Params.

paramMap2 = {lr.probabilityCol: "myProbability"} # Change output column name
paramMapCombined = paramMap.copy()
paramMapCombined.update(paramMap2)

model2 = pipeline.fit(training, paramMapCombined)

prediction = model2.transform(test)
selected = prediction.select("features", "label", "myProbability", "prediction")
for row in selected.collect():
    print row