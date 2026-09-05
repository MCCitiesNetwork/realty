# Realty Web — Explorer, Dist and the `realty-web` Grouping — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group the web-facing projects under `realty-web/`, add a React + Vite + TypeScript explorer that browses regions and renders their captured schematics in 3D, and add a `realty-web-dist` jar that bundles API and front end into one deployable process.

**Architecture:** `realty-rest` moves to `realty-web/realty-rest` and gains one optional `StaticSite` seam. `realty-web/realty-explorer` is an npm project wired into Gradle by the Node plugin, with its API client generated from `openapi.yaml`. `realty-web/realty-web-dist` copies the explorer's build output into a shadow jar's resources and serves it from the classpath.

**Tech Stack:** Java 25, Gradle 9.4, Javalin 7.2.3, React 19, Vite, TypeScript, Vitest, `openapi-typescript` + `openapi-fetch`, `schematic-renderer` + `three`, `com.github.node-gradle.node` 7.1.0.

**Spec:** `docs/superpowers/specs/2026-09-04-realty-web-explorer-design.md`

## Global Constraints

- **Java:** no wildcard or static imports; no fully-qualified class names inline; `Assertions.assertEquals(...)` not a static import.
- **`realty-rest`'s default behaviour must not change.** Every existing test passes untouched, and a `StaticSite` of `null` means the service behaves exactly as it does today.
- **Never let the SPA fallback answer an API path.** `/v1/*` must return JSON errors with static serving on *and* off.
- **A missing or empty `apiBaseUrl` means same-origin** (`/v1` relative). One bundle serves both deployments; a second frontend build is a plan failure.
- **The generated API schema is committed**, and CI fails if regenerating changes it.
- **TypeScript is `strict`.** No `any` in checked-in code except where a third-party type demands it, and then with a comment saying why.
- **`CLAUDE.md` is git-ignored in this repo** (`.gitignore` line 125). Do not attempt to commit documentation there; repo-visible docs go in READMEs and `docs/`.
- Build/test commands: `./gradlew build`, `./gradlew :realty-web:realty-rest:test`, and inside the explorer `npm run test`, `npm run build`, `npm run typecheck`.
- Node 22 and npm 10 are present on the dev machine; the Node plugin pins its own copy for CI reproducibility regardless.

---

## Parallelism

Tasks 1 and 2 touch Java. Tasks 3–10 touch the explorer. They do not share files after Task 1.

```
Task 1  Move realty-rest                    [BLOCKING — nothing starts until this lands]
   │
   ├── Task 2   StaticSite seam (Java)  ─────────────────────────────┐
   │                                                                 │
   └── Task 3   Explorer scaffold + Gradle wiring                     │
          │                                                          │
          ├── Task 4   Codegen pipeline        ┐                     │
          └── Task 5   Runtime config loader   ┘ (4 ∥ 5)             │
                 │                                                   │
                 └── Task 6   API client + fetchSchematic             │
                        │                                            │
                        ├── Task 7   Browse screen      ┐            │
                        ├── Task 8   Region detail      │ (7 ∥ 8 ∥ 9)│
                        └── Task 9   Schematic viewer   ┘            │
                                │                                    │
                                └──────────► Task 10  realty-web-dist ◄┘
                                                   │
                                                   ├── Task 11  Dist egg + release
                                                   └── Task 12  Docs + whole build
```

**Safe to run concurrently:** (2, 3), then (4, 5), then (7, 8, 9). Everything else is sequential.

**Why 7, 8 and 9 do not collide:** each creates its own directory under `src/screens/` or `src/viewer/`, and the only shared file is the router, which Task 8 owns and Tasks 7 and 9 do not touch. Task 9 exports a component Task 8 already imports behind a lazy boundary written in Task 8.

---

## File Structure

**Moved**
- `realty-rest/` → `realty-web/realty-rest/` (whole tree, via `git mv`)

**`realty-web/realty-rest`** (modified)
- `src/main/java/.../rest/StaticSite.java` — the seam record.
- `src/main/java/.../rest/RealtyRestServer.java` — optional static serving.
- `src/main/java/.../rest/RestSettings.java`, `RestConfiguration.java` — `REALTY_REST_WEB_ROOT`.
- `src/test/java/.../rest/StaticSiteTest.java` — both modes.

**`realty-web/realty-explorer`** (new)
- `package.json`, `vite.config.ts`, `tsconfig.json`, `build.gradle.kts`
- `src/api/schema.d.ts` — generated, committed
- `src/api/client.ts` — `createClient` + `fetchSchematic`
- `src/config.ts` — runtime config with same-origin fallback
- `src/screens/browse/`, `src/screens/region/`
- `src/viewer/SchematicViewer.tsx` — lazy-loaded
- `src/router.tsx`, `src/main.tsx`

**`realty-web/realty-web-dist`** (new)
- `build.gradle.kts`, `src/main/java/.../dist/RealtyWebDistMain.java`
- `pterodactyl-egg.json`, `README.md`

---

### Task 1: Move `realty-rest` under `realty-web`

**BLOCKING.** Nothing else starts until this is committed.

**Files:**
- Move: `realty-rest/` → `realty-web/realty-rest/`
- Modify: `settings.gradle.kts`, `.github/workflows/release-rest.yml`, `compose.yml`, `compose.local.yml`, `realty-web/realty-rest/Dockerfile`

**Interfaces:**
- Consumes: nothing.
- Produces: the Gradle path `:realty-web:realty-rest`. Every later task uses it.

- [ ] **Step 1: Move the tree with history**

```bash
mkdir -p realty-web
git mv realty-rest realty-web/realty-rest
```

- [ ] **Step 2: Repoint Gradle**

In `settings.gradle.kts`, replace `include("realty-rest")` with `include("realty-web:realty-rest")`.

