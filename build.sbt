import Dependencies._

ThisBuild / scalaVersion := "2.13.17"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "playlist-similarity",
    libraryDependencies += munit % Test,
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % "4.1.1",
      "com.google.guava" % "guava" % "33.5.0-jre",
      "io.circe" %% "circe-core" % "0.15.0-M1",
      "io.circe" %% "circe-parser" % "0.15.0-M1",
      "io.circe" %% "circe-generic" % "0.15.0-M1",
      "com.clickhouse" % "clickhouse-jdbc" % "0.9.8",
      "com.lihaoyi" %% "ujson" % "3.3.1"
    ),
    // javaOptions ++= Seq(
    //   "--add-opens=java.base/javax.security.auth=ALL-UNNAMED", // 
    //   "--add-opens=java.base/java.lang=ALL-UNNAMED", // 
    //   "-Djava.security.manager=allow"
    // ),
    // fork := true
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
