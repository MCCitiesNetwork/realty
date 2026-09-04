# Realty Web — Explorer and the `realty-web` Grouping — Design

Date: 2026-09-04
Status: Approved, pending implementation plan

Companion specs: `2026-09-02-realty-rest-api-design.md` (the API this consumes),
`2026-09-04-region-schematic-capture-design.md` (the capture feature whose bytes
this renders).

## Purpose

Give Realty a browser front end: browse and filter regions, open one, and see
its captured schematic rendered in 3D. This is the consumer the schematic
capture work was built for — until now the bytes have had no reader.

Along the way, group the web-facing pieces: `realty-rest` moves under
`realty-web/`, joining the new `realty-explorer`.

## Scope

In scope for v1:

- Moving `realty-rest` to `realty-web/realty-rest`, history preserved.
- A new `realty-web/realty-explorer`: React + Vite + TypeScript.
- Three screens: browse/filter, region detail, and the states in between
  (loading, empty, not-found).
- A 3D schematic viewer on the detail screen.
- A typed API client generated from `openapi.yaml`.

Out of scope for v1, each a deliberate omission: the 2-D world map
(`/v1/worlds/geometry`, `/v1/regions/at`); player-centric screens
(`/v1/players/*`, the owners leaderboard); authentication of any kind, since
the API is read-only and unauthenticated by design; and texture resource packs
for the renderer (see *Untextured first*).

## The move

`realty-rest` becomes `realty-web/realty-rest` via `git mv`, so history follows
the files. The Gradle path becomes `:realty-web:realty-rest`.

`realty-web/` is a plain container directory with no `build.gradle.kts` of its
own, exactly as `realty-paper-adapters/` already is. Gradle needs no project at
an intermediate path for `include("realty-web:realty-rest")` to work.

Five things outside the module name it and must change in the same commit, or
the build and the published artefacts break in ways CI would catch late:

| File | Change |
|---|---|
| `settings.gradle.kts` | `include("realty-rest")` → `include("realty-web:realty-rest")` |
| `.github/workflows/release-rest.yml` | `:realty-rest:shadowJar` → `:realty-web:realty-rest:shadowJar`, and the `realty-rest/build/libs/...` jar path |
| `realty-web/realty-rest/Dockerfile` | the `:realty-rest:shadowJar` invocation and the `COPY --from=build /workspace/realty-rest/build/libs/` path |
| `compose.yml`, `compose.local.yml` | `dockerfile: realty-rest/Dockerfile` → `realty-web/realty-rest/Dockerfile` |
| `realty-web/realty-rest/README.md`, `pterodactyl-egg.json` | any path references |

Both compose files keep `context: .`. The build context is the repository root
because `realty-rest` depends on `realty-backend` through Gradle project
references, and that is unchanged by the move — the Dockerfile comment already
explains this and stays true.

`archiveBaseName` is set explicitly to `realty-rest` in the module's
`shadowJar` block, so the produced jar keeps its name despite the deeper
project path. The release workflow's asset name does not change.

## realty-explorer

### Stack

React + Vite + TypeScript, built to static files. No SSR: the schematic viewer
is a Three.js/WebGL renderer with a WASM mesh pipeline, so nothing on the
critical path benefits from server rendering, and SSR would push the deployment
target from "any static host" to "a Node process".

### Screens

**Browse.** `GET /v1/regions/search`, exposing the filters the endpoint already
supports — type, world, price range, tag, occupancy, sort — with paging. This
is the entry point, so it must render usefully before any region is chosen.

**Region detail.** `GET /v1/region?world=&region=` for contract state, price,
dates and tags, plus the schematic viewer.

**In-between states.** A region with no captured schematic is the *normal*
case, not an error: capture is on demand, so most regions will have none until
someone runs the command. The detail screen shows the region's data with a
plain "no preview captured" panel where the viewer would be, and never an error
banner. A `404 SCHEMATIC_NOT_FOUND` is expected traffic.

### The API client — generated, not hand-written

`openapi-typescript` reads
`realty-web/realty-rest/src/main/resources/openapi.yaml` **directly**, a
sibling path in the monorepo, and emits `src/api/schema.d.ts`. There is no
publish step between spec and client, so the two cannot drift by accident of
release timing.

`openapi-fetch` provides the runtime client over those types:
`client.GET("/v1/region", { params: { query: { world, region } } })`. Adding an
endpoint to the spec makes it appear in the client at the next generate, which
is the property this project needs — more endpoints are coming.

**The generated file is committed**, and CI regenerates and fails on any diff.
This is what actually enforces the guarantee: changing `openapi.yaml` without
regenerating breaks the build rather than silently shipping a client that
misdescribes the API. Gradle declares `openapi.yaml` as an input to the
generate task, following the precedent already set in the REST module's own
`build.gradle.kts`, where `pterodactyl-egg.json` is declared an input to `test`
for the same reason: a file read from outside the classpath is invisible to
up-to-date checks unless it is declared, and a stale pass on the very file a
check exists to guard is worse than no check.

**A note on the spec version.** `openapi.yaml` declares `openapi: 3.0.3`,
although `2026-09-02-realty-rest-api-design.md` describes it as OpenAPI 3.1.
The document is wrong, not the file. This does not affect the choice —
`openapi-typescript` supports 3.0 and 3.1 alike — but the discrepancy is
recorded here rather than propagated.

