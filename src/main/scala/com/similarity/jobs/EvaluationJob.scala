package com.similarity.jobs

import com.similarity.lsh.BandBuilder
import com.similarity.minhash.MinHasher
import com.similarity.store.ClickHouseStore
import org.apache.spark.sql.SparkSession

object EvaluationJob {
  val MIN_TRACKS = 20

  def main(args: Array[String]): Unit = {
    val threshold = if (args.length > 0) args(0).toDouble else 0.5

    val spark = SparkSession
      .builder()
      .appName("Evaluation")
      .master("local[*]")
      .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    // 1. Ground truth
    val groundTruth = spark.read
      .option("header", "true")
      .csv(s"data/ground_truth_${threshold}")
      .collect()
      .flatMap(r => {
        try {
          val a = r.getAs[String]("pid_a").toLong
          val b = r.getAs[String]("pid_b").toLong
          Some(if (a < b) (a, b) else (b, a))
        } catch { case _: Exception => None } // ignore lignes corrompues
      })
      .toSet

    println(
      s"→ Vérité terrain : ${groundTruth.size} paires avec Jaccard >= $threshold"
    )

    // 2. Charge et cache les playlists + construit une map pid → tracks
    val playlists = spark.read
      .option("multiline", "false")
      .json("data/slices/")
      .select("pid", "tracks")
      .filter(r =>
        r.getAs[Any]("tracks") match {
          case arr: scala.collection.mutable.WrappedArray[_] =>
            arr.size >= MIN_TRACKS
          case _ => false
        }
      )
      .cache()

    val total = playlists.count()
    println(s"→ $total playlists chargées")

    // Map pid → Set[tracks] pour vérification Jaccard exacte
    val trackMap: Map[Long, Set[String]] = playlists
      .collect()
      .map { row =>
        val pid = row.getAs[Long]("pid")
        val tracks = row
          .getAs[scala.collection.mutable.WrappedArray[String]]("tracks")
          .toSet
        pid -> tracks
      }
      .toMap

    // 3. LSH en parallèle via Spark RDD
    println("→ Recherche LSH pour toutes les playlists...")

    val broadcastTrackMap = spark.sparkContext.broadcast(trackMap)

    val lshPairs = playlists.rdd
      .flatMap { row =>
        val pid = row.getAs[Long]("pid")
        val tracks = row
          .getAs[scala.collection.mutable.WrappedArray[String]]("tracks")
          .toSeq
        val sig = MinHasher.signature(tracks)
        val buckets = BandBuilder.toBuckets(sig, pid)

        // Requête ClickHouse locale au worker
        val candidates =
          ClickHouseStore.candidatesBatch(buckets).filter(_ != pid)

        // ✅ Vérification Jaccard exacte en post-filtre
        val localTrackMap = broadcastTrackMap.value
        val tracksA = tracks.toSet
        candidates.flatMap { cPid =>
          localTrackMap.get(cPid).flatMap { tracksB =>
            val inter = tracksA.intersect(tracksB).size.toDouble
            val union = tracksA.union(tracksB).size.toDouble
            val j = if (union == 0.0) 0.0 else inter / union
            if (j >= threshold) {
              val a = math.min(pid, cPid)
              val b = math.max(pid, cPid)
              Some((a, b))
            } else None
          }
        }
      }
      .distinct()
      .collect()
      .toSet

    println(
      s"→ LSH a retourné ${lshPairs.size} paires candidates (après vérification Jaccard)"
    )

    // 4. Métriques
    val truePositives = lshPairs.intersect(groundTruth).size
    val falsePositives = lshPairs.diff(groundTruth).size
    val falseNegatives = groundTruth.diff(lshPairs).size

    val precision =
      if (lshPairs.isEmpty) 0.0 else truePositives.toDouble / lshPairs.size
    val recall =
      if (groundTruth.isEmpty) 0.0
      else truePositives.toDouble / groundTruth.size
    val f1 =
      if (precision + recall == 0.0) 0.0
      else 2 * precision * recall / (precision + recall)

    println()
    println("=" * 40)
    println(s"  Seuil Jaccard    : $threshold")
    println(
      s"  Bandes × Lignes  : ${BandBuilder.NUM_BANDS} × ${BandBuilder.ROWS_PER_BAND}"
    )
    println(
      s"  s*               : ${math.pow(1.0 / BandBuilder.NUM_BANDS, 1.0 / BandBuilder.ROWS_PER_BAND)}"
    )
    println(s"  Vrais positifs   : $truePositives")
    println(s"  Faux positifs    : $falsePositives")
    println(s"  Faux négatifs    : $falseNegatives")
    println(f"  Precision        : ${precision * 100}%.1f%%")
    println(f"  Recall           : ${recall * 100}%.1f%%")
    println(f"  F1-Score         : ${f1 * 100}%.1f%%")
    println("=" * 40)

    spark.stop()
  }
}
