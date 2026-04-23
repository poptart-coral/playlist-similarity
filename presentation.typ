#import "@preview/polylux:0.4.0": *

// ─── Thème ────────────────────────────────────────────────────────────────────
#let primary  = rgb("#1a1a2e")
#let accent   = rgb("#e94560")
#let light    = rgb("#f0f0f0")
#let subtle   = rgb("#8892b0")
#let card-bg  = rgb("#16213e")
#let green    = rgb("#4ade80")
#let yellow   = rgb("#fbbf24")
#let orange   = rgb("#fb923c")

#set page(paper: "presentation-16-9", fill: primary)
#set text(font: ("Inter", "Liberation Sans", "DejaVu Sans"), fill: light, size: 14pt)
#set strong(delta: 300)
#set par(justify: false, leading: 0.55em)

#let chip(t) = box(
  fill: accent.lighten(80%),
  inset: (x: 10pt, y: 5pt),
  radius: 20pt,
)[#text(size: 14pt, fill: accent, weight: "bold")[#t]]

#let card(title, body) = rect(
  fill: card-bg, radius: 8pt,
  inset: 14pt, width: 100%,
)[
  #text(size: 15pt, weight: "bold", fill: accent)[#title]
  #v(4pt)
  #body
]

#let slide-title(t) = {
  align(center)[
    #text(size: 22pt, weight: "bold")[#t]
    #v(3pt)
    #rect(width: 120pt, height: 4pt, fill: accent, radius: 2pt)
  ]
}

// ─── SLIDE 1 : Titre ──────────────────────────────────────────────────────────
#slide[
  #align(center + horizon)[
    #rect(width: 80pt, height: 6pt, fill: accent, radius: 3pt)
    #v(20pt)
    #text(size: 40pt, weight: "bold", fill: light)[Détection de playlists similaires]
    #v(14pt)
    #text(size: 19pt, fill: subtle)[MinHash · LSH · Apache Spark · ClickHouse]
    #v(28pt)
    #text(size: 15pt, fill: subtle)[Pauline Contat · DO5 · Polytech Montpellier · 2026]
  ]
]

// ─── SLIDE 2 : Problème ───────────────────────────────────────────────────────
#slide[
  #slide-title("Le problème")
  #v(8pt)
  #grid(columns: (1fr, 1fr), gutter: 14pt,

    card("Contexte")[
      - *1 000 000* de playlists (Spotify MPD)
      - Chaque playlist : jusqu'à *376 tracks*
      #v(5pt)
      - Recommandation, déduplication, clustering
      - Comparer chaque paire = *trop lent*
      #v(5pt)
      #text(fill: subtle, size: 13pt)[Approche naïve sur 1M playlists :]
      #text(weight: "bold", fill: accent, size: 16pt)[~500 milliards de paires]
      #text(fill: subtle, size: 13pt)[Latence, coût, mémoire : impossible en production]
    ],

    card("Objectif")[
      Trouver rapidement les playlists avec\
      *plus de 50% de tracks en commun*
      #v(8pt)
      #align(center)[
        #text(size: 15pt, fill: subtle)[Similarité mesurée par le *score de Jaccard*]
        #v(5pt)
        #chip("Jaccard(A,B) = |A ∩ B| / |A ∪ B|")
        #v(5pt)
        #chip("Seuil : Jaccard >= 0.5")
      ]
      #v(6pt)
      #text(fill: subtle, size: 13pt)[0 = rien en commun · 1 = identiques]
    ],
  )
]

// ─── SLIDE 3 : Dataset & Évaluation ──────────────────────────────────────────
#slide[
  #slide-title("Dataset & protocole d'évaluation")
  #v(8pt)
  #grid(columns: (1fr, 1fr), gutter: 12pt,

    card("Structure des données")[
      - Source : *Spotify MPD*
      - Format : *NDJSON* (1 ligne = 1 playlist)
      - Champs utiles : `pid`, `tracks`
      #v(4pt)
      #text(fill: subtle, size: 12pt)[{"pid": 320, "tracks": ["spotify:track:...", ...]}]
    ],

    card("Volumes utilisés")[
      - *1 000 000* playlists pour l'index
      - Évaluation sur *20 000* playlists
      - Filtre >= 20 tracks → *16 724*
      #v(4pt)
      #text(fill: subtle, size: 13pt)[Moy. 67 · Méd. 49 · Max 376]
    ],

    card("Filtre >= 20 tracks")[
      - Playlists courtes biaisent le Jaccard
      #text(fill: subtle, size: 13pt)[Ex : A={T1,T2}, B={T1,T3} → Jaccard=0.33]
      - Réduit les faux positifs accidentels
      - Precision & F1 plus stables
    ],

    card("GroundTruthJob")[
      - Cross Join Spark sur 16 724 playlists
      - Jaccard exact sur *toutes les paires*
      - *1h29 · 12 cores* · exécuté *une seule fois*
      - Résultat stocké en CSV
      #v(4pt)
      #text(fill: accent, size: 13pt)[94 paires similaires trouvées]
    ],
  )
]