`realty-web/` needs no `build.gradle.kts` — Gradle does not require a project at an intermediate path, exactly as `realty-paper-adapters/` already demonstrates.

- [ ] **Step 3: Verify Gradle before touching anything else**

Run: `./gradlew :realty-web:realty-rest:test`
Expected: PASS. If the path is wrong Gradle fails fast with "project not found", which is why this runs before the remaining edits.

- [ ] **Step 4: Repoint the release workflow**

In `.github/workflows/release-rest.yml`:

```yaml
./gradlew --no-daemon :realty-web:realty-rest:shadowJar \
```

and the jar path:

```bash
JAR="realty-web/realty-rest/build/libs/realty-rest-${VERSION}-all.jar"
```

There is a second reference in the failure branch (`ls -1 realty-rest/build/libs/`) — update it too. The asset name is unchanged because `archiveBaseName` is set explicitly in the module's `shadowJar` block.

- [ ] **Step 5: Repoint the Dockerfile**

In `realty-web/realty-rest/Dockerfile`, two lines:

```dockerfile
RUN ./gradlew --no-daemon :realty-web:realty-rest:shadowJar
```

```dockerfile
COPY --from=build /workspace/realty-web/realty-rest/build/libs/*-all.jar app.jar
```

Its comment about copying the whole project stays true and correct — leave it.

- [ ] **Step 6: Repoint both compose files**

In `compose.yml` and `compose.local.yml`, `dockerfile: realty-rest/Dockerfile` becomes `dockerfile: realty-web/realty-rest/Dockerfile`. **`context: .` does not change** — the build context is the repo root because the module depends on `realty-backend` through project references.

- [ ] **Step 7: Sweep for stale paths**

```bash
grep -rn "realty-rest" --include=*.kts --include=*.yml --include=*.yaml \
  --include=Dockerfile --include=*.json --include=*.md . \
  | grep -v "\.claude/" | grep -v "/build/" | grep -v "realty-web/realty-rest"
```

Every remaining hit is either prose naming the service (fine) or a stale path (fix). `docs/superpowers/plans/2026-09-02-realty-rest.md` is a historical record of a completed plan — leave it alone; it describes the layout as it was.

- [ ] **Step 8: Verify the Docker build actually resolves**

Run: `docker build -f realty-web/realty-rest/Dockerfile -t realty-rest-move-check .`
Expected: builds through the Gradle stage. This is the only check that proves the Dockerfile's task path and COPY path are both right; the Gradle test in Step 3 does not exercise them.

- [ ] **Step 9: Full build**

Run: `./gradlew build`
Expected: PASS, 854 tests.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: move realty-rest under realty-web"
```

---

### Task 2: The `StaticSite` seam in `realty-rest`

Runs concurrently with Task 3. Touches only Java.

**Files:**
- Create: `realty-web/realty-rest/src/main/java/io/github/md5sha256/realty/rest/StaticSite.java`
- Modify: `RealtyRestServer.java`, `RestSettings.java`, `RestConfiguration.java`
- Test: `realty-web/realty-rest/src/test/java/io/github/md5sha256/realty/rest/StaticSiteTest.java`
- Modify: `realty-web/realty-rest/src/test/java/.../TestServers.java`

**Interfaces:**
- Consumes: Task 1's paths.
- Produces:
  - `public record StaticSite(@NotNull String directory, @NotNull Location location)`
  - `RealtyRestServer(RealtyBackend, Database, RestSettings, ModuleClient, @Nullable StaticSite)`
  - `RestSettings.webRoot()` returning `@Nullable String`
  - `TestServers.withStaticSite(Path dir)`
  - **Task 10 constructs `new StaticSite("/web", Location.CLASSPATH)`.**

- [ ] **Step 1: Write the failing test**

`StaticSiteTest.java`. `@TempDir` gives a real directory, so `Location.EXTERNAL` is exercised without shipping fixtures:

```java
package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class StaticSiteTest {

    @TempDir
    Path webRoot;

    @BeforeEach
    void writeIndex() throws IOException {
        Files.writeString(this.webRoot.resolve("index.html"), "<html>explorer</html>");
    }

    @Test
    void servesIndexAtTheRoot() {
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Response response = client.get("/");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("explorer"));
        });
    }

    @Test
    void servesIndexForAClientSideRoute() {
        // A deep link into the SPA must not 404: the router runs in the browser.
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Response response = client.get("/region/plot_a");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("explorer"));
        });
    }

    @Test
    void anUnknownApiPathStillReturnsJsonNotIndexHtml() {
        // The trap this whole seam has to avoid: spaRoot catches every unmatched
        // GET, so without skipFileFunction an API client asking for a bad endpoint
        // gets index.html and a 200.
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Response response = client.get("/v1/nope");
            Assertions.assertEquals(404, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"error\""), "expected a JSON error, got: " + body);
            Assertions.assertFalse(body.contains("explorer"), "index.html was served for an API path");
        });
    }

    @Test
    void aRealApiRouteStillWorks() {
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Assertions.assertEquals(200, client.get("/v1/health").code());
        });
    }

    @Test
    void withoutAStaticSiteTheRootIs404() {
        // The default must stay a pure API.
        JavalinTest.test(TestServers.withHealthyDatabase().javalin(), (app, client) -> {
            Assertions.assertEquals(404, client.get("/").code());
        });
    }
}
```

Add to `TestServers`:

```java
    static @NotNull RealtyRestServer withStaticSite(@NotNull java.nio.file.Path directory) {
        return new RealtyRestServer(stubBackend(), new StubDatabase(false), defaultSettings(),
                ModuleClient.disabled(),
                new StaticSite(directory.toAbsolutePath().toString(), Location.EXTERNAL));
    }
