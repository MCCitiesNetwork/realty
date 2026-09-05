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
- A new `realty-web/realty-web-dist`: a single jar bundling the API and the
  built front end, so an operator can deploy one process instead of two.

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

The build produces static files. Where they are served from is a deployment
choice — a static host, or the `realty-web-dist` jar — and the same bundle
covers both.

In the split deployment the browser calls `realty-rest` cross-origin, using the
**existing** `REALTY_REST_CORS_ORIGINS` setting, so the API needs no change to
support it. (If the static host reverse-proxies `/v1`, even that is
unnecessary; see *Deployment shape*.) In `realty-web-dist` everything is
same-origin and CORS never enters the picture.

**Runtime configuration, not build-time, and optional.** The app fetches
`/config.json` before first render:

```json
{ "apiBaseUrl": "https://api.example.com" }
```

A container entrypoint writes that file from an environment variable at start.
One built artefact therefore works in development, staging and production — the
artefact that was tested is the artefact deployed, which a `VITE_`-baked base
URL cannot promise.

**A missing or empty `apiBaseUrl` means "same origin", and requests go to a
relative `/v1`.** This is not a convenience: it is what lets *one* bundle serve
both deployment shapes. In `realty-web-dist` the API is same-origin by
construction, so no config file is written and none is needed; in the split
deployment the entrypoint writes one. Without this fallback the dist build
would need its own frontend variant, and the single-artefact property would be
lost.

A 404 on `/config.json` is therefore expected traffic, not an error, and must
not surface as one.

In development, Vite proxies `/v1` to a local `realty-rest`, so development has
no cross-origin traffic and needs no CORS configuration at all.

### `realty-web-dist` — the bundled single-process build

A third project, `realty-web/realty-web-dist`, produces one fat jar containing
the API and the built front end. It exists so the deployment shape is the
operator's choice rather than the architecture's:

| Deployment | Artifacts | Processes | CORS | `config.json` |
|---|---|---|---|---|
| Split | API jar + static bundle | 2 | required | required |
| **Dist** | one jar | 1 | none | none |

**How it bundles.** The explorer's Vite output is copied into the dist jar's
resources under `/web`, and served with `Location.CLASSPATH`. One artifact
means the egg's install script stays the shape it already has — download a jar,
run it — rather than growing a second asset to stage. Swapping the front end
means shipping a new jar, which is acceptable because the two are already
version-locked (see *Versioning*).

**The seam in `realty-rest`.** Serving static files needs a hook, because
`RealtyRestServer` owns the Javalin configuration. It gains one optional
constructor parameter:

```java
record StaticSite(@NotNull String directory, @NotNull Location location) {}
```

`null` means "pure API", and that is what `realty-rest`'s own main passes
unless `REALTY_REST_WEB_ROOT` is set — so the standalone service's behaviour is
byte-identical to today's, and the "empty disables" convention already used by
`REALTY_REST_CORS_ORIGINS` and `REALTY_REST_MODULE_URL` holds here too. The
dist main passes `new StaticSite("/web", Location.CLASSPATH)`.

That parameter is also what makes the split deployment able to serve a bundle
from disk if an operator wants it, without a second code path.

**The trap this must not fall into.** `spaRoot` catches every unmatched GET,
including `/v1/nope` — so a naive implementation returns `index.html` with a
`200` to an API client asking for a bad endpoint. That looks correct in a
browser and breaks every consumer that checks status codes.

Javalin's `StaticFileConfig.skipFileFunction` is the fix: static handling is
skipped for any request whose path starts with `/v1`, leaving the existing JSON
error handling intact.

`HealthEndpointTest.anUnknownPathReturnsAJsonErrorBody` already asserts
`/v1/nope` is a JSON `404` — but it runs with static serving **off**, so it
would keep passing while this was broken. The mitigation is therefore a
*second* test with static serving **on**, asserting the same thing. A guard
that cannot fail in the configuration it guards is not a guard.

**Versioning.** The dist jar carries the same version as `realty-rest`. The
existing egg already pins `REALTY_REST_VERSION` deliberately, because the
service refuses to boot unless the database schema matches exactly — so API and
front end already ship in lockstep, and bundling them changes nothing about
that.

**Build wiring.** `realty-web-dist` depends on `:realty-web:realty-rest` and on
the explorer's npm build output, copying it into resources before `shadowJar`.
Its `archiveBaseName` is set explicitly, as `realty-rest`'s already is, so the
release asset name does not follow the project path.

### Deployment shape: two eggs by default, one if you want it

Both shapes are supported, and the operator picks. What is *not* supported is
running two processes under one egg, which is worth writing down because it is
the first thing anyone tries.

**Why not two processes in one egg.** A Pterodactyl egg is one server, one
container, one **foreground** startup command. The existing egg's contract
shows where that binds: `config.stop` is `^C`, which reaches the foreground
process only, and `config.startup.done` is a single readiness regex.
Backgrounding a static file server beside the jar therefore leaves it
unreachable by the stop signal, invisible when it dies (the panel still reports
"running"), and outside the readiness marker — and the `java_25` Yolk image
ships a JRE and nothing else, so a file server would have to be supplied too.

`realty-web-dist` sidesteps all of that by being **one process**, not two in a
trench coat. One jar, one foreground command, one readiness marker, one
allocation — the egg is the existing one with a different asset name.

**Two eggs stays the default** for anyone who wants the front end to deploy on
its own cadence, or to sit on a CDN rather than a game-server host. That
deployment uses an off-the-shelf static or nginx egg for the explorer.

**A note on the split deployment's CORS.** If the static egg runs nginx, it can
reverse-proxy `/v1` through to `realty-rest`, which makes the browser see one
origin and removes the need for `REALTY_REST_CORS_ORIGINS` and `config.json`
entirely. That is a deployment recipe rather than a code path — nothing in
either project changes to enable it — so it belongs in the README rather than
here, but it is the recommended way to run the split.

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
- **Same-origin fallback:** that a missing or empty `apiBaseUrl` resolves to a
  relative `/v1`. This is the single `if` the whole one-bundle-two-deployments
  property rests on.
- **Static serving, in `realty-rest`'s own suite:** with a `StaticSite`
  configured, `/v1/nope` must still return a JSON `404` and `/` must return
  `index.html`. The existing unknown-path test runs with static serving off and
  would not catch a regression here.
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

**Serving the SPA from `realty-rest` unconditionally.** Rejected — but only the
*unconditionally*. Making the API always responsible for static assets would
undo the separation the REST design chose deliberately. `realty-web-dist`
instead makes it an opt-in packaging: the standalone service keeps its
behaviour byte-for-byte, and the bundling lives in a project whose whole
purpose is bundling.

**A second frontend build for the bundled deployment.** Rejected: the
same-origin fallback described under *Configuration* means one bundle serves
both shapes. Two builds would double the release surface to save one `if`.

**Two processes under one Pterodactyl egg.** Rejected on the panel's own terms
— see *Deployment shape*. `realty-web-dist` reaches the same goal by being one
process rather than by fighting the supervisor.

**Build-time API base URL.** Rejected: it means one build per environment, so
the artefact under test is never the artefact deployed.
