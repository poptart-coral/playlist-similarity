package com.similarity.store

import com.similarity.model.Bucket
import java.sql.DriverManager

object ClickHouseStore {
  val url = "jdbc:ch://localhost:8123/default?user=default&password=clickhouse"

  def setup(): Unit = {
    val conn = DriverManager.getConnection(url)
    conn
      .createStatement()
      .execute("""
      CREATE TABLE IF NOT EXISTS lsh_buckets (
        band_id     UInt8,
        bucket_hash String,
        pid         UInt64
      ) ENGINE = MergeTree()
      ORDER BY (band_id, bucket_hash)
    """)
    conn.close()
  }

  def truncate(): Unit = {
    val conn = DriverManager.getConnection(url)
    conn.createStatement().execute("TRUNCATE TABLE lsh_buckets")
    conn.close()
  }

  def insertBatch(allBuckets: Seq[Bucket]): Unit = {
    val conn = DriverManager.getConnection(url)
    val stmt = conn.prepareStatement(
      "INSERT INTO lsh_buckets (band_id, bucket_hash, pid) VALUES (?, ?, ?)"
    )
    allBuckets.foreach { b =>
      stmt.setInt(1, b.bandId)
      stmt.setString(2, b.bucketHash)
      stmt.setLong(3, b.pid)
      stmt.addBatch()
    }
    stmt.executeBatch()
    conn.close()
  }

  def candidatesBatch(buckets: Seq[Bucket]): Set[Long] = {
    // Batch : 1 seule requête pour toutes les bandes d'une playlist
    val inClause = buckets
      .map(b => s"('${b.bucketHash}', ${b.bandId})")
      .mkString(",")
    val sql = s"""
      SELECT DISTINCT pid
      FROM lsh_buckets
      WHERE (bucket_hash, band_id) IN ($inClause)
    """
    val conn = DriverManager.getConnection(url)
    try {
      val rs = conn.createStatement().executeQuery(sql)
      val result = scala.collection.mutable.Set[Long]()
      while (rs.next()) result.add(rs.getLong("pid"))
      result.toSet
    } finally {
      conn.close() // toujours fermé même en cas d'exception
    }
  }
}
