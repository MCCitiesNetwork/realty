# realty-web

The web-facing half of Realty: a read-only HTTP API over the Realty database, and a
browser front end for it.

| Project | What it is |
|---|---|
| `realty-rest` | The API. A standalone JVM service reading MariaDB. Serves `/v1`. |
| `realty-explorer` | The front end. React + Vite, built to static files. |
| `realty-web-dist` | Both of the above in one jar, for a single-process deployment. |

## Two ways to deploy

Pick one. The same explorer bundle works in both — it asks for `/config.json`, and
falls back to same-origin when there is none.

### Bundled — one process

`realty-web-dist-<version>-all.jar` serves the API under `/v1` and the front end at
`/`, from a single artifact.

```bash
REALTY_DB_URL=mariadb://db:3306/realty \
REALTY_DB_USERNAME=realty \
REALTY_DB_PASSWORD=... \
  java -jar realty-web-dist-all.jar
```

No CORS to configure and no `config.json` to write: the browser only ever sees one
origin. On Pterodactyl this is one server and one allocation — use the
**Realty Web** egg.

`REALTY_REST_WEB_ROOT` has no effect on this build and the egg does not offer it:
the front end is served from the jar's own classpath, which always wins over a
configured directory. Every other variable in the `realty-rest` table below applies
unchanged.

This is the only single-egg option. Pterodactyl supervises one foreground command,
so a second process backgrounded beside the jar would be unreachable by the stop
signal and invisible when it died; the bundled build sidesteps that by being one
process rather than two.

### Split — API and front end deployed separately

Run `realty-rest-<version>-all.jar` for the API, and serve `realty-explorer`'s
`dist/` from any static host. Use this when the front end should deploy on its own
cadence, or belongs on a CDN rather than a game-server host.

The front end then needs to know where the API is. Write a `config.json` beside
`index.html`:

```json
{ "apiBaseUrl": "https://api.example.com" }
```

**Recommended: put the two behind one origin anyway.** If the static host is nginx,
proxy `/v1` through to the API and no CORS or `config.json` is needed at all:

```nginx
location /v1/ { proxy_pass http://realty-rest-host:8080/v1/; }
location /    { try_files $uri /index.html; }
```

The `try_files` line is not optional: the explorer routes client-side, so a deep
link like `/region/world/plot_a` must return `index.html` rather than a 404.

If the two really are on different origins, set `REALTY_REST_CORS_ORIGINS` on the
API to the front end's origin. It is empty by default, which disables CORS — a
service that allowed every origin by default is one nobody chose.

## The front end

