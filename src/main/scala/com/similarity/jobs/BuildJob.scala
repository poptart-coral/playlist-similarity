package com.similarity.jobs

import org.apache.spark.sql.{SparkSession, Row}
import com.similarity.minhash.MinHasher
import com.similarity.lsh.BandBuilder
import com.similarity.store.ClickHouseStore

object BuildJob {
  def main(args: Array[String]): Unit = {

    // Prepare ClickHouse (table creation)
    ClickHouseStore.setup()
    ClickHouseStore.truncate()

    // Start Spark
    val spark = SparkSession
      .builder()
      .appName("PlaylistSimilarityBuild")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    // Read NDJSON 1 line = 1 playlist
    println("Lecture du NDJSON")
    val playlists = spark.read
      .option("multiline", "false")
      .json("data/playlists.ndjson")

    playlists.printSchema()
    println(s"${playlists.count()} playlists chargées")

    // MinHash + BandBuilder + ClickHouse insertion
    println("Calcul signatures + insertion ClickHouse...")
    playlists.foreachPartition {
      (rows: Iterator[Row]) => // type explicite obligatoire sur Dataset[Row]
        rows.foreach { row =>
          val pid = row.getLong(row.fieldIndex("pid"))
          val tracks = row.getSeq[String](row.fieldIndex("tracks"))
          val sig = MinHasher.signature(tracks)
          val bkts = BandBuilder.toBuckets(sig, pid)
          ClickHouseStore.insert(bkts)
        }
    }

    spark.stop()
    println("Build terminé")
  }
}

// Dont work
// object BuildJob {
//   def main(args: Array[String]): Unit = {

//     val spark = SparkSession.builder()
//       .appName("PlaylistSimilarityBuild")
//       .master("local[*]")
//       .config("spark.driver.memory", "4g")
//       .getOrCreate()

//     println("Lecture du ZIP")
//     val zipBytes = spark.read
//       .format("binaryFile")
//       .load("spotify_million_playlist_dataset.zip")
//       .collect()(0)
//       .getAs[Array[Byte]]("content")

//     println(s"ZIP lu, Taille: ${zipBytes.length / 1024 / 1024} MB")

//     val zis   = new ZipInputStream(new ByteArrayInputStream(zipBytes))
//     var entry = zis.getNextEntry

//     while (entry != null) {
//       println(s"  fichier trouvé: ${entry.getName}")
//       entry = zis.getNextEntry
//     }
//     zis.close()

//     spark.stop()
//     println("OK")
//   }
// }
