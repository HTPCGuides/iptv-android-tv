# Engine API and generator behaviour — as `m3u_gen_acestream` does it

Source of truth: `SCP002/m3u_gen_acestream` v2.2.2, commit `8a824cc` (2026-09-13), cloned into
`.reference/` (gitignored). File references below are into that tree. The app reproduces what
this code does; where the Go code is non-deterministic or odd, the choice made here is written
down next to it.

## 1. Engine calls

The engine address is `host:port`, default `127.0.0.1:6878`. HTTP only. The Go client has a
**5 s total timeout per request** (`util/network/http.go`, `NewHTTPClient(time.Second * 5)`),
honours proxy env vars, and sends no auth or headers.

### 1.1 Liveness — `GET /webui/api/service?method=get_version`

`acestream/engine.go` `WaitForConnection`. Retries every 5 s until it succeeds.

```json
{"result": {"code": 3012500, "platform": "android", "version": "3.2.5"}, "error": null}
```

Counted as **up** only when the body parses, `result.code != 0` and `error == null`. An HTTP 200
with `code: 0` is *not* up.

### 1.2 Channel index — `GET /search?page_size=200&page=N`

`acestream/engine.go` `SearchAll` / `searchAtPage`. No `query`, no category, no API key — an
empty search returns the whole index.

- `page` starts at **0**, `page_size` is **200**.
- Paging stops at the first page that returns **fewer than 200 `results`** (channels, not
  items). A full last page therefore costs one extra, empty request.
- On an error mid-way the pages fetched so far are still used: generation runs on the partial
  list and the error is only logged.
- The HTTP status is not checked; the body is decoded regardless.

```json
{
  "result": {
    "total": 1234,
    "time": 0.01,
    "results": [
      {
        "name": "Channel name",          // string OR number — decode both, keep the raw text
        "icons": [{"url": "https://…", "type": 0}],
        "items": [
          {
            "name": "Channel name HD",
            "infohash": "0123…40 hex",
            "channel_id": 123,
            "status": 2,                  // 1 = no guarantee, 2 = available
            "availability": 1.0,          // 0.0 … 1.0
            "availability_updated_at": 1757790000,   // unix seconds
            "categories": ["sport"],
            "languages": ["eng"],
            "countries": ["gb"]
          }
        ]
      }
    ]
  }
}
```

Vocabulary: a **result** (Go `SearchResult`) is a channel; each of its **items** is a
**source**. One M3U entry is written per *item*, and the entry name is the *item* name, not
the channel name.

### 1.3 Dead-source check (optional) — `GET <removeDeadLinkTemplate>`

`acestream/checker.go` `IsAvailable`, used only when `removeDeadSources: true`.
Default link: `http://{{.EngineAddr}}/ace/getstream?infohash={{.Infohash}}`.

- Fails if the request errors, the status is ≥ 400, or the first read returns 0 bytes /
  an error — all within `checkRespTimeout` (default 20 s, covering connect *and* first read).
- Reads one buffer of up to 1880 bytes (10 TS packets). With `useMpegTsAnalyzer: true` it
  must also parse one 188-byte MPEG-TS packet out of that buffer.
- `removeDeadWorkers` checks run in parallel (default 1 — the stock engine does not like more).
- Results are cached per infohash across all playlists in one run.

### 1.4 URLs the playlists point at (not called by the generator)

| Format        | Entry URL                                                   |
| ------------- | ----------------------------------------------------------- |
| MPEG-TS       | `http://{{.EngineAddr}}/ace/getstream?infohash={{.Infohash}}` |
| HLS           | `http://{{.EngineAddr}}/ace/manifest.m3u8?infohash={{.Infohash}}` |
| HTTPAceProxy  | `http://127.0.0.1:8000/infohash/{{.Infohash}}/stream.mp4`   |

## 2. Generator pipeline, per playlist

