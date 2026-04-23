package com.similarity.jobs

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col
import java.io.{File, PrintWriter}

object GroundTruthJob {

  def main(args: Array[String]): Unit = {
    val threshold  = if (args.length > 0) args(0).toDouble else 0.5
    val minTracks  = 20

    val spark = SparkSession.builder()
      .appName("GroundTruth")
      .master("local[*]")
      .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    import spark.implicits._

    val raw = spark.read
      .option("multiline", "false")
      .json("data/playlists.ndjson")

    // Filtre les petites playlists
    val playlists = raw
      .filter(r => r.getAs[Any]("tracks") match {
        case arr: scala.collection.mutable.WrappedArray[_] => arr.size >= minTracks
        case _ => false
      })
      .select("pid", "tracks")
      .cache()

    val total = playlists.count()
    println(s"→ ${total} playlists avec >= $minTracks tracks")

    // Cross join avec alias pour éviter ambiguïté
    val a = playlists.alias("a")
    val b = playlists.alias("b")

    import org.apache.spark.sql.functions._

    val pairs = a.join(b,
        col("a.pid") < col("b.pid")  // évite les doublons (i,j) et (j,i)
      )
      .select(
        col("a.pid").as("pid_a"),
        col("b.pid").as("pid_b"),
        col("a.tracks").as("tracks_a"),
        col("b.tracks").as("tracks_b")
      )

    // UDF Jaccard
    val jaccardUDF = udf((ta: Seq[String], tb: Seq[String]) => {
      val setA = ta.toSet
      val setB = tb.toSet
      val inter = setA.intersect(setB).size.toDouble
      val union = setA.union(setB).size.toDouble
      if (union == 0.0) 0.0 else inter / union
    })

    val result = pairs
      .withColumn("jaccard", jaccardUDF(col("tracks_a"), col("tracks_b")))
      .filter(col("jaccard") >= threshold)
      .select("pid_a", "pid_b", "jaccard")
      .cache()

    println(s"→ Calcul Jaccard sur toutes les paires (seuil=$threshold)...")

    result
      .write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"data/ground_truth_${threshold}")

    val count = result.count()
    println(s"→ $count paires avec Jaccard >= $threshold sauvegardées")

    spark.stop()
  }
}