**The one place generation does not protect us.** `openapi-fetch` does not
type-enforce `parseAs`; it is unconditionally optional
([openapi-ts#2633](https://github.com/openapi-ts/openapi-typescript/issues/2633)).
The schematic endpoint returns `application/octet-stream`, so it needs
`parseAs: "arrayBuffer"` explicitly, and omitting it fails at runtime rather
than at compile time. That call is therefore wrapped exactly once:

```ts
export const fetchSchematic =
  (world: string, region: string) => async (): Promise<ArrayBuffer> =>
    client.GET("/v1/region/schematic", {
      params: { query: { world, region } },
      parseAs: "arrayBuffer",
    }).then(r => r.data!);
```

Nothing else calls that endpoint, so the mistake can be made once and is then
fixed for good. The returned closure is already the shape `SchematicRenderer`
expects.

### The schematic viewer

`schematic-renderer` (npm), with `three` as a peer dependency. Its constructor
takes a map of id to async `ArrayBuffer` supplier, which is precisely what
`fetchSchematic` returns:

```ts
new SchematicRenderer(canvas, { [regionId]: fetchSchematic(world, regionId) },
                      {}, { showGrid: true });
```

The viewer is **lazy-loaded** behind a dynamic import, so the browse screen —
the entry point, and the one most visitors will see — does not pay for Three.js
and a WASM payload it never uses.

**Untextured first.** Resource packs are optional and v1 passes `{}`. The
consequence to accept: geometry renders without Minecraft block textures.
Shipping a resource pack means shipping copyrighted texture assets, which is a
licensing question rather than an engineering one, and is deliberately left for
a later decision rather than settled by default.

## Build integration

`com.github.node-gradle.node` (7.1.0), applied only in
`realty-web/realty-explorer/build.gradle.kts`. It provisions a pinned Node,
runs `npm ci`, and wires the frontend build into `assemble` and the frontend
tests into `check`, so `./gradlew build` covers the whole repository.

The cost, stated rather than discovered later: a Node toolchain download on
first build, and a slower `./gradlew build` for everyone — including someone
touching only the Paper plugin. This is accepted for the single-command
property. If it becomes a real irritation the frontend tasks can be put behind
a Gradle property without changing anything else in this design.

## Configuration and serving

The build produces static files, deployed to any static host. The browser calls
`realty-rest` cross-origin, using the **existing** `REALTY_REST_CORS_ORIGINS`
setting — the API needs no change to support this.

**Runtime configuration, not build-time.** The app fetches `/config.json`
before first render:

```json
{ "apiBaseUrl": "https://api.example.com" }
```

A container entrypoint writes that file from an environment variable at start.
One built artefact therefore works in development, staging and production — the
artefact that was tested is the artefact deployed, which a `VITE_`-baked base
URL cannot promise.

In development, Vite proxies `/v1` to a local `realty-rest`, so development has
no cross-origin traffic and needs no CORS configuration at all.

### Deployment shape: two Pterodactyl eggs, not one

`realty-rest` ships as a Pterodactyl egg, so the obvious question is whether one
egg could host both services. It could — and the answer is deliberately no.

A Pterodactyl egg is one server, one container, one **foreground** startup
command. The existing egg's contract shows where that binds: `config.stop` is
`^C`, which reaches the foreground process only, and `config.startup.done` is a
single readiness regex. Backgrounding a static file server beside the jar
therefore leaves it unreachable by the stop signal, invisible when it dies (the
panel still reports "running"), and outside the readiness marker — and the
`java_25` Yolk image ships a JRE and nothing else, so a file server would have
to be supplied as well.

The workable single-egg option is different: Javalin 7.2.3 can serve the bundle
itself via `staticFiles.add(dir, Location.EXTERNAL)` and
`spaRoot.addFile(...)`, giving one process, one port, and no CORS or
`config.json` at all, since everything is same-origin.

**Two eggs is chosen anyway**, keeping `realty-rest` a pure API and letting the
frontend deploy on its own cadence. The explorer can use an off-the-shelf
static/nginx egg rather than a custom one. The costs accepted: a second server
to operate, `REALTY_REST_CORS_ORIGINS` to configure, and the `config.json`
indirection.

This is recorded because the single-egg option is attractive on first look and
will be proposed again; the reasoning above is what to weigh it against, not a
claim that it cannot work.

## Testing

- **API client:** Vitest against a mocked fetch — parameter serialisation, and
  that `fetchSchematic` requests `arrayBuffer`, which is the failure the type
  system cannot catch.
- **Screens:** React Testing Library for browse and detail, including the
  no-schematic case rendering a panel rather than an error.
- **The renderer is mocked.** WebGL and WASM do not run under jsdom. The
  worthwhile assertion is that the viewer is constructed with the right canvas
  and loader, not that Three.js draws correctly — that belongs to a human
  looking at a screen.
- `npm test` runs under `./gradlew check` via the Node plugin.

## Alternatives considered

**Hand-written API types.** Rejected on the strength of "more endpoints will be
added soon": hand-written types drift silently, and the drift surfaces as a
runtime shape mismatch in a browser rather than a build failure.

**A generator producing a full client (`@hey-api/openapi-ts`, `orval`).**
Rejected as more machinery than needed: `openapi-typescript` emits types with
no runtime at all, and `openapi-fetch` is a thin typed wrapper over `fetch`.
Neither imposes a data-fetching library on the app.

**SvelteKit or Next.js.** Rejected: SSR is dead weight for a WebGL/WASM viewer,
and Next.js in particular pushes toward a Node hosting target when static files
suffice.

**Serving the SPA from `realty-rest` itself.** Rejected: it would make the API
process responsible for static assets, couple frontend releases to API
releases, and undo the deliberate separation in the REST design. CORS already
exists and costs nothing.

**Build-time API base URL.** Rejected: it means one build per environment, so
the artefact under test is never the artefact deployed.
