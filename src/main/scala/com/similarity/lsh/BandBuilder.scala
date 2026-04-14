package com.similarity.lsh

import com.similarity.model.Bucket
import com.google.common.hash.Hashing
import java.nio.ByteBuffer

object BandBuilder {
  val NUM_BANDS     = 16
  val ROWS_PER_BAND = 8

  def toBuckets(sig: Array[Int], pid: Long): Seq[Bucket] = {
    (0 until NUM_BANDS).map { band =>
      val slice = sig.slice(band * ROWS_PER_BAND, (band + 1) * ROWS_PER_BAND)
      val bytes = slice.flatMap(i => ByteBuffer.allocate(4).putInt(i).array())
      val hash  = Hashing.murmur3_128().hashBytes(bytes).toString
      Bucket(band, hash, pid)
    }
  }
}