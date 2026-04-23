# scripts/benchmark.py
import subprocess, time, csv, sys, os

# pid fixe pour la recherche — doit exister dans tous les datasets
SEARCH_PID = "100373"
SIZES = [1000, 2000, 5000, 10000, 20000]
RESULTS = []

def run(cmd):
    """Lance une commande et retourne le temps écoulé en ms."""
    start = time.time()
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    elapsed = (time.time() - start) * 1000
    if result.returncode != 0:
        print(f"ERREUR: {result.stderr[-500:]}")
    return elapsed

# Compile une seule fois au début pour ne pas fausser les mesures
print("→ Compilation initiale...")
subprocess.run("sbt compile", shell=True)

for n_files in [1, 2, 5, 10, 20]:
    n_playlists = n_files * 1000
    print(f"\n{'='*50}")
    print(f"→ Dataset : {n_playlists} playlists ({n_files} fichiers)")

    # 1. Extraction
    print("  Extraction...")
    run(f"python3 scripts/extract.py {n_files}")

    # 2. Build index ClickHouse
    print("  BuildJob...")
    # Vide ClickHouse d'abord
    subprocess.run(
        'curl -s "http://localhost:8123/?user=default&password=clickhouse&query=TRUNCATE+TABLE+lsh_buckets"',
        shell=True
    )
    build_time = run("sbt -no-colors \"runMain com.similarity.jobs.BuildJob\"")
    print(f"  BuildJob : {build_time:.0f}ms")

    # 3. Naive search
    print("  NaiveSearchJob...")
    naive_time = run(f"sbt -no-colors \"runMain com.similarity.jobs.NaiveSearchJob {SEARCH_PID}\"")
    print(f"  NaïF : {naive_time:.0f}ms")

    # 4. LSH search
    print("  SearchJob...")
    lsh_time = run(f"sbt -no-colors \"runMain com.similarity.jobs.SearchJob {SEARCH_PID}\"")
    print(f"  LSH : {lsh_time:.0f}ms")

    RESULTS.append({
        "playlists": n_playlists,
        "build_ms": round(build_time),
        "naive_ms": round(naive_time),
        "lsh_ms": round(lsh_time),
    })

# Sauvegarde CSV
os.makedirs("data", exist_ok=True)
with open("data/benchmark.csv", "w", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["playlists", "build_ms", "naive_ms", "lsh_ms"])
    writer.writeheader()
    writer.writerows(RESULTS)

print("\n→ Résultats sauvegardés dans data/benchmark.csv")
print("\nRésumé :")
print(f"{'Playlists':>12} {'Naïf (ms)':>12} {'LSH (ms)':>12}")
print("-" * 40)
for r in RESULTS:
    print(f"{r['playlists']:>12} {r['naive_ms']:>12} {r['lsh_ms']:>12}")