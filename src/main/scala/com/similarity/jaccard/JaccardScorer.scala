package com.similarity.jaccard

import com.similarity.model.Playlist
import com.similarity.model.ScoredPlaylist

object JaccardScorer {
  // The score method computes the Jaccard similarity between two sets of tracks. It returns a value between 0 and 1, where 1 means the playlists are identical and 0 means they have no tracks in common.
  def score(a: Set[String], b: Set[String]): Double =
    if (a.isEmpty && b.isEmpty) 0.0
    else a.intersect(b).size.toDouble / a.union(b).size

  // Jaccard to compute the similarity between a query playlist and all other playlists, returning the top k most similar ones.
  def exactTopK(query: Playlist, all: Seq[Playlist], k: Int = 10): Seq[ScoredPlaylist] = {
    val querySet = query.tracks.toSet
    all
      .filterNot(_.pid == query.pid)
      .map(p => ScoredPlaylist(p.pid, score(querySet, p.tracks.toSet)))
      .sortBy(-_.score)
      .take(k)
  }
}