// ─── SLIDE 4 : MinHash ────────────────────────────────────────────────────────
#slide[
  #slide-title("MinHash : estimer Jaccard sans tout comparer")
  #v(8pt)
  #grid(columns: (1fr, 1fr), gutter: 14pt,

    card("Principe")[
      - Signature compacte par playlist
      - *k fonctions de hash*, garder le minimum
      - Seed différent par fonction
      #v(6pt)
      #align(center)[
        #text(fill: subtle, size: 14pt)[P(min\_h(A) = min\_h(B)) = Jaccard(A, B)]
      ]
      #v(6pt)
      - Signature : *128 entiers* par playlist
      - *Pourquoi 128 ?* Bon compromis précision/vitesse
      #text(fill: subtle, size: 13pt)[64 → trop bruité · 256 → 2× plus lent]
    ],

    card("MurmurHash3")[
      - Hash *très rapide*, non-cryptographique
      - Distribution uniforme sur les valeurs
      #v(8pt)
      #text(size: 13pt)[
        ```
        A = {T1,T2,T3,T5}
        B = {T1,T3,T4,T5}
        Jaccard réel = 3/5 = 0.60

        Sig A :[1][2][3][4]
        Sig B :[2][3][1]
        3/4 égaux → estimation = 0.75
        ```
      ]
    ],
  )
]

// ─── SLIDE 5 : LSH Banding ────────────────────────────────────────────────────
#slide[
  #slide-title("LSH Banding : trouver les candidats rapidement")
  #v(8pt)
  #grid(columns: (1fr, 1fr), gutter: 14pt,

    card("Principe du banding")[
      - Diviser 128 hash en *b bandes de r lignes*
      - Deux playlists dans le même bucket → candidats
      #v(5pt)
      #text(fill: subtle, size: 14pt)[Seuil d'inflexion : s\* = (1/b)^(1/r)]
      #v(5pt)
      - Diviseurs de 128 : {1, 2, 4, 8, *16*, 32, 64, 128}
      #text(fill: subtle, size: 13pt)[s\* n'est pas librement choisi]
      - *16×8 → 16 buckets* par playlist
    ],

    card("Comparaison des configurations")[
      #text(size: 14pt)[
        #table(
          columns: (58pt, 50pt, 72pt, 72pt),
          fill: (col, row) => if row == 0 { accent.darken(30%) } else if row == 1 { green.darken(60%) } else { card-bg },
          inset: 6pt,
          [*Config*], [*s\**], [*Precision*], [*Recall*],
          [*16×8*],   [*0.71*], [#text(fill: green)[*100%*]], [#text(fill: yellow)[*47.9%*]],
          [32×4],     [0.595],  [< 100%],      [~70%],
          [64×2],     [0.13],   [très faible],  [~100%],
        )
      ]
      #v(6pt)
      - *16×8 choisi* : zéro faux positif observé
      - Trade-off : pertes entre Jaccard 0.5 et 0.71
      #text(fill: subtle, size: 13pt)[Priorité à la fiabilité (precision)]
    ],
  )
]