`realty-explorer` is built with [Ant Design](https://ant.design/) and reads like a
property listing site. Everything on it is the API's answer at load time: there is no
placeholder copy, no stock imagery and no currency symbol, because the API reports none.

| Route | What it shows | Backed by |
|---|---|---|
| `/` | Search, the market in numbers, tags, what is vacant, recent activity, auctions | `/v1/stats`, `/v1/tags`, `/v1/worlds`, `/v1/regions/search`, `/v1/activity`, `/v1/auctions` |
| `/listings` | The search, with every filter in the URL so a filter set is a link. "Show" spells the states a visitor thinks in -- for sale, for rent, sold, leased -- from the API's type and occupancy filters | `/v1/regions/search` |
| `/region/:world/:region` | One listing: 3D preview, price and terms, facts, then its history beside who WorldGuard lets build | `/v1/region`, `/v1/region/schematic`, `/v1/region/history`, `/v1/region/members`, `/v1/resource-pack` |
| `/auctions` | Every auction taking bids, a card each with a live countdown | `/v1/auctions` |
| `/activity` | The server-wide feed as a day-by-day timeline, filterable by world and event type | `/v1/activity` |
| `/owners` | Title holders ranked by plots held, each bar relative to the leader | `/v1/leaderboard/owners` |
| `/players/:id` | One player's holdings | `/v1/players/summary`, `/v1/players/regions` |

The header's player finder suggests title holders from `/v1/leaderboard/owners` as you
type, with their heads from crafthead.net, and resolves any other name through
`/v1/players/lookup` -- falling back to playerdb.co only when the module cannot be
asked. Player names, region geometry, access lists and the resource pack all come from
the query-service module; when it is unreachable the pages say so rather than showing
an empty list, and an unnamed player is shown by the first block of their UUID.

A freehold with an asking price is shown as for sale whoever holds its title: a holder
who has priced a plot is selling it. Its "last sold for" figure is the last sale, never
the asking price.

The 3D preview keeps the camera outside the plot -- orbit and zoom, but never through a
wall -- and the renderer's own memory of resource packs is cleared before every start,
so a pack changed on the game server reaches every browser on its next visit.

The theme follows the operating system's light or dark preference.

## Developing the front end

```bash
cd realty-explorer
npm install
npm run dev        # proxies /v1 to localhost:8080, so no CORS in development
npm run test
npm run generate:api   # after changing openapi.yaml
```

`npm run generate:api` regenerates `src/api/schema.d.ts` from
`realty-rest/src/main/resources/openapi.yaml`. That file is **committed**, and CI
fails if regenerating it produces a diff — so changing the API without regenerating
breaks the build rather than silently shipping a client that misdescribes it.

`./gradlew build` runs all of this too: the Node plugin provisions its own Node,
installs, tests and builds. The first run downloads a toolchain and is slow.

## Notes

- **A resource pack is effectively required for the preview to be readable.** Without
  one the renderer does not draw blocks untextured -- it does not draw most of them at
  all, since there is no model to build a mesh from. Chests and other block entities
  still appear, so a plot renders as a few objects floating in space and looks like a
  failed capture.

  Realty ships and serves no textures. List the packs in `resource-packs` in the
  query-service module's `config.yml`; `GET /v1/resource-pack` passes the URLs on and
  the browser fetches each directly.

  **First is highest priority.** Where two packs provide the same texture the earlier
  one wins, so write an override pack above the base pack it expects underneath it.

  **`server.properties`' `resource-pack` will not work on its own.** That pack is sent
  to the game client, which applies it over its own copy of the game, so it is usually
  an override pack and a browser has no vanilla assets underneath it. That is what the
  list is for: put your server pack first and a pack carrying the base assets below it,
  and the renderer merges the two. Every pack must be one you have the right to publish.

  Two things to check when a preview stays untextured: every pack host must send CORS
  headers (many do not, having only ever served the game client), and each URL must be
  absolute `http`/`https` -- the module rejects anything else at startup rather than
  letting it fail silently in a browser. One unreachable pack costs its own textures;
  the rest still load.

  A viewer can also drop their own pack (`.zip`) onto the canvas. Resource packs only:
  a dropped schematic is refused, so nobody can swap out what a region's preview shows.
- **Credit the pack you configured.** Most packs are licensed on the condition that
  they are attributed, and the pack's URL is set on the game server where nobody
  browsing the site can see it. So the credit is configured in the same place, in the
  query-service module's `config.yml`:

  ```yaml
  resource-packs:
    - url: "https://cdn.example.com/server-override.zip"
      attribution:
        - text: "Textures: Example Pack 32x"
          url: "https://packs.example.com/"
    - url: "https://cdn.example.com/vanilla-base.zip"
      attribution:
        - "CC BY 4.0"
  ```

  Credits belong to the entry, not to the file: two packs may be licensed differently,
  and a credit shown against the wrong one credits the wrong author.

  `GET /v1/resource-pack` reports each pack's credits beside its URL, and they render under
  a region's preview -- and only there, since that is the only place the pack is used.
  A link must be an absolute `http`/`https` URL; the module rejects anything else at
  startup, and the front end re-checks before rendering, because the value is
  operator-supplied and ends up in a page. An empty list renders nothing.

  Nothing about the pack is configured on the web host. One setting, in the file where
  the pack itself was chosen -- splitting the two across two hosts is how one of them
  ends up stale.
- A region with no captured schematic is the normal case — capture is on demand via
  `/realty schematic capture`. The detail screen shows a panel, not an error.
- **A capture starts at the block the player stands on.** A region claimed from bedrock
  to the build limit would otherwise capture as a column of stone with a house on top.
  The block under the capturing player's feet becomes the floor; the footprint and
  ceiling are the region's own. Run the command from the doorstep, not the basement.
  Because the floor is where the player stands, the command is players-only: the
  console cannot run it.
- **Use a self-contained resource pack.** The renderer builds geometry from the pack's
  `blockstates` and `models`, so a textures-only override pack such as Faithful draws
  nothing but chests. Mojang's own client jar works as-is and sends CORS headers, and
  linking to it redistributes nothing:
  `https://piston-data.mojang.com/v1/objects/<sha1>/client.jar` (find the sha1 for a
  version in `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`).
