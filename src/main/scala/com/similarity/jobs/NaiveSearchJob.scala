package com.similarity.jobs

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col

object NaiveSearchJob {

  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Usage: NaiveSearchJob <playlist_id>")

    val targetPid = args(0).toLong

    val spark = SparkSession
      .builder()
      .appName("NaiveSearch")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    val playlists = spark.read
      .option("multiline", "false")
      .json("data/playlists.ndjson")
      .cache()

    val targetRows = playlists.filter(col("pid") === targetPid).take(1)

    if (targetRows.isEmpty) {
      System.err.println(s"Playlist introuvable: $targetPid")
      spark.stop()
      sys.exit(1)
    }

    val targetTracks = targetRows(0).getAs[Seq[String]]("tracks").toSet
    val totalPlaylists = playlists.count()

    println(s"→ Comparaison exhaustive contre $totalPlaylists playlists...")

    val start = System.currentTimeMillis()

    // Compare TOUTES les playlists une par une — O(n²)
    val results = playlists
      .filter(col("pid") =!= targetPid)
      .collect()
      .map { row =>
        val pid = row.getAs[Long]("pid")
        val tracks = row.getAs[Seq[String]]("tracks").toSet
        val score = jaccard(targetTracks, tracks)
        (pid, score)
      }
      .filter { case (_, score) => score > 0.0 }
      .sortBy { case (_, score) => -score }
      .take(10)

    val elapsed = System.currentTimeMillis() - start

    println(s"→ Temps écoulé : ${elapsed}ms pour $totalPlaylists playlists")
    println(
      s"→ Extrapolation à 1M playlists : ${elapsed * 200}ms (≈${(elapsed * 200) / 1000}s)"
    )
    println()
    println(f"${"playlist_id"}%-15s ${"jaccard"}%-10s")
    println("-" * 28)
    results.foreach { case (pid, score) =>
      println(f"$pid%-15d $score%-10.4f")
    }

    spark.stop()
  }

  private def jaccard(a: Set[String], b: Set[String]): Double = {
    val intersection = a.intersect(b).size.toDouble
    val union = a.union(b).size.toDouble
    if (union == 0.0) 0.0 else intersection / union
  }
}