```

with imports for `StaticSite` and `io.javalin.http.staticfiles.Location`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :realty-web:realty-rest:test --tests "*StaticSiteTest*"`
Expected: FAIL — `StaticSite` and `withStaticSite` do not exist.

- [ ] **Step 3: Write the record**

```java
package io.github.md5sha256.realty.rest;

import io.javalin.http.staticfiles.Location;
import org.jetbrains.annotations.NotNull;

/**
 * A built front end for this service to serve alongside the API.
 *
 * <p>Optional by construction: {@code null} means the service is a pure API, which
 * is what the standalone deployment passes. {@code realty-web-dist} passes a
 * classpath site instead, which is the whole of the difference between the two
 * deployments.</p>
 *
 * @param directory the static root -- a filesystem path for {@link Location#EXTERNAL},
 *                  or a classpath resource path such as {@code /web}
 * @param location  whether {@code directory} is on disk or on the classpath
 */
public record StaticSite(@NotNull String directory, @NotNull Location location) {
}
```

- [ ] **Step 4: Wire it into `RealtyRestServer`**

Add a nullable field and a fifth constructor; the existing constructors delegate with `null` so no caller changes:

```java
    public RealtyRestServer(@NotNull RealtyBackend backend,
                            @NotNull Database database,
                            @NotNull RestSettings settings,
                            @NotNull ModuleClient moduleClient) {
        this(backend, database, settings, moduleClient, null);
    }

    public RealtyRestServer(@NotNull RealtyBackend backend,
                            @NotNull Database database,
                            @NotNull RestSettings settings,
                            @NotNull ModuleClient moduleClient,
                            @Nullable StaticSite staticSite) {
        this.backend = backend;
        this.database = database;
        this.settings = settings;
        this.moduleClient = moduleClient;
        this.staticSite = staticSite;
        this.worldLookup = new WorldLookup(database);
        this.javalin = buildJavalin();
    }
```

In `buildJavalin()`, after the CORS block and **before** `registerRoutes(config.routes)`:

```java
            if (this.staticSite != null) {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.directory = this.staticSite.directory();
                    staticFiles.location = this.staticSite.location();
                    // Without this, spaRoot below answers /v1/nope with index.html
                    // and a 200 -- correct-looking in a browser, wrong for every
                    // client that checks status codes.
                    staticFiles.skipFileFunction = request -> request.getRequestURI().startsWith("/v1");
                });
                config.spaRoot.addFile("/", this.staticSite.directory() + "/index.html",
                        this.staticSite.location());
            }
```

Add imports for `StaticSite` is unnecessary (same package); add `org.jetbrains.annotations.Nullable` if absent.

If `spaRoot` still answers `/v1/nope` with `index.html`, the fallback is registered ahead of route matching — in that case register an explicit `routes.get("/v1/<*>", ctx -> { throw ApiException.notFound(...); })` as the last route and keep the test as the arbiter.

- [ ] **Step 5: Add the environment variable**

`RestSettings` gains `@Nullable String webRoot` with javadoc in the file's existing style, stating that empty disables it and that this matches `corsOrigins` and `moduleUrl`.

`RestConfiguration` reads `REALTY_REST_WEB_ROOT`, treating empty as `null`, and includes it in the settings it logs at startup.

`RealtyRestMain` builds `new StaticSite(webRoot, Location.EXTERNAL)` when non-null, else passes `null`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :realty-web:realty-rest:test`
Expected: PASS — the five new tests plus every existing one, unchanged.

- [ ] **Step 7: Commit**

```bash
git add realty-web/realty-rest
git commit -m "feat(rest): optionally serve a static front end"
```

---

### Task 3: Explorer scaffold and Gradle wiring

Runs concurrently with Task 2. Touches only the new directory plus `settings.gradle.kts`.

**Files:**
- Create: `realty-web/realty-explorer/{package.json,tsconfig.json,vite.config.ts,index.html,build.gradle.kts,.gitignore}`
- Create: `realty-web/realty-explorer/src/main.tsx`, `src/App.tsx`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: Task 1's layout.
- Produces:
  - npm scripts `dev`, `build`, `test`, `typecheck`, `generate:api`
  - Gradle project `:realty-web:realty-explorer` with `npmBuild` wired into `assemble`, `npmTest` into `check`
  - **build output at `realty-web/realty-explorer/dist/` — Task 10 copies from there**

- [ ] **Step 1: Scaffold**

```bash
cd realty-web
npm create vite@latest realty-explorer -- --template react-ts
cd realty-explorer
npm install
npm install openapi-fetch three schematic-renderer
npm install -D openapi-typescript vitest @testing-library/react @testing-library/jest-dom jsdom @types/three
```

- [ ] **Step 2: Add the scripts**

In `package.json`:

```json
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "typecheck": "tsc --noEmit",
    "generate:api": "openapi-typescript ../realty-rest/src/main/resources/openapi.yaml -o src/api/schema.d.ts"
  }
```

The relative path is the point: the client is generated from the spec in the sibling module, with no publish step between them.

- [ ] **Step 3: Configure Vite**

`vite.config.ts` — the dev proxy is what keeps development same-origin:

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/v1": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
  },
});
```

Create `src/test-setup.ts` containing `import "@testing-library/jest-dom";`.

- [ ] **Step 4: Ignore build output**

`realty-web/realty-explorer/.gitignore`:

```
node_modules/
dist/
```

`src/api/schema.d.ts` is **not** ignored — it is committed deliberately.

- [ ] **Step 5: Wire Gradle**

`realty-web/realty-explorer/build.gradle.kts`:

