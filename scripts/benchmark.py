# scripts/benchmark.py
import subprocess, time, csv, os, re

# pid fixe pour la recherche — doit exister dans tous les datasets
SEARCH_PID = "853"
SIZES = [(1, 1000), (2, 2000), (5, 5000), (10, 10000), (20, 20000)]
RESULTS = []

# def get_valid_pid(n_playlists):
#     """Retourne un pid qui a au moins 1 candidat LSH dans le dataset actuel."""
#     r = subprocess.run(
#         'curl -s "http://localhost:8123/?user=default&password=clickhouse&query=SELECT+pid+FROM+lsh_buckets+GROUP+BY+pid+HAVING+count()%3E=16+LIMIT+1+FORMAT+TabSeparated"',
#         shell=True, capture_output=True, text=True
#     )
#     pid = r.stdout.strip()
#     return pid if pid else "0"

def run_and_extract_time(cmd, pattern):
    """Lance une commande et extrait le temps interne loggé par le job."""
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    output = result.stdout + result.stderr
    match = re.search(pattern, output)
    if match:
        return int(match.group(1))
    return -1

print("→ Compilation initiale...")
subprocess.run("sbt compile", shell=True, capture_output=True)

for n_files, n_playlists in SIZES:
    print(f"\n{'='*50}")
    print(f"→ Dataset : {n_playlists} playlists")

    # Extraction
    subprocess.run(f"python3 scripts/extract.py {n_files}", shell=True, capture_output=True)

    # BuildJob
    start = time.time()
    subprocess.run(f'sbt -no-colors "runMain com.similarity.jobs.BuildJob"', shell=True, capture_output=True)
    build_ms = int((time.time() - start) * 1000)
    print(f"  BuildJob : {build_ms}ms")

    # search_pid = get_valid_pid(n_playlists)
    # print(f"  pid cible : {search_pid}")

    # NaiveSearchJob → extrait "→ Temps écoulé : Xms"
    result_naive = subprocess.run(
        f'sbt -no-colors "runMain com.similarity.jobs.NaiveSearchJob {SEARCH_PID}"',
        shell=True, capture_output=True, text=True
    )
    naive_match = re.search(r"Temps écoulé : (\d+)ms", result_naive.stdout + result_naive.stderr)
    naive_ms = int(naive_match.group(1)) if naive_match else -1
    print(f"  Naïf (temps interne) : {naive_ms}ms")

    # SearchJob → extrait "→ Temps LSH : Xms"
    r = subprocess.run(f'sbt -no-colors "runMain com.similarity.jobs.SearchJob {SEARCH_PID}"',
                       shell=True, capture_output=True, text=True)
    out = r.stdout + r.stderr
    m = re.search(r"Temps LSH : (\d+)ms", out)
    lsh_ms = int(m.group(1)) if m else -1
    # Si -1 → le job a quand même tourné, capture le temps total subprocess
    if lsh_ms == -1:
        print(f"  ⚠ Pas de candidat LSH pour pid {SEARCH_PID} — temps capturé depuis subprocess")
    print(f"  LSH (temps interne)  : {lsh_ms}ms")

    RESULTS.append({"playlists": n_playlists, "build_ms": build_ms, "naive_ms": naive_ms, "lsh_ms": lsh_ms})

# Sauvegarde CSV
os.makedirs("data", exist_ok=True)
with open("data/benchmark.csv", "w", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["playlists", "build_ms", "naive_ms", "lsh_ms"])
    writer.writeheader()
    writer.writerows(RESULTS)

print("\n→ Résultats dans data/benchmark.csv")
print(f"\n{'Playlists':>12} {'Naïf (ms)':>12} {'LSH (ms)':>12}")
print("-" * 40)
for r in RESULTS:
    print(f"{r['playlists']:>12} {r['naive_ms']:>12} {r['lsh_ms']:>12}")