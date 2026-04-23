# scripts/extract.py
import zipfile, json, sys

ZIP = "./spotify_million_playlist_dataset.zip"
OUT = "data/playlists.ndjson"
MAX = int(sys.argv[1]) if len(sys.argv) > 1 else 5  # ← argument CLI

with zipfile.ZipFile(ZIP) as zf:
    files = [n for n in zf.namelist() if n.endswith(".json")][:MAX]
    with open(OUT, "w") as out:
        for name in files:
            print(f"→ {name}")
            data = json.loads(zf.read(name))
            for pl in data["playlists"]:
                out.write(json.dumps({
                    "pid":    pl["pid"],
                    "tracks": [t["track_uri"] for t in pl["tracks"]]
                }) + "\n")
print("over!")