```kotlin
plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    // Pinned so CI does not drift with whatever the runner happens to ship.
    version.set("22.18.0")
    download.set(true)
}

val npmBuild by tasks.registering(com.github.gradle.node.npm.task.NpmTask::class) {
    dependsOn(tasks.named("npmInstall"))
    npmCommand.set(listOf("run", "build"))
    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("vite.config.ts")
    // The spec lives in a sibling module; without declaring it, editing the API
    // and rebuilding would reuse a stale client.
    inputs.file("../realty-rest/src/main/resources/openapi.yaml")
    outputs.dir("dist")
}

val npmTest by tasks.registering(com.github.gradle.node.npm.task.NpmTask::class) {
    dependsOn(tasks.named("npmInstall"))
    npmCommand.set(listOf("run", "test"))
    inputs.dir("src")
    outputs.upToDateWhen { false }
}

tasks.named("assemble") { dependsOn(npmBuild) }
tasks.named("check") { dependsOn(npmTest) }
```

In `settings.gradle.kts`, add `include("realty-web:realty-explorer")`.

- [ ] **Step 6: Verify Gradle drives npm**

Run: `./gradlew :realty-web:realty-explorer:build`
Expected: downloads Node, installs, builds, produces `realty-web/realty-explorer/dist/index.html`.

```bash
ls realty-web/realty-explorer/dist/index.html
```

- [ ] **Step 7: Verify the whole build still works**

Run: `./gradlew build`
Expected: PASS, and the frontend build runs as part of it.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts realty-web/realty-explorer
git commit -m "feat(explorer): scaffold the React explorer and wire it into Gradle"
```

---

### Task 4: Codegen pipeline

Concurrent with Task 5.

**Files:**
- Create: `realty-web/realty-explorer/src/api/schema.d.ts` (generated, committed)
- Modify: `.github/workflows/build.yml`

**Interfaces:**
- Consumes: Task 3's `generate:api` script.
- Produces: `import type { paths } from "./schema";` — **Task 6 depends on this type name.**

- [ ] **Step 1: Generate**

```bash
cd realty-web/realty-explorer && npm run generate:api
```

- [ ] **Step 2: Confirm the endpoints landed**

```bash
grep -c "/v1/region/schematic" src/api/schema.d.ts
grep -c "/v1/regions/search" src/api/schema.d.ts
```

Expected: at least 1 each. If either is 0 the generator did not see the spec — check the relative path before continuing.

- [ ] **Step 3: Add the CI drift check**

In `.github/workflows/build.yml`, after the existing build job's steps, add a job:

```yaml
  api-client-drift:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - name: Install
        working-directory: realty-web/realty-explorer
        run: npm ci
      - name: Regenerate the API client
        working-directory: realty-web/realty-explorer
        run: npm run generate:api
      - name: Fail if the committed client is stale
        run: |
          if ! git diff --exit-code -- realty-web/realty-explorer/src/api/schema.d.ts; then
            echo "::error::openapi.yaml changed without regenerating the client."
            echo "Run: cd realty-web/realty-explorer && npm run generate:api"
            exit 1
          fi
```

This is the whole point of committing the generated file: without this job, committing it is just noise.

- [ ] **Step 4: Prove the check works**

Temporarily add a dummy path to `openapi.yaml`, run `git diff --exit-code -- realty-web/realty-explorer/src/api/schema.d.ts` after regenerating, and confirm it exits non-zero. Revert the dummy path.

A check nobody has seen fail is a check nobody knows works.

- [ ] **Step 5: Commit**

```bash
git add realty-web/realty-explorer/src/api/schema.d.ts .github/workflows/build.yml
git commit -m "feat(explorer): generate the API client from openapi.yaml"
```

---

### Task 5: Runtime configuration loader

Concurrent with Task 4. Shares no files with it.

**Files:**
- Create: `realty-web/realty-explorer/src/config.ts`
- Test: `realty-web/realty-explorer/src/config.test.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `export async function loadConfig(): Promise<AppConfig>` and `export type AppConfig = { apiBaseUrl: string }`. **Task 6 calls `loadConfig`.**

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it, vi, afterEach } from "vitest";
import { loadConfig } from "./config";

const mockFetch = (impl: () => Promise<Response>) => {
  vi.stubGlobal("fetch", vi.fn(impl));
};

afterEach(() => vi.unstubAllGlobals());

