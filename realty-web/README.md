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
**Realty Web (bundled)** egg.

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

  Realty ships and serves no textures. Set `resource-pack-url` in the query-service
  module's `config.yml` to a pack the browser can fetch; `GET /v1/resource-pack`
  passes that URL on and the browser fetches it directly.

  **This is not `server.properties`' `resource-pack`.** That pack is sent to the game
  client, which applies it over its own copy of the game, so it is usually an override
  pack -- a browser has no vanilla assets underneath it. Name a pack that stands on its
  own, and one you have the right to publish.

  Two things to check when a preview stays untextured: the pack host must send CORS
  headers (many do not, having only ever served the game client), and the URL must be
  absolute `http`/`https` -- the module rejects anything else at startup rather than
  letting it fail silently in a browser.

  A viewer can also drop their own pack (`.zip`) onto the canvas. Resource packs only:
  a dropped schematic is refused, so nobody can swap out what a region's preview shows.
- **Credit the pack you configured.** Most packs are licensed on the condition that
  they are attributed, and the pack's URL is set on the game server where nobody
  browsing the site can see it. So the credit is configured in the same place, in the
  query-service module's `config.yml`:

  ```yaml
  resource-pack-url: "https://cdn.example.com/pack.zip"
  resource-pack-attribution:
    - text: "Textures: Faithful 64x"
      url: "https://faithfulpack.net/"
    - "CC BY 4.0"
  ```

  `GET /v1/resource-pack` reports the list beside the URL, and the credit renders under
  a region's preview -- and only there, since that is the only place the pack is used.
  A link must be an absolute `http`/`https` URL; the module rejects anything else at
  startup, and the front end re-checks before rendering, because the value is
  operator-supplied and ends up in a page. An empty list renders nothing.

  Nothing about the pack is configured on the web host. One setting, in the file where
  the pack itself was chosen -- splitting the two across two hosts is how one of them
  ends up stale.
- A region with no captured schematic is the normal case — capture is on demand via
  `/realty schematic capture`. The detail screen shows a panel, not an error.
