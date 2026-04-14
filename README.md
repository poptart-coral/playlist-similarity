## prerequisites

- sbt 1.10.5
- scala 2.13.16
- docker 29.2.1
- docker compose
- java temurin 21 jdk

## how to run

```bash
sbt reload
sbt update
sbt compile
sbt run
```

The length of file is 5585657586, which exceeds the max length allowed: 2147483647

```python
python scripts/extract.py
```

```scala
sbt "runMain com.similarity.jobs.BuildJob"
```

```bash
curl -s "http://localhost:8123/?user=default&password=clickhouse&query=SELECT+count()+FROM+lsh_buckets"

curl -s "http://localhost:8123/?user=default&password=clickhouse&query=SELECT+*+FROM+lsh_buckets+LIMIT+5+FORMAT+Pretty"
```

Schema clickhouse:

```
lsh_buckets (
  bucket UInt64,
  playlist_id UInt64
) ENGINE = ReplacingMergeTree()
ORDER BY (bucket, playlist_id)
```