// ─── SLIDE 6 : Architecture ───────────────────────────────────────────────────
#slide[
  #slide-title("Architecture du pipeline")
  #v(8pt)
  #align(center)[
    #text(fill: subtle, size: 13pt)[Spark : calcul distribué · ClickHouse : stockage et requêtes des buckets LSH]
    #v(6pt)
    #block(width: 96%)[
      #grid(columns: (1fr, 16pt, 1fr, 16pt, 1fr), gutter: 0pt,
        rect(fill: card-bg, radius: 8pt, inset: 12pt)[
          #text(weight: "bold", fill: accent, size: 15pt)[BuildJob]
          #v(5pt)
          #text(size: 13pt)[
            - Charge les fichiers NDJSON
            - Calcule MinHash (128) + banding (16×8)
            - Écrit les buckets dans ClickHouse
          ]
        ],
        align(center + horizon)[#text(size: 18pt, fill: accent)[->]],
        rect(fill: card-bg, radius: 8pt, inset: 12pt)[
          #text(weight: "bold", fill: accent, size: 15pt)[SearchJob]
          #v(5pt)
          #text(size: 13pt)[
            - Lit la playlist cible
            - Calcule signature + 16 buckets
            - Batch 16 requêtes vers ClickHouse
            - Rerank avec Jaccard exact
          ]
        ],
        align(center + horizon)[#text(size: 18pt, fill: accent)[->]],
        rect(fill: card-bg, radius: 8pt, inset: 12pt)[
          #text(weight: "bold", fill: accent, size: 15pt)[GroundTruthJob]
          #v(5pt)
          #text(size: 13pt)[
            - Cross Join Spark
            - Exécuté une fois offline
            - Sert de référence pour l'évaluation
          ]
        ],
      )
    ]
  ]
  #v(8pt)
  #align(center)[
    #chip("Spark local[*] — 12 cores")
    #h(8pt)
    #chip("ClickHouse MergeTree partitionné par band_index")
  ]
]

// ─── SLIDE 7 : ClickHouse ─────────────────────────────────────────────────────
#slide[
  #slide-title("Pourquoi ClickHouse (et pas Postgres)")
  #v(8pt)
  #grid(columns: (1fr, 1fr, 1fr), gutter: 12pt,
    card("Accès adapté au LSH")[
      - Table partitionnée par *band_index*
      - Une recherche lit seulement 1/16 des partitions
      #v(5pt)
      #text(fill: subtle, size: 13pt)[WHERE band_index = X]
    ],

    card("Performance en lecture")[
      - 16 lookups ciblés par bucket
      - Peu de colonnes lues
      - Agrégations rapides
      #v(5pt)
      #text(fill: subtle, size: 13pt)[Très bon pour read-many analytique]
    ],

    card("Pourquoi pas Postgres")[
      - Postgres : excellent en transactionnel
      - Ici : lectures analytiques segmentées
      #v(5pt)
      #text(fill: subtle, size: 13pt)[Profil write-once, read-many → ClickHouse plus adapté]
    ],
  )
]

