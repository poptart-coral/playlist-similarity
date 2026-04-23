# Playlist Similarity

## Prérequis

- **sbt** `1.10.5`
- **Scala** `2.13.16`
- **Java** Temurin JDK `21`
- **Docker** `29.2.1+`
- **Docker Compose**
- **Python 3** (pour les scripts)

---

## Installation rapide

```bash
sbt reload
sbt update
sbt compile
```

---

## Lancer l’infra (ClickHouse)

```bash
docker compose up -d
```

Vérifier que ClickHouse répond :

```bash
curl -s "http://localhost:8123/?user=default&password=clickhouse&query=SELECT+1"
```

Arrêter l’infra :

```bash
docker compose down
```

---

## Générer les données

```bash
python3 scripts/extract.py
```

Exemple avec argument (nombre de fichiers) :

```bash
python3 scripts/extract.py 10
```

---

## Exécuter les jobs Scala

Build (indexation / préparation) :

```bash
sbt "runMain com.similarity.jobs.BuildJob"
```

Recherche (exemple playlist id = `320`) :

```bash
sbt "runMain com.similarity.jobs.SearchJob 320"
```

Exécution standard du projet :

```bash
sbt run
```

---

## Vérifications ClickHouse utiles

Nombre de lignes dans les buckets :

```bash
curl -s "http://localhost:8123/?user=default&password=clickhouse&query=SELECT+count()+FROM+lsh_buckets"
```

Aperçu des données :

```bash
curl -s "http://localhost:8123/?user=default&password=clickhouse&query=SELECT+*+FROM+lsh_buckets+LIMIT+5+FORMAT+Pretty"
```

Schéma attendu :

```sql
lsh_buckets (
  bucket UInt64,
  playlist_id UInt64
) ENGINE = ReplacingMergeTree()
ORDER BY (bucket, playlist_id)
```

---

## Benchmark

```bash
python3 scripts/benchmark.py
```

---

## Commandes de debug sbt

```bash
sbt clean
sbt compile
sbt test
sbt run
```

---

## Fichiers utiles

- Configuration build : [playlist-similarity/build.sbt](playlist-similarity/build.sbt)
- Compose Docker : [playlist-similarity/docker-compose.yaml](playlist-similarity/docker-compose.yaml)
- Script extraction : [playlist-similarity/scripts/extract.py](playlist-similarity/scripts/extract.py)
- Script benchmark : [playlist-similarity/scripts/benchmark.py](playlist-similarity/scripts/benchmark.py)

```sql
-- Nombre de lignes dans les buckets
SELECT count() FROM lsh_buckets;

-- Aperçu des données
SELECT * FROM lsh_buckets LIMIT 5;

-- Schéma de la table
DESCRIBE lsh_buckets;

-- Playlists avec le plus de buckets (candidats potentiels)
SELECT pid, count() as bucket_count
FROM lsh_buckets
GROUP BY pid
ORDER BY bucket_count DESC
LIMIT 20;

-- Playlists avec le plus de candidats similaires potentiels
SELECT pid, COUNT(DISTINCT bucket_hash) as unique_buckets, COUNT(*) as total_entries
FROM lsh_buckets
GROUP BY pid
HAVING total_entries > 0
ORDER BY unique_buckets DESC
LIMIT 20;

-- Buckets les plus peuplés (signes de similarité forte)
SELECT bucket_hash, band_id, COUNT(*) as playlist_count
FROM lsh_buckets
GROUP BY bucket_hash, band_id
HAVING playlist_count > 1
ORDER BY playlist_count DESC
LIMIT 20;

-- Playlists partageant au moins 1 bucket
SELECT a.pid, b.pid, COUNT(*) as shared_buckets
FROM lsh_buckets a
JOIN lsh_buckets b ON a.bucket_hash = b.bucket_hash AND a.band_id = b.band_id
WHERE a.pid < b.pid
GROUP BY a.pid, b.pid
ORDER BY shared_buckets DESC
LIMIT 50;

-- Distribution : combien de playlists par nombre de buckets
SELECT bucket_count, COUNT(*) as playlist_count
FROM (
  SELECT pid, COUNT() as bucket_count
  FROM lsh_buckets
  GROUP BY pid
)
GROUP BY bucket_count
ORDER BY bucket_count;

-- Playlists sans candidats (isolées)
SELECT pid
FROM lsh_buckets
GROUP BY pid
HAVING COUNT(DISTINCT pid) = 1;
```