describe("loadConfig", () => {
  it("uses apiBaseUrl from config.json when present", async () => {
    mockFetch(async () =>
      new Response(JSON.stringify({ apiBaseUrl: "https://api.example.com" }), { status: 200 }));
    expect((await loadConfig()).apiBaseUrl).toBe("https://api.example.com");
  });

  it("falls back to same-origin when config.json is missing", async () => {
    // The dist deployment ships no config.json at all: a 404 here is expected
    // traffic, not an error, and must not surface as one.
    mockFetch(async () => new Response("", { status: 404 }));
    expect((await loadConfig()).apiBaseUrl).toBe("");
  });

  it("falls back to same-origin when apiBaseUrl is empty", async () => {
    mockFetch(async () => new Response(JSON.stringify({ apiBaseUrl: "" }), { status: 200 }));
    expect((await loadConfig()).apiBaseUrl).toBe("");
  });

  it("falls back to same-origin when config.json is not valid JSON", async () => {
    mockFetch(async () => new Response("<html>404</html>", { status: 200 }));
    expect((await loadConfig()).apiBaseUrl).toBe("");
  });

  it("falls back to same-origin when the request throws", async () => {
    mockFetch(async () => { throw new Error("network down"); });
    expect((await loadConfig()).apiBaseUrl).toBe("");
  });

  it("strips a trailing slash so paths concatenate cleanly", async () => {
    mockFetch(async () =>
      new Response(JSON.stringify({ apiBaseUrl: "https://api.example.com/" }), { status: 200 }));
    expect((await loadConfig()).apiBaseUrl).toBe("https://api.example.com");
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test` in `realty-web/realty-explorer`
Expected: FAIL — `./config` does not exist.

- [ ] **Step 3: Implement**

```ts
export type AppConfig = {
  /** Absolute API origin, or "" meaning same-origin (requests go to a relative /v1). */
  apiBaseUrl: string;
};

const SAME_ORIGIN: AppConfig = { apiBaseUrl: "" };

/**
 * Reads /config.json if it is there.
 *
 * Every failure resolves to same-origin rather than throwing: the bundled
 * (realty-web-dist) deployment ships no config.json at all, so a 404 is the
 * normal case for it, and one build has to serve both deployments.
 */
export async function loadConfig(): Promise<AppConfig> {
  try {
    const response = await fetch("/config.json", { cache: "no-store" });
    if (!response.ok) return SAME_ORIGIN;
    const parsed = (await response.json()) as Partial<AppConfig>;
    const base = typeof parsed.apiBaseUrl === "string" ? parsed.apiBaseUrl.trim() : "";
    return { apiBaseUrl: base.replace(/\/+$/, "") };
  } catch {
    return SAME_ORIGIN;
  }
}
```

- [ ] **Step 4: Run the tests**

Run: `npm run test`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add realty-web/realty-explorer/src/config.ts realty-web/realty-explorer/src/config.test.ts
git commit -m "feat(explorer): resolve the API base URL at runtime, same-origin by default"
```

---

### Task 6: API client and `fetchSchematic`

**Files:**
- Create: `realty-web/realty-explorer/src/api/client.ts`
- Test: `realty-web/realty-explorer/src/api/client.test.ts`

**Interfaces:**
- Consumes: `paths` (Task 4), `loadConfig` (Task 5).
- Produces:
  - `export function createApiClient(baseUrl: string)` returning an `openapi-fetch` client
  - `export function fetchSchematic(client, world, region): () => Promise<ArrayBuffer>`
  - **Tasks 7, 8, 9 consume these.**

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it, vi } from "vitest";
import { createApiClient, fetchSchematic } from "./client";

describe("fetchSchematic", () => {
  it("requests the response as an ArrayBuffer", async () => {
    // openapi-fetch does not type-enforce parseAs, so this assertion is the only
    // thing standing between us and a runtime failure on the binary endpoint.
    const bytes = new Uint8Array([1, 2, 3]).buffer;
    const fetchSpy = vi.fn(async () =>
      new Response(bytes, {
        status: 200,
        headers: { "Content-Type": "application/octet-stream" },
      }));

    const client = createApiClient("", fetchSpy as unknown as typeof fetch);
    const result = await fetchSchematic(client, "world", "plot_a")();

    expect(result).toBeInstanceOf(ArrayBuffer);
    expect(new Uint8Array(result)).toEqual(new Uint8Array([1, 2, 3]));
  });

  it("passes world and region as query parameters", async () => {
    const fetchSpy = vi.fn(async () => new Response(new ArrayBuffer(0), { status: 200 }));
    const client = createApiClient("", fetchSpy as unknown as typeof fetch);

    await fetchSchematic(client, "My World", "plot_a")();

    const url = String((fetchSpy.mock.calls[0][0] as Request).url ?? fetchSpy.mock.calls[0][0]);
    expect(url).toContain("world=My+World");
    expect(url).toContain("region=plot_a");
  });

  it("targets the configured base URL", async () => {
    const fetchSpy = vi.fn(async () => new Response(new ArrayBuffer(0), { status: 200 }));
    const client = createApiClient("https://api.example.com", fetchSpy as unknown as typeof fetch);

    await fetchSchematic(client, "world", "plot_a")();

    const url = String((fetchSpy.mock.calls[0][0] as Request).url ?? fetchSpy.mock.calls[0][0]);
    expect(url).toContain("https://api.example.com/v1/region/schematic");
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test`
Expected: FAIL — `./client` does not exist.

- [ ] **Step 3: Implement**

```ts
import createClient, { type Client } from "openapi-fetch";
import type { paths } from "./schema";

export type ApiClient = Client<paths>;

/**
 * @param baseUrl absolute API origin, or "" for same-origin (a relative /v1)
 * @param fetchImpl injected in tests; production uses the global fetch
 */
export function createApiClient(baseUrl: string, fetchImpl?: typeof fetch): ApiClient {
  return createClient<paths>({
    baseUrl: baseUrl || "/",
    ...(fetchImpl ? { fetch: fetchImpl } : {}),
  });
}

/**
 * Returns a loader for the region's schematic bytes.
 *
 * The `parseAs` is load-bearing and cannot be type-checked: openapi-fetch leaves
 * it unconditionally optional, so omitting it on this octet-stream endpoint fails
 * at runtime rather than at compile time. This is the only call site, so the
 * mistake is available exactly once.
 *
 * The returned closure is already the shape SchematicRenderer expects.
 */
export function fetchSchematic(
  client: ApiClient,
  world: string,
  region: string,
): () => Promise<ArrayBuffer> {
  return async () => {
    const { data, error } = await client.GET("/v1/region/schematic", {
      params: { query: { world, region } },
      parseAs: "arrayBuffer",
    });
    if (error || !data) {
      throw new Error(`No schematic for ${region} in ${world}`);
    }
    return data as ArrayBuffer;
  };
}
```

- [ ] **Step 4: Run the tests**

Run: `npm run test`
Expected: PASS, 3 new tests plus Task 5's 6.

- [ ] **Step 5: Commit**

```bash
git add realty-web/realty-explorer/src/api
git commit -m "feat(explorer): add the typed API client and schematic loader"
```

---

### Task 7: Browse screen

Concurrent with Tasks 8 and 9. Owns `src/screens/browse/` only.

**Files:**
- Create: `src/screens/browse/BrowseScreen.tsx`, `src/screens/browse/BrowseScreen.test.tsx`

**Interfaces:**
- Consumes: `ApiClient` (Task 6).
- Produces: `export function BrowseScreen({ client }: { client: ApiClient })`. **Task 8 routes to it.**

- [ ] **Step 1: Write the failing test**

Cover the three states that matter, with a stubbed client — no network:

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { BrowseScreen } from "./BrowseScreen";
import type { ApiClient } from "../../api/client";

const clientReturning = (body: unknown) =>
  ({ GET: vi.fn(async () => ({ data: body, error: undefined })) }) as unknown as ApiClient;

describe("BrowseScreen", () => {
  it("lists the regions it is given", async () => {
    const client = clientReturning({
      results: [{ region: "plot_a", world: { name: "world" }, state: "FOR_SALE" }],
      totalCount: 1,
    });
    render(<BrowseScreen client={client} />);
    await waitFor(() => expect(screen.getByText("plot_a")).toBeInTheDocument());
  });

  it("shows an empty state rather than a blank page when nothing matches", async () => {
    const client = clientReturning({ results: [], totalCount: 0 });
    render(<BrowseScreen client={client} />);
    await waitFor(() => expect(screen.getByText(/no regions/i)).toBeInTheDocument());
  });

  it("surfaces an error when the request fails", async () => {
    const client = ({ GET: vi.fn(async () => ({ data: undefined, error: { error: "boom" } })) })
      as unknown as ApiClient;
    render(<BrowseScreen client={client} />);
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test -- BrowseScreen`
Expected: FAIL — the module does not exist.

- [ ] **Step 3: Implement**

Write `BrowseScreen.tsx` calling `client.GET("/v1/regions/search", { params: { query: { page, pageSize } } })` in an effect, holding `loading | error | data` state, and rendering: a list of results linking to `/region/:world/:region`; an empty state reading "No regions match these filters."; and an error with `role="alert"`. Filters (type, world, price, tag, occupancy, sort) are controlled inputs feeding the same query.

Check the generated `schema.d.ts` for the exact response field names before writing the render — they come from the spec, not from this plan.

- [ ] **Step 4: Run the tests**

Run: `npm run test -- BrowseScreen`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add realty-web/realty-explorer/src/screens/browse
git commit -m "feat(explorer): add the region browse screen"
```

---

### Task 8: Region detail screen and router

Concurrent with Tasks 7 and 9. Owns `src/screens/region/`, `src/router.tsx`, `src/main.tsx`, `src/App.tsx`.

**Files:**
- Create: `src/screens/region/RegionScreen.tsx`, `src/screens/region/RegionScreen.test.tsx`, `src/router.tsx`
- Modify: `src/main.tsx`, `src/App.tsx`

**Interfaces:**
- Consumes: `ApiClient` (Task 6), `loadConfig` (Task 5), `BrowseScreen` (Task 7), and **lazily** `SchematicViewer` (Task 9).
- Produces: the app entry point.

> **Parallel-safety note.** This task writes the lazy import of Task 9's component. Write it exactly as below even if Task 9 has not landed yet — TypeScript will fail to resolve it until Task 9 exists, which is why Step 5 runs after both.

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { RegionScreen } from "./RegionScreen";
import type { ApiClient } from "../../api/client";

vi.mock("../../viewer/SchematicViewer", () => ({
  SchematicViewer: () => <div data-testid="viewer" />,
}));

const clientReturning = (body: unknown) =>
  ({ GET: vi.fn(async () => ({ data: body, error: undefined })) }) as unknown as ApiClient;

describe("RegionScreen", () => {
  it("renders the region's details", async () => {
    const client = clientReturning({ region: "plot_a", world: { name: "world" }, state: "FOR_SALE", tags: [] });
    render(<RegionScreen client={client} world="world" region="plot_a" hasSchematic={false} />);
    await waitFor(() => expect(screen.getByText("plot_a")).toBeInTheDocument());
  });

  it("shows a plain panel, not an error, when no schematic was captured", async () => {
    // Capture is on demand, so most regions have none. This is expected traffic.
    const client = clientReturning({ region: "plot_a", world: { name: "world" }, state: "FOR_SALE", tags: [] });
    render(<RegionScreen client={client} world="world" region="plot_a" hasSchematic={false} />);
    await waitFor(() => expect(screen.getByText(/no preview captured/i)).toBeInTheDocument());
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test -- RegionScreen`
Expected: FAIL.

- [ ] **Step 3: Implement the screen**

`RegionScreen.tsx` fetches `/v1/region`, renders contract state, price, dates and tags, and renders the viewer region:

```tsx
const SchematicViewer = lazy(() =>
  import("../../viewer/SchematicViewer").then(m => ({ default: m.SchematicViewer })));
```

wrapped in `<Suspense fallback={...}>`. The lazy boundary is what keeps Three.js and the WASM payload off the browse screen.

- [ ] **Step 4: Implement the router and entry point**

`router.tsx` maps `/` to `BrowseScreen` and `/region/:world/:region` to `RegionScreen`. `main.tsx` awaits `loadConfig()`, builds the client with `createApiClient(config.apiBaseUrl)`, and renders the router.

- [ ] **Step 5: Run the whole suite**

Run: `npm run test && npm run typecheck`
Expected: PASS. Typecheck is what catches a missing Task 9.

- [ ] **Step 6: Commit**

```bash
git add realty-web/realty-explorer/src
git commit -m "feat(explorer): add the region detail screen and router"
```

---

### Task 9: Schematic viewer

Concurrent with Tasks 7 and 8. Owns `src/viewer/` only.

**Files:**
- Create: `src/viewer/SchematicViewer.tsx`, `src/viewer/SchematicViewer.test.tsx`

**Interfaces:**
- Consumes: `fetchSchematic` (Task 6).
- Produces: `export function SchematicViewer({ client, world, region }: Props)`. **Task 8 lazy-imports this exact name from this exact path.**

- [ ] **Step 1: Write the failing test**

The renderer is mocked: WebGL and WASM do not run in jsdom, and the worthwhile assertion is that we construct it correctly.

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";

const constructorSpy = vi.fn();
vi.mock("schematic-renderer", () => ({
  SchematicRenderer: class {
    constructor(...args: unknown[]) { constructorSpy(...args); }
    dispose() {}
  },
}));

import { SchematicViewer } from "./SchematicViewer";
import type { ApiClient } from "../api/client";

const client = ({ GET: vi.fn(async () => ({ data: new ArrayBuffer(8), error: undefined })) })
  as unknown as ApiClient;

describe("SchematicViewer", () => {
  it("constructs the renderer with a canvas and a loader keyed by region", async () => {
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    const [canvas, schematics] = constructorSpy.mock.calls[0];
    expect(canvas).toBeInstanceOf(HTMLCanvasElement);
    expect(Object.keys(schematics as object)).toEqual(["plot_a"]);
    expect(typeof (schematics as Record<string, unknown>)["plot_a"]).toBe("function");
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test -- SchematicViewer`
Expected: FAIL.

- [ ] **Step 3: Implement**

```tsx
import { useEffect, useRef } from "react";
import { SchematicRenderer } from "schematic-renderer";
import { fetchSchematic, type ApiClient } from "../api/client";

type Props = { client: ApiClient; world: string; region: string };

export function SchematicViewer({ client, world, region }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!canvasRef.current) return;
    const renderer = new SchematicRenderer(
      canvasRef.current,
      { [region]: fetchSchematic(client, world, region) },
      {},                    // resource packs: none, so geometry renders untextured
      { showGrid: true },
    );
    return () => {
      // Without this, navigating between regions leaks a WebGL context per visit
      // and the browser eventually refuses to create more.
      (renderer as { dispose?: () => void }).dispose?.();
    };
  }, [client, world, region]);

  return <canvas ref={canvasRef} aria-label={`3D preview of ${region}`} />;
}
```

- [ ] **Step 4: Run the tests**

Run: `npm run test -- SchematicViewer`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add realty-web/realty-explorer/src/viewer
git commit -m "feat(explorer): add the lazy-loaded schematic viewer"
```

---

### Task 10: `realty-web-dist`

Needs Task 2 (the seam) and Tasks 3–9 (a real bundle to embed).

**Files:**
- Create: `realty-web/realty-web-dist/build.gradle.kts`
- Create: `realty-web/realty-web-dist/src/main/java/io/github/md5sha256/realty/dist/RealtyWebDistMain.java`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `StaticSite` (Task 2), `realty-web/realty-explorer/dist/` (Task 3).
- Produces: `realty-web-dist-<version>-all.jar`. **Task 11 releases it.**

- [ ] **Step 1: Create the project**

`realty-web/realty-web-dist/build.gradle.kts`:

```kotlin
plugins {
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    implementation(project(":realty-web:realty-rest"))
}

// The explorer's build output becomes jar resources under /web, so the single
// artifact carries the front end and Javalin can serve it from the classpath.
val explorerDist = project(":realty-web:realty-explorer").layout.projectDirectory.dir("dist")

val copyExplorer by tasks.registering(Copy::class) {
    dependsOn(":realty-web:realty-explorer:npmBuild")
    from(explorerDist)
    into(layout.buildDirectory.dir("explorer/web"))
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("explorer"))
}

tasks.named("processResources") { dependsOn(copyExplorer) }

tasks.shadowJar {
    archiveBaseName.set("realty-web-dist")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "io.github.md5sha256.realty.dist.RealtyWebDistMain"
    }
    mergeServiceFiles()
}
```

Add `include("realty-web:realty-web-dist")` to `settings.gradle.kts`.

- [ ] **Step 2: Write the main class**

It must reuse `realty-rest`'s configuration rather than reimplementing it — the only difference between the two deployments is the `StaticSite`. Read `RealtyRestMain` first and mirror its startup sequence exactly, substituting the server construction:

```java
package io.github.md5sha256.realty.dist;

import io.javalin.http.staticfiles.Location;
// ... the same imports RealtyRestMain uses

/**
 * Entry point for the bundled distribution: the same service as {@code realty-rest},
 * additionally serving the explorer from this jar's own resources.
 *
 * <p>The front end lives at {@code /web} on the classpath, so this is a single
 * artifact and a single process -- which is what lets it run under one Pterodactyl
 * egg, where two processes cannot be supervised.</p>
 */
public final class RealtyWebDistMain {

    private RealtyWebDistMain() {
    }

    public static void main(String[] args) {
        // identical to RealtyRestMain up to server construction, then:
        // new RealtyRestServer(backend, database, settings, moduleClient,
        //         new StaticSite("/web", Location.CLASSPATH));
    }
}
```

If `RealtyRestMain`'s startup sequence is not reusable as-is, extract the shared part into a package-private helper in `realty-rest` and call it from both — do not copy it. Two divergent startup sequences is exactly the failure this bundling is supposed to avoid.

- [ ] **Step 3: Build the jar**

Run: `./gradlew :realty-web:realty-web-dist:shadowJar`
Expected: PASS.

- [ ] **Step 4: Verify the front end is actually inside**

```bash
unzip -l realty-web/realty-web-dist/build/libs/realty-web-dist-*-all.jar | grep "web/index.html"
```

Expected: one match. A jar that builds but embeds nothing would otherwise pass every check until an operator ran it.

- [ ] **Step 5: Run it against the dev database**

```bash
./gradlew startDevDb
REALTY_DB_URL=mariadb://localhost:3306/realty \
REALTY_DB_USERNAME=realty REALTY_DB_PASSWORD=realty \
  java -jar realty-web/realty-web-dist/build/libs/realty-web-dist-*-all.jar
```

Then, in another shell:

```bash
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' http://localhost:8080/
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/v1/health
curl -s http://localhost:8080/v1/nope | head -c 100
```

Expected: `200 text/html` for `/`, `200` for health, and a **JSON** body for `/v1/nope` — not `index.html`. That last one is the trap; this is the only place it is checked end to end.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts realty-web/realty-web-dist
git commit -m "feat(dist): bundle the explorer and API into one jar"
```

---

### Task 11: Dist egg and release workflow

**Files:**
- Create: `realty-web/realty-web-dist/pterodactyl-egg.json`
- Modify: `.github/workflows/release-rest.yml`

- [ ] **Step 1: Write the egg**

Copy `realty-web/realty-rest/pterodactyl-egg.json` and change: `name` to "Realty Web (bundled)", the jar name to `realty-web-dist-${VERSION}-all.jar` in the install script, the startup command to run it, and the `REALTY_REST_VERSION` variable description to say it pins both API and front end. Keep the `done` marker unchanged — the same server logs the same line.

Do **not** add a `REALTY_REST_WEB_ROOT` variable here: the bundled build serves from the classpath and setting it would be meaningless.

- [ ] **Step 2: Release both jars**

In `release-rest.yml`, build and upload the dist jar alongside the API jar:

```yaml
./gradlew --no-daemon :realty-web:realty-rest:shadowJar \
                     :realty-web:realty-web-dist:shadowJar \
```

and add the second asset path. Note the dist build now requires Node — the workflow needs `actions/setup-node` or the Gradle Node plugin's download, whichever the job already has.

- [ ] **Step 3: Validate the egg parses**

```bash
python3 -c "import json; json.load(open('realty-web/realty-web-dist/pterodactyl-egg.json'))"
```

If `realty-rest` has a `PterodactylEggTest`, add the equivalent for this egg.

- [ ] **Step 4: Commit**

```bash
git add realty-web/realty-web-dist/pterodactyl-egg.json .github/workflows/release-rest.yml
git commit -m "build(dist): publish the bundled jar and its egg"
```

---

### Task 12: Documentation and whole-build verification

- [ ] **Step 1: Full build from clean**

Run: `./gradlew clean build`
Expected: PASS across every module, including the frontend.

- [ ] **Step 2: Write the deployment README**

`realty-web/README.md` covering both shapes: the split (two eggs, `REALTY_REST_CORS_ORIGINS`, `config.json`) and the bundled (one egg, neither). Include the nginx recipe for the split, since it removes CORS entirely and is the recommended way to run it:

```nginx
location /v1/ { proxy_pass http://realty-rest-host:8080/v1/; }
location /    { try_files $uri /index.html; }
```

- [ ] **Step 3: Update the REST README**

Document `REALTY_REST_WEB_ROOT` in `realty-web/realty-rest/README.md`'s variable table: empty disables, matching the other "empty disables" settings.

- [ ] **Step 4: Verify the docs claim nothing untrue**

Re-read both READMEs against the code. Every variable, path and command named must exist. **Do not add notes to `CLAUDE.md`** — it is git-ignored here, so anything written there is invisible to everyone else.

- [ ] **Step 5: Commit**

```bash
git add realty-web/README.md realty-web/realty-rest/README.md
git commit -m "docs: describe both realty-web deployment shapes"
```

---

## Self-Review

**Spec coverage:**

| Spec requirement | Task |
|---|---|
| Move to `realty-web/realty-rest`, history preserved | 1 |
| Gradle path, workflow, Dockerfile, compose updated | 1 |
| React + Vite + TS explorer | 3 |
| Browse screen | 7 |
| Region detail screen | 8 |
| No-schematic renders a panel, not an error | 8 |
| 3D viewer, lazy-loaded, untextured | 9 |
| Client generated from `openapi.yaml`, committed, CI drift check | 4 |
| `fetchSchematic` wraps `parseAs` once | 6 |
| Same-origin fallback when `apiBaseUrl` absent/empty | 5 |
| Gradle Node plugin, `assemble`/`check` wiring | 3 |
| `realty-web-dist` fat jar, SPA on classpath | 10 |
| `StaticSite` seam, null = pure API | 2 |
| `skipFileFunction` keeps `/v1` JSON | 2, and end-to-end in 10 |
| A test with static serving *on* | 2 |
| Dist egg, versioning | 11 |
| nginx recipe for the split deployment | 12 |

No spec requirement is unassigned.

**Placeholder scan:** Tasks 7, 8 and 10 direct the implementer to read a neighbouring file before finalising — the generated `schema.d.ts` for response field names, and `RealtyRestMain` for the startup sequence. That is a deliberate instruction to check a source of truth this plan should not duplicate (and would date), not a deferred decision: the behaviour required is fully specified in each case.

**Type consistency:** `ApiClient` is defined in Task 6 and consumed by 7, 8, 9 under that name. `SchematicViewer` is exported from `src/viewer/SchematicViewer.tsx` in Task 9 and lazy-imported from exactly that path in Task 8. `loadConfig`/`AppConfig` are defined in Task 5 and used in 6 and 8. `StaticSite(directory, location)` is defined in Task 2 and constructed in Task 10 as `new StaticSite("/web", Location.CLASSPATH)`. `npmBuild` is registered in Task 3 and depended on in Task 10.

**Parallel-safety:** the concurrent groups (2 ∥ 3), (4 ∥ 5) and (7 ∥ 8 ∥ 9) share no files. Task 8 is the only writer of `router.tsx`, `main.tsx` and `App.tsx`; Task 4 is the only writer of `schema.d.ts`; each screen task owns its own directory. Tasks 3 and 10 both edit `settings.gradle.kts`, but never concurrently — Task 10 is downstream of Task 3.