`m3u/generator.go` `Generate`. Every playlist starts again from the full, unmodified index.

1. **`categoryRxToCategoryMap`** (only if the map is non-empty): an item with no categories
   first becomes `[""]`. For each category, every regex key that matches it replaces it with
   its value.
   - Matching is against the *original* category string, not the result of an earlier
     replacement in the same pass.
   - Go map iteration order is random, so when several keys match, which value wins is
     random. **App: keys apply in config order and the last match wins.**
2. **`nameRxToCategoriesMap`** (only if non-empty): every regex key matching the item name
   *replaces* the item's category list with the value list. Same ordering note applies.
3. **`statusFilter`**: keep items whose `status` is in the list. An empty list keeps
   **nothing** (`lo.Contains` on an empty slice). The app's form will not allow an empty list;
   an imported config with one is reproduced faithfully and flagged.
4. **`availabilityThreshold`**: keep `availability >= threshold`.
5. **`availabilityUpdatedThreshold`** (Go duration, default `36h0m0s`): keep
   `now_unix - availability_updated_at <= threshold_seconds` (seconds truncated to an integer).
6. **Categories**, then **languages**, then **countries** — identical rules, shown for
   categories. For the check only, an empty list counts as `[""]`.
   - `categoriesFilter` non-empty, non-strict: keep if **any** item category is in the filter.
   - `categoriesFilter` non-empty, `categoriesFilterStrict: true`: keep if **every** item
     category is in the filter (`lo.Every(filter, itemCategories)`); e.g. filter
     `[movies, regional, documentaries]` drops `[movies, regional, sport]`.
   - `categoriesBlacklist` non-empty: drop if **any** item category is in it.
   - Comparison is exact and case-sensitive at this stage (lower-casing only happens in
     step 8). `""` in a filter/blacklist means "no value".
7. **Name**: `nameRxFilter` non-empty → keep if any regex matches; then `nameRxBlacklist`
   non-empty → drop if any regex matches.
8. **`removeDeadSources`** (§1.3).
9. **Entries**: each remaining item becomes one entry:

   | Variable         | Value                                                                         |
   | ---------------- | ----------------------------------------------------------------------------- |
   | `{{.Name}}`       | item `name`, verbatim                                                          |
   | `{{.Infohash}}`   | item `infohash`                                                                |
   | `{{.Categories}}` | item categories → lower-case → de-duplicated → `""` removed → sorted → joined with `;` |
   | `{{.Countries}}`  | same treatment                                                                 |
   | `{{.Languages}}`  | same treatment                                                                 |
   | `{{.EngineAddr}}` | config `engineAddr` (`host:port`)                                              |
   | `{{.TVGName}}`    | item name with every space replaced by `_`                                     |
   | `{{.IconURL}}`    | URL of the **channel's** first icon, or `""`                                   |

10. **Order**: stable sort by `Name`, then stable sort by `Categories` — i.e. by
    (Categories, Name), both compared **byte-wise** (Go `strings.Compare` on UTF-8).
11. **Write**: `headerTemplate` is written **verbatim** (it is *not* executed as a template),
    then `entryTemplate` is executed once per entry and appended. No separator is added
    between entries; the default templates end in `\n`. Go `text/template` does no escaping.

### Regular expressions

`dlclark/regexp2` with the `RE2` option: .NET-style syntax (look-arounds and `(?i)` work — the
default config uses `^(?!.*(…)).*`). `MatchString` is an **unanchored search**, so the Kotlin
equivalent is `Regex.containsMatchIn` / `Matcher.find()`, not `matches()`.

## 3. Config (`config/config.go`)

