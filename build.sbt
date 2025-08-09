val v = new {
  val Scala          = "2.13.16"
  val Spark          = "4.0.0"
  val Munit          = "1.1.1"
  val Testcontainers = "1.21.3"
}

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := v.Scala
ThisBuild / scalacOptions ++=
  Seq("-encoding", "UTF-8", "-unchecked", "-feature", "-explaintypes")

val coreSparkLibs = Seq(
  "org.apache.spark" %% "spark-core" % v.Spark % Provided,
  "org.apache.spark" %% "spark-sql"  % v.Spark % Provided
)
val commonLibs = Seq(
  "org.scalameta" %% "munit" % v.Munit % Test
)

lazy val `spark-examples-root` = (project in file("."))
  .settings(publishLocal := {}, publish := {}, publishArtifact := false)
  .aggregate(`common`, `example-1`)

lazy val `common` = (project in file("common")).settings(
  libraryDependencies ++= commonLibs
)

lazy val `example-1` = (project in file("example-1"))
  .settings(
    assembly / mainClass := Some("com.jubu.spark.Main"),
    libraryDependencies ++= coreSparkLibs ++ commonLibs ++ Seq(
      "org.testcontainers" % "kafka"                      % v.Testcontainers % Test,
      "org.apache.spark"  %% "spark-sql-kafka-0-10"       % v.Spark,
      "org.apache.spark"  %% "spark-streaming-kafka-0-10" % v.Spark
    ),
    // Assembly settings
    assembly / assemblyJarName := "spark-example-1.jar",
    assembly / assemblyOption ~= { _.withIncludeScala(false) },
    run / fork    := true,
    Test / fork   := true,
    Compile / run := Defaults
      .runTask(Test / fullClasspath, Compile / run / mainClass, Compile / run / runner)
      .evaluated
  )
  .dependsOn(`common`)
