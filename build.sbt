name := "direct_kafka_word_count"

scalaVersion := "2.10.5"

val sparkVersion = "1.6.1"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",
  ("org.apache.spark" %% "spark-streaming-kafka" % sparkVersion) exclude ("org.spark-project.spark", "unused")
)
libraryDependencies +=  "org.scalaj" %% "scalaj-http" % "2.3.0" 

assemblyJarName in assembly := name.value + ".jar"
