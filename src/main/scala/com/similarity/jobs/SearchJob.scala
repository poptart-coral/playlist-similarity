package com.similarity.jobs

import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.functions.{col, size}

import com.similarity.lsh.BandBuilder
import com.similarity.minhash.MinHasher
import com.similarity.model.ScoredPlaylist
import com.similarity.store.ClickHouseStore

object SearchJob {

  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Usage: SearchJob <playlist_id>")

    val targetPid = args(0).toLong

    val spark = SparkSession
      .builder()
      .appName("PlaylistSimilaritySearch")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    val playlists = spark.read
      .option("multiline", "false")
      .json("data/playlists.ndjson")
      .filter(size(col("tracks")) >= 20)
      .cache()

    val targetRows = playlists
      .filter(col("pid") === targetPid)
      .take(1)

    if (targetRows.isEmpty) {
      System.err.println(s"Playlist introuvable: $targetPid")
      spark.stop()
      sys.exit(1)
    }

    val targetRow = targetRows(0)
    val targetTracks = toTrackSet(targetRow)

    val start = System.currentTimeMillis()

    val targetSig = MinHasher.signature(targetTracks.toSeq)
    val targetBuckets = BandBuilder.toBuckets(targetSig, targetPid)

    val candidateIds = ClickHouseStore
      .candidatesBatch(targetBuckets)
      .filter(_ != targetPid)

    val elapsed = System.currentTimeMillis() - start
    println(s"→ Temps LSH : ${elapsed}ms")

    if (candidateIds.isEmpty) {
      println(s"Aucun candidat trouvé pour la playlist $targetPid")
      spark.stop()
      sys.exit(0)
    }

    import spark.implicits._
    val candidateIdDs = candidateIds.toSeq.toDS()

    val candidateRows = playlists
      .join(
        candidateIdDs.toDF("candidate_pid"),
        playlists("pid") === col("candidate_pid")
      )
      .select("pid", "tracks")
      .collect()

    val ranked = candidateRows
      .map { row =>
        val pid = row.getAs[Long]("pid")
        val tracks = row.getAs[Seq[String]]("tracks").toSet
        ScoredPlaylist(pid, jaccard(targetTracks, tracks))
      }
      .filter(_.score > 0.0)
      .sortBy(sp => -sp.score)

    println(s"Playlist cible: $targetPid")
    println(s"Nombre de candidats LSH: ${candidateIds.size}")
    println()
    println(f"${"playlist_id"}%-15s ${"jaccard"}%-10s")
    println("-" * 28)

    ranked.foreach { sp =>
      println(f"${sp.pid}%-15d ${sp.score}%-10.4f")
    }

    spark.stop()
  }

  private def toTrackSet(row: Row): Set[String] = {
    row.getAs[Seq[String]]("tracks").toSet
  }

  private def jaccard(a: Set[String], b: Set[String]): Double = {
    val intersection = a.intersect(b).size.toDouble
    val union = a.union(b).size.toDouble
    if (union == 0.0) 0.0 else intersection / union
  }
}