// ─── SLIDE : Évaluation ─────────────────────────────────────────────────────
#slide[
  #slide-title("Évaluation : Precision & Recall")
  #v(6pt)

  #grid(columns: (1fr, 1fr), gutter: 12pt,

    card("Protocole")[
      - Config : *16 bandes × 8 lignes*
      - Seuil Jaccard : *0.5*
      - s\* = *(1/16)^(1/8) = 0.7071*
      #v(4pt)
      - 20 000 playlists testées
      - Filtre >= 20 tracks → *16 724*
      - Ground truth : *94 paires*
      #v(4pt)
      #text(fill: subtle, size: 13pt)[Precision = VP / (VP + FP)]
      #text(fill: subtle, size: 13pt)[Recall = VP / (VP + FN)]
      #v(4pt)
      #text(fill: orange, size: 13pt)[Sans filtre : 25 faux positifs]
    ],

    card("Résultats 16×8")[
      #text(size: 13pt)[
        #table(
          columns: (122pt, 82pt),
          fill: (col, row) => if row == 0 { accent.darken(30%) } else { card-bg },
          inset: 6pt,
          [*Métrique*], [*Valeur*],
          [Vrais positifs], [45],
          [Faux positifs], [#text(fill: green)[0]],
          [Faux négatifs], [#text(fill: yellow)[49]],
          [Precision], [#text(fill: green)[*100%*]],
          [Recall], [#text(fill: yellow)[*47.9%*]],
          [F1-score], [*64.7%*],
          [GroundTruth], [1h 29],
          [SearchJob], [#text(fill: green)[< 1 s]],
        )
      ]
      #v(4pt)
      #text(fill: subtle, size: 12pt)[49 FN : paires entre 0.5 et 0.71]
    ],
  )
]

// ─── SLIDE 9 : Scalabilité ────────────────────────────────────────────────────
#slide[
  #slide-title("Scalabilité : LSH vs approche naïve")
  #v(8pt)
  #grid(columns: (1fr, 1fr), gutter: 14pt,

    card("Mesures expérimentales")[
      #text(size: 14pt)[
        #table(
          columns: (75pt, 60pt, 60pt, 55pt),
          fill: (col, row) => if row == 0 { accent.darken(30%) } else if row == 5 { card-bg.lighten(10%) } else { card-bg },
          inset: 6pt,
          [*Playlists*], [*Naïf*],  [*LSH*],   [*Ratio*],
          [1 000],       [218ms],   [232ms],   [#text(fill: yellow)[0.9×]],
          [2 000],       [528ms],   [180ms],   [#text(fill: green)[2.9×]],
          [5 000],       [510ms],   [218ms],   [#text(fill: green)[2.3×]],
          [10 000],      [1.9s],    [420ms],   [#text(fill: green)[4.5×]],
          [20 000],      [5.8s],    [< 1s],    [#text(fill: green)[> 6×]],
        )
      ]
      #v(5pt)
      #text(fill: subtle, size: 12pt)[Mesures locales · variabilité possible (cache, GC, JVM)]
    ],

    card("Analyse")[
      - *SearchJob O(1)* — indépendant de n
      - *Overhead fixe* : ~150–200ms (JDBC + batch CH)
      #text(fill: subtle, size: 13pt)[Moins rentable sous ~5k playlists]
      #v(6pt)
      - *Naïf à 1M* : OutOfMemoryError
      #text(fill: subtle, size: 13pt)[Cross join impossible en mémoire]
      #v(6pt)
      - *LSH à 1M* : 231ms · 240 candidats
      #text(fill: subtle, size: 13pt)[2 playlists Jaccard = 1.0 · scores jusqu'à 0.51]
    ],
  )
]

// ─── SLIDE 10 : Limites & suites ──────────────────────────────────────────────
#slide[
  #slide-title("Limites actuelles et prochaines étapes")
  #v(8pt)
  #grid(columns: (1fr, 1fr), gutter: 14pt,

    card("Limites")[
      - Rappel limité avec config 16×8
      - Dépend de la qualité des métadonnées tracks
      - Benchmark local : sensible au warm-up JVM
      - Ground truth coûteux (cross join exhaustif)
    ],

    card("Améliorations")[
      - Tester 32×4 + re-ranking pour remonter le recall
      - Score hybride (Jaccard + features audio/artist)
      - Évaluation industrialisée (runs répétés, p95)
      - Ingestion incrémentale + monitoring qualité
    ],
  )
]

// ─── SLIDE 11 : Conclusion ────────────────────────────────────────────────────
#slide[
  #slide-title("Conclusion")
  #v(8pt)
  #align(center)[
    #text(size: 26pt, weight: "bold")[Un pipeline scalable de A à Z]
    #v(14pt)
    #grid(columns: (1fr, 1fr, 1fr, 1fr), gutter: 10pt,
      rect(fill: card-bg, radius: 8pt, inset: 12pt)[
        #align(center)[
          #text(weight: "bold", size: 15pt)[MinHash]
          #v(5pt)
          #text(fill: subtle, size: 13pt)[
            128 fonctions\
            ±8.8% d'erreur\
            Comparaison rapide
          ]
        ]
      ],
      rect(fill: card-bg, radius: 8pt, inset: 12pt)[
        #align(center)[
          #text(weight: "bold", size: 15pt)[LSH 16×8]
          #v(5pt)
          #text(fill: subtle, size: 13pt)[
            s\* = 0.71\
            Precision 100%\
            F1 = 64.7%
          ]
        ]
      ],
      rect(fill: card-bg, radius: 8pt, inset: 12pt)[
        #align(center)[
          #text(weight: "bold", size: 15pt)[Spark]
          #v(5pt)
          #text(fill: subtle, size: 13pt)[
            Parallèle & scalable\
            BuildJob O(n)\
            SearchJob O(1)
          ]
        ]
      ],
      rect(fill: card-bg, radius: 8pt, inset: 12pt)[
        #align(center)[
          #text(weight: "bold", size: 15pt)[ClickHouse]
          #v(5pt)
          #text(fill: subtle, size: 13pt)[
            Requêtes en batch\
            Latence faible\
            Adapté au LSH
          ]
        ]
      ],
    )
    #v(12pt)
    #text(size: 14pt, fill: subtle)[1M playlists · 16 requêtes batch · résultats en < 1s]
  ]
]