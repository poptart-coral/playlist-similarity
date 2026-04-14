package com.similarity.model

case class Playlist(pid: Long, name: String, tracks: Seq[String])
case class Bucket(bandId: Int, bucketHash: String, pid: Long)
case class ScoredPlaylist(pid: Long, score: Double)