```yaml
engineAddr: 127.0.0.1:6878
playlists:
  - outputPath: ./out/playlist_mpegts_all.m3u8
    headerTemplate: |
      #EXTM3U url-tvg="http://epg.one/epg2.xml.gz" tvg-shift=0 deinterlace=1 m3uautoload=1
    entryTemplate: |
      #EXTINF:-1 group-title="{{.Categories}}",{{.Name}}
      http://{{.EngineAddr}}/ace/getstream?infohash={{.Infohash}}
    categoryRxToCategoryMap: {regex: category}
    nameRxToCategoriesMap:   {regex: [category, …]}
    nameRxFilter: []            nameRxBlacklist: []
    categoriesFilter: []        categoriesFilterStrict: false   categoriesBlacklist: []
    languagesFilter: []         languagesFilterStrict: false    languagesBlacklist: []
    countriesFilter: []         countriesFilterStrict: false    countriesBlacklist: []
    statusFilter: [2]
    availabilityThreshold: 1.0
    availabilityUpdatedThreshold: 36h0m0s
    removeDeadSources: false    # these five are filled with defaults when missing
    useMpegTsAnalyzer: false
    checkRespTimeout: 20s
    removeDeadLinkTemplate: http://{{.EngineAddr}}/ace/getstream?infohash={{.Infohash}}
    removeDeadWorkers: 1
```

Validation on load: every regex key/entry must compile, and `entryTemplate` and
`removeDeadLinkTemplate` must parse. Missing fields decode as Go zero values (so a missing
`statusFilter` is empty and keeps nothing; a missing `availabilityThreshold` is `0.0`).

### The three default playlists (the app's built-in profiles)

| # | outputPath | Link | Remap | Filters |
| - | ---------- | ---- | ----- | ------- |
| 1 | `playlist_mpegts_all.m3u8` | MPEG-TS | `^(?!.*(informational\|entertaining\|educational\|movies\|documentaries\|sport\|fashion\|music\|regional\|ethnic\|religion\|teleshop\|erotic_18_plus\|other_18_plus\|cyber_games\|amateur\|webcam)).*` → `other` | — |
| 2 | `playlist_hls_tv_+_music_+_no_category.m3u8` | HLS | `(?i)^tv$` → `television`, `^$` → `unknown` | categories `tv`, `music`, `unknown` |
| 3 | `playlist_httpaceproxy_all_but_porn.m3u8` | HTTPAceProxy | — | name blacklist `(?i).*erotic.*`, `(?i).*porn.*`, `(?i).*18\+.*`; category blacklist `erotic_18_plus`, `18+` |

All three: same header, `statusFilter: [2]`, `availabilityThreshold: 1.0`,
`availabilityUpdatedThreshold: 36h`, dead-source removal off.

## 4. Where the app deliberately differs

Output is unchanged by all of these; they are operational.

- **Read timeout 15 s** per engine request (connect 5 s) instead of 5 s total: a phone engine
  serving a 200-channel page over Wi-Fi can be slower, and a timeout truncates the index.
- **Paging stops after 5 000 pages** — the tool has no bound and would loop forever on an
  engine that ignores `page`.
- **Regex maps apply in config order** (last match wins) where Go's order is random (§2.1).
- **Templates** support `{{.Field}}`, comments and trim markers only; any other action is
  rejected when the profile is loaded instead of being executed.
- **Dead-source checks** go to the engine the index was read from, while entries may point at
  a different `EngineAddr`; the check cache lives as long as one index fetch. Connect and first
  read each get `checkRespTimeout`.
- **Config import** skips a playlist whose regex or template doesn't compile, and says why;
  the tool refuses the whole file. YAML is read with 1.2 booleans (only `true`/`false`), as
  go-yaml does, so a country code like `no` stays text.
- **MPEG-TS analyzer** is ziutek/dvb's sync search on the tool's buffer: `0x47` at offsets
  `i`, `i+188`, `i+376` for some `i < 188`, within one zero-padded 1 880-byte read.

Quirk kept as-is: profile 2 renames `tv` to `television` *before* filtering on `tv`, so no
source matches `tv` any more; it keeps `music` plus uncategorised sources (renamed `unknown`).
