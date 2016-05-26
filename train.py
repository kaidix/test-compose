from pyspark import SparkContext, SparkConf 
import sys
import pandas as pd
import numpy as np
from pyspark.mllib.regression import LabeledPoint
from pyspark.mllib.tree import DecisionTree

if len(sys.argv) < 2:
	print 'requires second parameter as training file'
	sys.exit(0)

conf = SparkConf().setMaster("mesos://mesos_master:5050").setAppName("heart-disease-prediction-descision-tree")

sc   = SparkContext(conf=conf)



# https://archive.ics.uci.edu/ml/machine-learning-databases/heart-disease/processed.cleveland.data
path = sys.argv[1]
heartdf = pd.read_csv(path,header=None)

print "Original Dataset (Rows:Colums): "
print heartdf.shape
print 

print "Categories of Diagnosis of heart disease (angiographic disease status) that we are predicting"
print "-- Value 0: < 50% diameter narrowing"
print "-- Value 1: > 50% diameter narrowing "
print heartdf.ix[:,13].unique() #Column containing the Diagnosis of heart disease

newheartdf = pd.concat([heartdf.ix[:,13], heartdf.ix[:,0:12]],axis=1, join_axes=[heartdf.index])
newheartdf.replace('?', np.nan, inplace=True) # Replace ? values

print
print "After dropping rows with anyone empty value (Rows:Columns): "
ndf2 = newheartdf.dropna()
ndf2.to_csv(path+"heart-disease-cleaveland.txt",sep=",",index=False,header=None,na_rep=np.nan)
print ndf2.shape
print
ndf2.ix[:5,:]

points = sc.textFile(path+'heart-disease-cleaveland.txt') 

def parsePoint(line):
    """
    Parse a line of text into an MLlib LabeledPoint object.
    """
    values = [float(s) for s in line.strip().split(',')]
    if values[0] == -1: # Convert -1 labels to 0 for MLlib
        values[0] = 0
    elif values[0] > 0:
        values[0] = 1
    return LabeledPoint(values[0], values[1:])

parsed_data = points.map(parsePoint)

print 'After parsing, number of training lines: %s' % parsed_data.count()

parsed_data.take(5)

# Split the data into training and test sets (30% held out for testing)
(trainingData, testData) = parsed_data.randomSplit([0.7, 0.3])
# Train a DecisionTree model.
#  Empty categoricalFeaturesInfo indicates all features are continuous.
model = DecisionTree.trainClassifier(trainingData, numClasses=5, categoricalFeaturesInfo={}, impurity='gini', maxDepth=3, maxBins=32)

predictions = model.predict(testData.map(lambda x: x.features))
labelsAndPredictions = testData.map(lambda lp: lp.label).zip(predictions)
testErr = labelsAndPredictions.filter(lambda (v, p): v != p).count() / float(testData.count())
print('Test Error = ' + str(testErr))
print('Learned classification tree model:')
print(model.toDebugString())

# save the model to
model.save(sc, "decisiontree")

