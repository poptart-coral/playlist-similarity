package com.similarity.minhash

import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets.UTF_8

object MinHasher {
  val NUM_HASHES = 128

  def signature(tracks: Seq[String]): Array[Int] = {
    (0 until NUM_HASHES).toArray.map { seed =>
      tracks.map { track =>
        Hashing.murmur3_32_fixed(seed).hashString(track, UTF_8).asInt()
      }.min
    }
  }
}