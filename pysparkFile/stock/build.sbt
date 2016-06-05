name := "Simple Project"

version := "1.0"

scalaVersion := "2.10.5"


val sparkVersion = "1.6.1"

libraryDependencies += "org.apache.spark" %% "spark-core" % "1.6.1" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "1.6.1" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-mllib" % "1.6.1" % "provided"
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",
  ("org.apache.spark" %% "spark-streaming-kafka" % sparkVersion) exclude ("org.spark-project.spark", "unused")
)
libraryDependencies +=  "org.scalaj" %% "scalaj-http" % "2.3.0" 

assemblyJarName in assembly := "simple-project_2.10-1.0.jar"