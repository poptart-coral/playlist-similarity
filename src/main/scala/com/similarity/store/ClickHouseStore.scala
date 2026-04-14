package com.similarity.store

import com.similarity.model.Bucket
import java.sql.DriverManager

object ClickHouseStore {
  val url = "jdbc:ch://localhost:8123/default?user=default&password=clickhouse"


  def setup(): Unit = {
    val conn = DriverManager.getConnection(url)
    conn.createStatement().execute("""
      CREATE TABLE IF NOT EXISTS lsh_buckets (
        band_id     UInt8,
        bucket_hash String,
        pid         UInt64
      ) ENGINE = MergeTree()
      ORDER BY (band_id, bucket_hash)
    """)
    conn.close()
  }

  def insert(buckets: Seq[Bucket]): Unit = {
    val conn = DriverManager.getConnection(url)
    val stmt = conn.prepareStatement(
      "INSERT INTO lsh_buckets (band_id, bucket_hash, pid) VALUES (?, ?, ?)"
    )
    buckets.foreach { b =>
      stmt.setInt(1, b.bandId)
      stmt.setString(2, b.bucketHash)
      stmt.setLong(3, b.pid)
      stmt.addBatch()
    }
    stmt.executeBatch()
    conn.close()
  }

  def candidates(bandId: Int, hash: String): Seq[Long] = {
    val conn = DriverManager.getConnection(url)
    val stmt = conn.prepareStatement(
      "SELECT DISTINCT pid FROM lsh_buckets WHERE band_id=? AND bucket_hash=?"
    )
    stmt.setInt(1, bandId)
    stmt.setString(2, hash)
    val rs  = stmt.executeQuery()
    val buf = scala.collection.mutable.ListBuffer[Long]()
    while (rs.next()) buf += rs.getLong("pid")
    conn.close()
    buf.toSeq
  }
}