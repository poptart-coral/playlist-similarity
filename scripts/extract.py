# scripts/extract.py
import os
import zipfile, json, sys

ZIP = "./spotify_million_playlist_dataset.zip"
OUT_DIR = "data/slices"    
os.makedirs(OUT_DIR, exist_ok=True)

n_files = int(sys.argv[1]) if len(sys.argv) > 1 else 20

with zipfile.ZipFile(ZIP) as z:
    slices = sorted([f for f in z.namelist() if f.endswith(".json") and "mpd.slice" in f])[:n_files]
    for slice_name in slices:
        out_path = f"{OUT_DIR}/{os.path.basename(slice_name).replace('.json', '.ndjson')}"
        if os.path.exists(out_path):
            print(f"  skip {slice_name} (déjà extrait)")
            continue
        with z.open(slice_name) as f:
            data = json.load(f)
        with open(out_path, "w") as out:
            for pl in data["playlists"]:
                out.write(json.dumps({
                    "pid":    pl["pid"],
                    "tracks": [t["track_uri"] for t in pl["tracks"]]
                }) + "\n")
        print(f"  → {out_path} ({len(data['playlists'])} playlists)")