# Private Schematic Format Implementation Plan

> **For the orchestrating session:** read this whole file first, then follow **Execution Protocol** below. Tasks are written so that a subagent given only this file's path and a task number can complete that task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop `/realty schematic capture` from producing, storing or serving anything WorldEdit can load. The plugin writes a Realty-only format holding a hollowed-out, position-less copy of the region; the explorer decodes it in the browser.

**Architecture:** `realty-paper` turns the captured clipboard into a `BlockGrid`, removes every block that cannot be seen from outside, and encodes the rest in a private binary layout. `realty-backend` stores those bytes unchanged in the existing `LONGBLOB`, and a migration deletes every capture made in the old format. `realty-rest` serves the bytes unchanged but refuses any row that lacks the Realty header. `realty-explorer` decodes the bytes and rebuilds an in-memory schematic for the renderer it already uses.

**Tech Stack:** Java 25, Gradle, WorldEdit 7.4.0 clipboard API, MyBatis + MariaDB, Javalin, JUnit 5. TypeScript, React, Vitest, `schematic-renderer` 1.6, `nucleation` 0.2 (test only).

**Decisions already made by the owner. Do not reopen them.**

| Decision | Choice |
|---|---|
| Keep a restorable full copy anywhere | **No.** Captures are never restored. |
| Rooms behind glass | Hidden. Glass does not let the view pass into the air behind it. |
| Existing captures | Deleted by migration. Regions are recaptured in game. |
| Rate limiting, login gate, server-side rendering | Out of scope for this plan. |

**What this does and does not achieve.** A saved response is meaningless to WorldEdit, Litematica and every existing converter. The decoder ships in the site's JavaScript, so a determined person can still recover blocks. What they recover is a facade: no interior, no world position, leaves that decay when pasted. Write nothing in code comments or docs that claims more than this.

---

## Verified Before Writing

Checked on 2026-09-28. These are facts, not assumptions.

**The code in this plan has been run.** Every Java and TypeScript block in Tasks 1 to 5 and Task 9 was extracted from this file and executed outside the repository.

| What | Result |
|---|---|
| Java classes and tests from Tasks 1 to 5, on the plugin's own test classpath | 44 tests, 44 pass |
| TypeScript decoder and tests from Task 9, under Vitest 3.2 | 20 tests, 20 pass |
| TypeScript decoder under `tsc --noEmit` with the explorer's `tsconfig.json` | no errors |

**What that run did not cover.** Tasks 6, 7, 8, 10 and 11 edit existing files and were not executed. Their code blocks are written against the files as read on this date.

Other facts established:

- The renderer's parser, `nucleation` 0.2.18, loads a Sponge v3 schematic rebuilt in JavaScript, **if it is gzipped**. Uncompressed NBT fails with `invalid gzip header`.
- A block entity written as `{Pos, Id, Data: {id}}` parses and keeps its id, so chests and signs still draw.
- `nucleation` initialises under Vitest in the `node` environment with a plain `await init()`.
- `DeflaterOutputStream` on the Java side and `DecompressionStream("deflate")` on the browser side agree. Both mean zlib framing, RFC 1950.
- The plugin's test classpath resolves **WorldEdit 7.4.0**, not the 7.3.18 an earlier plan names. Every WorldEdit method this plan calls exists in both.
- Stripping runs **once, at capture time**. Nothing is stripped per request: the REST service serves the stored bytes unchanged. On a synthetic 100x100x100 region, reading, culling and encoding took under 100 ms in total, and the browser decoded and rebuilt it in about 30 ms.
- `gh stack add -m "..." <branch>` commits only what is staged. Unstaged edits and untracked files stay in the working tree and travel to the new branch. Tested in a throwaway repository. `gh stack submit` was not tested, because it pushes.
- A JVM that has booted `WorldEditTestPlatform` does not exit by itself, because WorldEdit leaves threads running. Gradle's test worker is unaffected. It matters only if you run tests by hand.

---

## Wire Format, Version 1

All integers are big-endian. `text` is an unsigned 16-bit byte length followed by that many UTF-8 bytes. `varint` is unsigned LEB128.

```
offset 0   4 bytes   magic: 0x52 0x4C 0x54 0x59  ("RLTY")
offset 4   1 byte    version: 0x01
offset 5   ...       zlib stream (RFC 1950) containing the body

body:
  int32   dataVersion          Minecraft data version the states were read under
  int32   width                x extent
  int32   height               y extent
  int32   length               z extent
  int32   paletteSize          at least 1; entry 0 is always minecraft:air
  paletteSize x { text state ; text blockEntityId }     blockEntityId is "" for none
  int32   runCount
  runCount x { varint paletteIndex ; varint runLength }
```

Cells are listed **x outermost, then z, then y innermost**. The cell at `(x, y, z)` is number `(x * length + z) * height + y`. Sponge uses y, z, x; the difference is deliberate. Run lengths must sum to exactly `width * height * length`.

There is no offset, origin or world coordinate anywhere in the format. Coordinates are relative to the capture's own minimum corner.

**Golden fixture.** These bytes are a 3x2x2 grid, data version 4325, produced by the prototype encoder. Both test suites decode them. Base64:

```
UkxUWQF4nFWNQQrCMBBFJ426E72G0BMIOYkUGcOkCbaJZGbTA/ceHUWILv5iHu//ATivAGA13Tc7OM4pk68Y5IqpKjo1wFIyKXINFXzeWdTkW0Cf8uhyqRL7iFNwjyJS5p4jvsixVExjlEEHLm3AR2L578ry1vWeaPh9/1G1vTcGbGfgYM0G3807YQ==
```

Expected decode:

| Field | Value |
|---|---|
| dataVersion | 4325 |
| width, height, length | 3, 2, 2 |
| palette 0 | `minecraft:air`, no block entity |
| palette 1 | `minecraft:stone`, no block entity |
| palette 2 | `minecraft:oak_stairs[facing=north,half=bottom,shape=straight]`, no block entity |
| palette 3 | `minecraft:chest[facing=north,type=single]`, block entity `minecraft:chest` |
| cells | `[1,0,0,0,2,0,0,0,0,0,0,3]` |

So stone is at `(0,0,0)`, the stairs at `(1,0,0)` and the chest at `(2,1,1)`.

---

## What The Review Changed

The independent review in wave 5 found that the work as first built did not meet its purpose. These were fixed before anything was pushed. Where this section and a task below disagree, this section and the code are right.

| Finding | Fix |
|---|---|
| The view ran on through any chain of see-through blocks. A carpeted floor, a fence post or a flooded room carried it through a sealed building. | A see-through block now shows the one block directly behind it and the view stops there. |
| Every cell on the bottom of the box was kept. A house level with the ground kept its whole floor and anything set into it. | The bottom of the box is no longer a way in. The view starts on the four sides and the top. The preview's camera is held level with the plot or above it. |
| Occlusion was read per block type, from its default state. A double slab was read as a half slab. | `Occlusion.hides` recognises a double slab by its own state. |
| The first lookup of a block type's material writes to an unsynchronised map inside WorldEdit, and it was happening on a database thread. | `Occlusion.learnEveryMaterial` runs on the main thread before each capture. |
| No test would fail if the command stopped culling. | The command makes one call, `CaptureEncoding.encode`, and `CaptureEncodingTest` makes the same call. |
| A 58-byte response cost the browser 26 seconds and 2.5 GB. A compressed body was inflated in full before any check. | The decoder stops inflating at 64 MiB, refuses more than 100,000 block entities, refuses a name longer than the target format can write, and `realtyToRenderable` throws only `UnreadableSchematicError`. |
| Water was a see-through block like any other, so a pond would have shown one block of water and no bed. | Water and lava carry the view as air does. `BlockGrid.PaletteEntry` holds a `Sight` of `OPEN`, `SEE_THROUGH` or `SOLID` in place of a boolean. |
| A test searched compressed bytes for the word "Offset" and could not fail. | Removed. `CaptureEncodingTest` encodes one build at two places in the world and requires identical bytes. |
| Docs and comments claimed more than the code did. | Corrected. |

**Found and not fixed, because they are not part of this work. The owner has been told.**

- A region that covers only the inside of a room is captured whole. It has no outside.
- A region's bounds are served by `GET /v1/region`, so where a capture stood can be worked out.
- A polygonal region is captured as its bounding box, which takes in blocks of neighbouring plots.
- A fence or a row of iron bars hides what is behind it as glass does, so a roofed gazebo loses its contents in the preview.

## Shell Cull Rule

> Superseded in part. See **What The Review Changed**. The rule as built: the view starts on the four sides and the top; open cells, meaning air, water and lava, pass it on in every direction; every block it touches is kept; a kept see-through block also keeps the one block directly behind it, and that block passes nothing on.

A block is kept only if it can be seen from outside the capture box.

1. Every cell on any of the six faces of the box is **reached**.
2. A reached **air** cell reaches all six neighbours.
3. A reached **see-through block** reaches its neighbouring **blocks**, never its neighbouring air. See-through means not occluding: glass, leaves, stairs, slabs, fences, torches, flowers, chests, water.
4. A reached **occluding block** reaches nothing.
5. Every unreached cell becomes air. The palette is then rebuilt from the cells that remain, so a block type that was only inside never appears in the output.

Rule 3 exists because of a bug the prototype caught. Without it the grass under every flower and the roof under every stair was deleted, because its only exposed face was covered by a block that hides nothing. Rule 3 also gives the owner's glass decision: a bookshelf directly behind a window survives, the room behind it does not.

Rule 5's palette rebuild is a security requirement, not tidiness. A palette that still lists `minecraft:spawner` tells the reader what was inside.

---

## Global Constraints

- **No new Java dependency.** WorldEdit resolves through the existing `compileOnly` WorldGuard coordinate.
- **No wildcard imports, no static imports, no fully-qualified names inline.** Use `Assertions.assertEquals(...)`.
- **SQL uses Java text blocks** where it lives in Java. Migration files are plain `.sql`.
- **A migration is inert until registered** in `MariaSchemaMigrator.DEFAULT_MIGRATIONS`, and `SchemaVersionCheck.EXPECTED_VERSION` must match the highest one.
- **World reads stay on the main thread.** Everything this plan adds runs on a filled clipboard, touches no world, and belongs on the database executor where the old writer ran.
- **Do not name anything `async`.** The capture is tick-sliced, not asynchronous.
- **Do not add a setting, flag or permission** that turns the cull off or restores the old format.
- **Do not keep `RegionSchematicWriter`** or any other path that can produce Sponge bytes on the server.
- **Comments state what the code does and why.** No comment may call the format encryption or claim it cannot be reversed.
- **Match the surrounding style:** `final` utility classes with a private constructor, `@NotNull` and `@Nullable` from `org.jetbrains.annotations`, Javadoc that explains the reason for a choice.
- **Subagents run no `git` command that changes anything, and no `gh` command at all.** The orchestrator owns every commit, branch and pull request.
- **The work ships as a stack of pull requests made with `gh stack`.** See **Pull Request Stack**. Do not use `git checkout -b`, `git push` or `gh pr create` in its place.
- **`realty-backend` database tests need Docker.** If Docker is not running, say so in the report. Do not describe a skipped test as passing.
- **Node 26 locally, Node 22 in CI.** Two existing tests fail on Node 26 only, both with `blob.text is not a function`: `client.test.ts > returns the pack when the server configures one` and `resourcePacks.test.ts > fetches every pack and keeps the server's order`. They are the recorded baseline and are not caused by this work. Any other failure is.
- **Two `realty-rest` tests fail before any change:** `StaticSiteTest > withNoConfigOnDiskThePackagedOneIsStillServed` and `StaticSiteTest > aFrontEndsOwnConfigJsonIsServedFromDisk`. They are the recorded baseline, recorded on 2026-09-28, and are not caused by this work. Do not try to fix them.
- Test commands: `./gradlew :realty-backend:test`, `./gradlew :realty-paper:test`, `./gradlew :realty-web:realty-rest:test`, `./gradlew build`. In `realty-web/realty-explorer`: `npm test`, `npm run typecheck`.

---

## File Structure

**`realty-backend-api`**
- Create `src/main/java/io/github/md5sha256/realty/api/RealtySchematicFormat.java`. The header bytes and the one check that recognises them. Lives here because both the plugin and the REST service already depend on this module.
- Modify `src/main/java/io/github/md5sha256/realty/database/entity/RealtySchematicEntity.java`. Javadoc only.

**`realty-backend`**
- Create `src/main/resources/sql/migrations/V18__purge_worldedit_schematics.sql`.
- Modify `src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java`. Register V18.
- Create `src/test/java/io/github/md5sha256/realty/api/RealtySchematicFormatTest.java`. Here because `realty-backend-api` has no test source set.

**`realty-paper`**, all under `src/main/java/io/github/md5sha256/realty/schematic/`
- Create `BlockGrid.java`. Dimensions, palette and cells. No WorldEdit types.
- Create `VisualState.java`. Drops block-state properties that never change how a block looks.
- Create `ShellCull.java`. The rule above.
- Create `RealtySchematicEncoder.java`. `BlockGrid` to bytes.
- Create `ClipboardGrids.java`. The only new class that touches WorldEdit.
- Delete `RegionSchematicWriter.java`.
- Modify `src/main/java/io/github/md5sha256/realty/command/SchematicCommandGroup.java`. The `persist` method and its callers.
- Create tests beside the existing ones in `src/test/java/io/github/md5sha256/realty/schematic/`: `BlockGridTest`, `VisualStateTest`, `ShellCullTest`, `RealtySchematicEncoderTest`, `ClipboardGridsTest`, and the helper `RealtySchematicTestDecoder`.

**`realty-web/realty-rest`**
- Modify `src/main/java/io/github/md5sha256/realty/rest/RegionSchematicHandler.java`.
- Modify `src/main/java/io/github/md5sha256/realty/rest/SchemaVersionCheck.java`. Bump to 18.
- Modify `src/main/resources/openapi.yaml`.
- Modify `src/test/java/io/github/md5sha256/realty/rest/RegionSchematicEndpointTest.java`.

**`realty-web/realty-explorer`**
- Create `src/api/realtySchematic.ts` and `src/api/realtySchematic.test.ts`.
- Modify `src/viewer/SchematicViewer.tsx` and `src/viewer/SchematicViewer.test.tsx`.
- Modify `src/screens/region/RegionScreen.tsx` and `src/screens/region/RegionScreen.test.tsx`.
- Modify `package.json` and `package-lock.json`. One dev dependency.
- Regenerate `src/api/schema.d.ts`.

**Docs**
- Modify `README.md`, `realty-web/README.md`, `realty-web/realty-rest/README.md`.

---

## Execution Protocol

This section is for the orchestrating session.

### Pull Request Stack

The work is delivered as six stacked pull requests, made with the `gh stack` extension. Version 0.1.0 of `github/gh-stack` is installed, on `gh` 2.100.0. Each pull request builds on the one below it.

| Layer | Branch | Tasks | Pull request title |
|---|---|---|---|
| 1 | `feat/schematic-format-core` | plan file, 1, 2, 3, 4, 5 | `feat(schematic): add Realty's own capture format` |
| 2 | `feat/schematic-format-decoder` | 9 | `feat(explorer): decode captures in Realty's format` |
| 3 | `feat/schematic-format-capture` | 6 | `feat(schematic): capture a hollow shell, not a WorldEdit file` |
| 4 | `feat/schematic-format-gate` | 7, 8 | `feat(rest): delete old captures and serve only Realty's format` |
| 5 | `feat/schematic-format-viewer` | 10 | `feat(explorer): render captures from Realty's format` |
| 6 | `feat/schematic-format-docs` | 11 | `docs: describe what a capture now holds` |

The layers are in the order the work finishes, so every commit is made at the top of the stack and nothing is rebased during normal execution.

**How the stack is built**

- Layer 1 is created with `gh stack init feat/schematic-format-core`.
- Every later layer is created with `gh stack add <branch>`, run while on the layer below it.
- `gh stack add` carries uncommitted changes onto the new branch. That is what lets two subagents finish in one wave and their work land in two layers.
- Within a layer, commit once per task with plain `git add <files>` and `git commit`. Stage files by name, never with `-A` or `.`, because the working tree holds files that belong to a later layer.
- End each commit message with the attribution trailer the session specifies.

**Which files belong to which layer**

After a wave, the working tree holds the work of more than one layer. Sort it with the **Files** list at the top of each task. A file not named in any task's list does not get committed. Stop and find out why it changed.

**Pull request descriptions**

Keep them plain. Each description is the one short paragraph given below, as written.

- No headings, checklists, tables, emoji or badges.
- No test plan section and no summary section.
- No attribution or "generated with" footer.
- No links back to this plan.

| Layer | Description |
|---|---|
| 1 | Adds the format Realty will store captures in, the cull that removes blocks not visible from outside, and the encoder. Nothing uses them yet. |
| 2 | Adds the explorer's decoder for the capture format. Nothing uses it yet. |
| 3 | The capture command now stores a hollow, position-less copy in Realty's format. The WorldEdit schematic writer is removed. |
| 4 | Deletes captures stored as WorldEdit schematics, and makes the REST service refuse any row that is not in Realty's format. Bumps the schema version to 18. |
| 5 | The region page decodes captures before drawing them, and shows the no-preview panel for one it cannot read. |
| 6 | Updates the READMEs and the OpenAPI description to say what a capture holds. |

**Commands that are never run**

- `gh stack merge`. Merging is the owner's decision.
- `gh stack unstack`. It deletes the stack on GitHub.
- `gh stack modify`. It is interactive.
- `gh stack submit --open`. Pull requests stay drafts until the owner says otherwise.

### Before starting

- [ ] Confirm the branch is `main`. `git status --short` should show this plan file as untracked and nothing else. If anything else is modified, stop and ask the owner.
- [ ] Confirm the tool is there: `gh stack --version`. If it is missing, stop and tell the owner. Do not fall back to plain branches.
- [ ] Confirm `gh auth status` shows the active account the owner expects. Two accounts are logged in on this machine.
- [ ] Create layer 1: `gh stack init feat/schematic-format-core`.
- [ ] Commit this plan file alone: `docs: add the private schematic format plan`.
- [ ] Run `./gradlew :realty-paper:test :realty-web:realty-rest:test` and, in `realty-web/realty-explorer`, `npm test`. Record what fails **before** any change. Those failures are the baseline.

### Waves

Two subagents may run at once only when one works in Gradle and the other in npm. Two subagents in the same Gradle build compile each other's half-written files.

| Wave | Java subagent | Explorer subagent | Layers committed afterwards |
|---|---|---|---|
| 1 | Tasks 1, 2, 3, 4, 5 in order | Task 9 | 1, then 2 |
| 2 | Tasks 6, 7, 8 in order | Task 10 | 3, then 4, then 5 |
| 3 | Task 11, one subagent | | 6 |
| 4 | Task 12, orchestrator itself | | none |
| 5 | Independent review, one subagent | | fixes only |
| 6 | Submit the stack, orchestrator itself | | none |

Launch both subagents of a wave in one message so they run concurrently. Use the general-purpose agent type.

### Subagent prompt template

Give every implementing subagent this, with the bracketed parts filled in:

```
You are implementing part of a written plan in the repository at /Users/jerek/GitHub/realty.

Read the whole plan first: docs/superpowers/plans/2026-09-28-private-schematic-format.md

Then carry out [Task N, Task M, ...] in that order, and nothing else. Follow each task's
steps in sequence, including the steps that run a test and expect it to fail.

Rules:
- Obey the plan's Global Constraints section.
- The code blocks in the plan were prototyped and are the intended implementation. Use them.
  If one does not compile or a test the plan says should pass does not, fix the smallest
  thing that makes it correct and record exactly what you changed and why.
- Do not change the wire format, the shell cull rule, or anything in the table of decisions.
  If you believe one of them is wrong, stop and say so in your report.
- Do not run git commit, push, checkout, switch, stash, rebase or reset. Do not run gh.
- Do not edit files that belong to tasks you were not given.

Report back with:
1. Every file you created, modified or deleted.
2. For each task, the exact test command you ran and the last 30 lines of its output.
3. Every place you departed from the plan's code, with the reason.
4. Anything you could not verify, stated plainly.
```

### After each wave

- [ ] Read each report. A report that says tests pass is a claim. Run the task's test commands yourself and compare.
- [ ] Run `git status --short` and `git diff --stat`. Confirm only files listed in **File Structure** changed.
- [ ] Commit in layer order, lowest first. For the layer you are on, commit once per task using the message given in that task. For the next layer, stage its first task's files by name and run `gh stack add -m "<that task's commit message>" <branch>`, which creates the branch and makes the commit. Then commit the layer's remaining tasks with plain `git commit`.
- [ ] After committing, `git status --short` must be empty. Anything left over was not planned for.
- [ ] Run `gh stack view --short` and confirm the layers so far appear in order.
- [ ] If a task failed verification, send the same subagent the failing output with SendMessage. Do not start the next wave on top of a failing one.

### Wave 5: independent review

Launch one subagent that did none of the implementation:

```
Review the branch feat/schematic-format-docs in /Users/jerek/GitHub/realty against
main. It is the top of a stack of six branches, so it holds all of the work. The plan it implements is docs/superpowers/plans/2026-09-28-private-schematic-format.md.
Read the plan, then read `git diff main...HEAD`.

You are looking for ways the work fails its purpose. Check each of these and answer every one:
1. Can any code path on the server still produce, store or serve Sponge, Litematica or any
   other WorldEdit-loadable bytes?
2. Does any served byte reveal world coordinates?
3. After the shell cull, can a block type that existed only in the interior still appear in
   the palette?
4. Does the Java encoder's byte layout match the TypeScript decoder's, field by field?
5. Can a malformed or hostile response make the decoder allocate unbounded memory or loop?
6. Is there a test that would fail if the cull were removed? If the header check in the REST
   handler were removed?
7. Does any comment or doc overstate the protection?

Report findings most severe first, each with file, line and a concrete failing scenario.
Report "nothing found" for a check only if you actually examined it. Change no files.
```

Fix confirmed findings in the layer that owns the file, not at the top of the stack:

- [ ] `gh stack checkout <the layer's branch>`.
- [ ] Make the fix and commit it as its own commit.
- [ ] `gh stack rebase --upstack --no-trunk` to carry it into the layers above.
- [ ] If the rebase stops on a conflict, resolve it and run `gh stack rebase --continue`. If you cannot resolve it with confidence, run `gh stack rebase --abort` and report.
- [ ] `gh stack top`, then rerun Task 12.

### Wave 6: submit the stack

Only after Task 12 passes and the review's findings are dealt with.

- [ ] `gh stack view --short`. Expect six branches, none marked as needing a rebase.
- [ ] `gh stack submit --auto`. This pushes all six branches and opens six draft pull requests, linked as a stack.
- [ ] `gh stack view --json` to get each pull request's number.
- [ ] For each pull request, set the title and description from the tables in **Pull Request Stack**:

```bash
gh pr edit <number> --title "<title from the table>" --body "<description from the table>"
```

- [ ] `gh stack view --short` once more, and open one pull request in the browser to confirm the stack is shown on it.

If `gh stack submit` fails part way, run it again. It creates what is missing and leaves what exists. Do not switch to `git push` and `gh pr create`.

### Finishing

Leave the pull requests as drafts and do not merge. Report to the owner:

- The six pull request links, bottom of the stack first.
- The baseline failures against the final failures.
- Everything in Task 12's list of what could not be verified.
- Any place a subagent departed from the plan.

---

### Task 1: The format marker

**Files:**
- Create: `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtySchematicFormat.java`
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/api/RealtySchematicFormatTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `RealtySchematicFormat.VERSION`, `RealtySchematicFormat.HEADER_LENGTH`, `RealtySchematicFormat.header()`, `RealtySchematicFormat.isReadable(byte[])`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RealtySchematicFormatTest {

    @Test
    void theHeaderIsTheMagicThenTheVersion() {
        Assertions.assertArrayEquals(new byte[]{'R', 'L', 'T', 'Y', 1}, RealtySchematicFormat.header());
        Assertions.assertEquals(5, RealtySchematicFormat.HEADER_LENGTH);
    }

    @Test
    void theHeaderIsACopySoACallerCannotCorruptIt() {
        RealtySchematicFormat.header()[0] = 0;
        Assertions.assertEquals('R', RealtySchematicFormat.header()[0]);
    }

    @Test
    void bytesThatStartWithTheHeaderAreReadable() {
        Assertions.assertTrue(RealtySchematicFormat.isReadable(new byte[]{'R', 'L', 'T', 'Y', 1, 9, 9}));
    }

    @Test
    void aWorldEditSchematicIsNotReadable() {
        // Sponge schematics are gzipped NBT, so they open with the gzip magic.
        Assertions.assertFalse(RealtySchematicFormat.isReadable(new byte[]{0x1f, (byte) 0x8b, 8, 0, 0, 0}));
    }

    @Test
    void aVersionThisBuildDoesNotKnowIsNotReadable() {
        Assertions.assertFalse(RealtySchematicFormat.isReadable(new byte[]{'R', 'L', 'T', 'Y', 2, 9}));
    }

    @Test
    void nullEmptyAndTruncatedBytesAreNotReadable() {
        Assertions.assertFalse(RealtySchematicFormat.isReadable(null));
        Assertions.assertFalse(RealtySchematicFormat.isReadable(new byte[0]));
        Assertions.assertFalse(RealtySchematicFormat.isReadable(new byte[]{'R', 'L', 'T', 'Y'}));
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew :realty-backend:test --tests "*RealtySchematicFormatTest*"`
Expected: compilation failure, `RealtySchematicFormat` does not exist.

- [ ] **Step 3: Write the class**

```java
package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The first bytes of every schematic Realty stores, and the check that recognises them.
 *
 * <p>Captures are written in a layout of Realty's own rather than as a WorldEdit
 * schematic, so that a file saved from the public endpoint loads in no existing tool.
 * The plugin writes this header, and the REST service refuses to serve a row without
 * it. That refusal is what keeps a capture from an older plugin, still sitting in a
 * shared database, from being handed out as a WorldEdit file.</p>
 *
 * <p>This is a format, not a secret. The decoder ships to every browser.</p>
 */
public final class RealtySchematicFormat {

    /** The layout version this build writes and reads. */
    public static final int VERSION = 1;

    private static final byte[] MAGIC = {'R', 'L', 'T', 'Y'};

    /** The magic plus one version byte. The compressed body starts here. */
    public static final int HEADER_LENGTH = MAGIC.length + 1;

    private RealtySchematicFormat() {
    }

    /** A fresh copy of the header, so no caller can alter the one every other caller gets. */
    public static byte @NotNull [] header() {
        byte[] header = Arrays.copyOf(MAGIC, HEADER_LENGTH);
        header[MAGIC.length] = (byte) VERSION;
        return header;
    }

    /**
     * Whether {@code data} opens with the header of a version this build understands.
     *
     * <p>Says nothing about the body. A row that passes here can still be corrupt, and
     * the decoder is what finds that out.</p>
     */
    public static boolean isReadable(byte @Nullable [] data) {
        if (data == null || data.length < HEADER_LENGTH) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return data[MAGIC.length] == VERSION;
    }
}
```

- [ ] **Step 4: Run the test and see it pass**

Run: `./gradlew :realty-backend:test --tests "*RealtySchematicFormatTest*"`
Expected: 6 tests pass. This test needs no database and no Docker.

**Commit message:** `feat(api): add the Realty schematic format marker`

---

### Task 2: The block grid and visual states

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/BlockGrid.java`
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/VisualState.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/BlockGridTest.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/VisualStateTest.java`

**Interfaces:**
- Produces: `BlockGrid(int width, int height, int length, List<PaletteEntry> palette, int[] cells)`, `BlockGrid.index(x, y, z)`, `BlockGrid.AIR`, `BlockGrid.PaletteEntry(String state, String blockEntityId, boolean occluding)`, `BlockGrid.PaletteEntry.AIR`, `VisualState.reduce(String)`.

- [ ] **Step 1: Write the failing tests**

`BlockGridTest.java`:

```java
package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class BlockGridTest {

    private static final List<BlockGrid.PaletteEntry> AIR_ONLY = List.of(BlockGrid.PaletteEntry.AIR);

    @Test
    void cellsRunXOutermostThenZThenYInnermost() {
        BlockGrid grid = new BlockGrid(3, 2, 2, AIR_ONLY, new int[12]);
        Assertions.assertEquals(0, grid.index(0, 0, 0));
        Assertions.assertEquals(1, grid.index(0, 1, 0));
        Assertions.assertEquals(2, grid.index(0, 0, 1));
        Assertions.assertEquals(4, grid.index(1, 0, 0));
        Assertions.assertEquals(11, grid.index(2, 1, 1));
    }

    @Test
    void refusesCellsThatDoNotMatchTheDimensions() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BlockGrid(3, 2, 2, AIR_ONLY, new int[11]));
    }

    @Test
    void refusesAPaletteThatDoesNotStartWithAir() {
        List<BlockGrid.PaletteEntry> stoneFirst =
                List.of(new BlockGrid.PaletteEntry("minecraft:stone", "", true));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BlockGrid(1, 1, 1, stoneFirst, new int[1]));
    }

    @Test
    void refusesAnEmptyBox() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BlockGrid(0, 1, 1, AIR_ONLY, new int[0]));
    }
}
```

`VisualStateTest.java`:

```java
package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VisualStateTest {

    @Test
    void aBlockWithNoPropertiesIsUnchanged() {
        Assertions.assertEquals("minecraft:stone", VisualState.reduce("minecraft:stone"));
    }

    @Test
    void propertiesThatShapeTheModelAreKept() {
        String stairs = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]";
        Assertions.assertEquals(stairs, VisualState.reduce(stairs));
    }

    @Test
    void leavesLoseWhatKeepsThemAlive() {
        // Pasted back, these leaves fall to the defaults and decay.
        Assertions.assertEquals("minecraft:oak_leaves",
                VisualState.reduce("minecraft:oak_leaves[distance=1,persistent=true]"));
    }

    @Test
    void droppingSomePropertiesKeepsTheRestInOrder() {
        Assertions.assertEquals("minecraft:scaffolding[bottom=true]",
                VisualState.reduce("minecraft:scaffolding[bottom=true,distance=3]"));
    }

    @Test
    void poweredIsDroppedOnlyWhereItChangesNothingVisible() {
        Assertions.assertEquals("minecraft:oak_door[facing=east,half=lower,hinge=left,open=false]",
                VisualState.reduce(
                        "minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=true]"));
        Assertions.assertEquals("minecraft:oak_trapdoor[facing=east,half=top,open=true]",
                VisualState.reduce("minecraft:oak_trapdoor[facing=east,half=top,open=true,powered=true]"));
        Assertions.assertEquals("minecraft:oak_fence_gate[facing=east,in_wall=false,open=false]",
                VisualState.reduce(
                        "minecraft:oak_fence_gate[facing=east,in_wall=false,open=false,powered=true]"));
    }

    @Test
    void poweredIsKeptWhereItIsTheModel() {
        // A lever, a button and a powered rail each draw differently when powered.
        String lever = "minecraft:lever[face=wall,facing=north,powered=true]";
        Assertions.assertEquals(lever, VisualState.reduce(lever));
        String rail = "minecraft:powered_rail[powered=true,shape=north_south]";
        Assertions.assertEquals(rail, VisualState.reduce(rail));
    }

    @Test
    void noteBlocksAreLeftAloneBecauseResourcePacksRetextureThem() {
        // Custom-block plugins map textures onto note block states, so dropping these
        // would draw the wrong block.
        String note = "minecraft:note_block[instrument=harp,note=3,powered=false]";
        Assertions.assertEquals(note, VisualState.reduce(note));
    }

    @Test
    void waterloggedIsKeptBecauseTheWaterIsDrawn() {
        String slab = "minecraft:stone_slab[type=bottom,waterlogged=true]";
        Assertions.assertEquals(slab, VisualState.reduce(slab));
    }

    @Test
    void triggeredIsDroppedOnDispensersButNotOnCrafters() {
        Assertions.assertEquals("minecraft:dispenser[facing=north]",
                VisualState.reduce("minecraft:dispenser[facing=north,triggered=true]"));
        String crafter = "minecraft:crafter[crafting=false,orientation=north_up,triggered=true]";
        Assertions.assertEquals(crafter, VisualState.reduce(crafter));
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew :realty-paper:test --tests "*BlockGridTest*" --tests "*VisualStateTest*"`
Expected: compilation failure.

- [ ] **Step 3: Write `BlockGrid`**

```java
package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A captured volume as plain numbers: a box of cells, each naming an entry in a palette.
 *
 * <p>Holds no WorldEdit type and no world coordinate. The cull and the encoder work on
 * this, which is what lets both be tested without a server, and what guarantees that a
 * position in the world has nowhere to travel.</p>
 *
 * <p>Cells run x outermost, then z, then y innermost. That is the order they are written
 * in, so encoding is one pass over the array.</p>
 *
 * @param width   extent along x
 * @param height  extent along y
 * @param length  extent along z
 * @param palette every distinct block in the grid; entry 0 is always air
 * @param cells   one palette index per cell
 */
public record BlockGrid(int width,
                        int height,
                        int length,
                        @NotNull List<PaletteEntry> palette,
                        int @NotNull [] cells) {

    /** The palette index of air, in every grid. */
    public static final int AIR = 0;

    /**
     * One distinct block.
     *
     * @param state         the block and the properties that shape it, as WorldEdit prints them
     * @param blockEntityId the block entity's id, or the empty string for none
     * @param occluding     whether the block hides what is behind it; used by the cull, never written
     */
    public record PaletteEntry(@NotNull String state, @NotNull String blockEntityId, boolean occluding) {

        public static final PaletteEntry AIR = new PaletteEntry("minecraft:air", "", false);
    }

    public BlockGrid {
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException(
                    "A grid needs a positive size, got " + width + "x" + height + "x" + length);
        }
        if (cells.length != Math.multiplyExact(Math.multiplyExact(width, height), length)) {
            throw new IllegalArgumentException(
                    cells.length + " cells cannot fill " + width + "x" + height + "x" + length);
        }
        if (palette.isEmpty() || !PaletteEntry.AIR.equals(palette.getFirst())) {
            throw new IllegalArgumentException("Palette entry 0 must be air");
        }
        palette = List.copyOf(palette);
    }

    /** Where the cell at these grid coordinates sits in {@link #cells()}. */
    public int index(int x, int y, int z) {
        return (x * this.length + z) * this.height + y;
    }
}
```

- [ ] **Step 4: Write `VisualState`**

```java
package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.StringJoiner;

/**
 * Drops the block-state properties that never change how a block is drawn.
 *
 * <p>A preview needs a stair's facing and a door's hinge. It does not need to know that
 * a leaf block was placed by a player, or that a door has a redstone signal. Leaving
 * those out costs the preview nothing, and a build pasted from a capture comes back
 * with leaves that decay and mechanisms in their resting state.</p>
 *
 * <p>The list is short on purpose. A property is here only if it changes no vanilla
 * model and no resource pack is known to key on it. Note blocks are left untouched for
 * that second reason: custom-block plugins hang their textures on note block states.
 * {@code waterlogged} is kept because the water is drawn.</p>
 */
public final class VisualState {

    /** Never visible, on any block that has them. */
    private static final Set<String> NEVER_DRAWN =
            Set.of("persistent", "distance", "occupied", "stage", "unstable", "enabled");

    private VisualState() {
    }

    /**
     * @param blockState a state as WorldEdit prints it, such as
     *                   {@code minecraft:oak_leaves[distance=1,persistent=true]}
     * @return the same state without the properties that are never drawn; the brackets
     *         go too when nothing is left inside them
     */
    public static @NotNull String reduce(@NotNull String blockState) {
        int open = blockState.indexOf('[');
        if (open < 0 || !blockState.endsWith("]")) {
            return blockState;
        }
        String id = blockState.substring(0, open);
        StringJoiner kept = new StringJoiner(",", "[", "]");
        kept.setEmptyValue("");
        for (String property : blockState.substring(open + 1, blockState.length() - 1).split(",")) {
            int equals = property.indexOf('=');
            String name = equals < 0 ? property : property.substring(0, equals);
            if (!isDropped(id, name)) {
                kept.add(property);
            }
        }
        return id + kept;
    }

    private static boolean isDropped(@NotNull String id, @NotNull String name) {
        if (NEVER_DRAWN.contains(name)) {
            return true;
        }
        // A lever, a button and a rail are drawn from "powered"; these three are not.
        if (name.equals("powered")) {
            return id.endsWith("_door") || id.endsWith("_trapdoor") || id.endsWith("_fence_gate");
        }
        // A crafter's face changes when triggered. A dispenser's and a dropper's do not.
        if (name.equals("triggered")) {
            return id.equals("minecraft:dispenser") || id.equals("minecraft:dropper");
        }
        return false;
    }
}
```

- [ ] **Step 5: Run the tests and see them pass**

Run: `./gradlew :realty-paper:test --tests "*BlockGridTest*" --tests "*VisualStateTest*"`
Expected: 4 and 9 tests pass.

**Commit message:** `feat(schematic): add the block grid and visual state reduction`

---

### Task 3: The shell cull

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/ShellCull.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/ShellCullTest.java`

**Interfaces:**
- Consumes: `BlockGrid` from Task 2.
- Produces: `ShellCull.hollow(BlockGrid)` returning a new `BlockGrid`.

- [ ] **Step 1: Write the failing test**

Every test builds on one house: a 7x6x7 box holding a stone cube from 1 to 5 on x and z and 0 to 4 on y, walls one block thick, air inside.

```java
package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class ShellCullTest {

    private static final int STONE = 1;
    private static final int CHEST = 2;
    private static final int SPAWNER = 3;
    private static final int GLASS = 4;
    private static final int STAIRS = 5;

    private static final List<BlockGrid.PaletteEntry> PALETTE = List.of(
            BlockGrid.PaletteEntry.AIR,
            new BlockGrid.PaletteEntry("minecraft:stone", "", true),
            new BlockGrid.PaletteEntry("minecraft:chest[facing=north,type=single]", "minecraft:chest", false),
            new BlockGrid.PaletteEntry("minecraft:spawner", "minecraft:mob_spawner", false),
            new BlockGrid.PaletteEntry("minecraft:glass", "", false),
            new BlockGrid.PaletteEntry("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]", "", false));

    /** A sealed stone house with one-block walls, standing in open air. */
    private static BlockGrid house() {
        BlockGrid grid = new BlockGrid(7, 6, 7, PALETTE, new int[7 * 6 * 7]);
        for (int x = 1; x <= 5; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 1; z <= 5; z++) {
                    boolean wall = x == 1 || x == 5 || y == 0 || y == 4 || z == 1 || z == 5;
                    if (wall) {
                        grid.cells()[grid.index(x, y, z)] = STONE;
                    }
                }
            }
        }
        return grid;
    }

    private static void put(BlockGrid grid, int x, int y, int z, int block) {
        grid.cells()[grid.index(x, y, z)] = block;
    }

    private static String at(BlockGrid grid, int x, int y, int z) {
        return grid.palette().get(grid.cells()[grid.index(x, y, z)]).state();
    }

    private static List<String> states(BlockGrid grid) {
        return grid.palette().stream().map(BlockGrid.PaletteEntry::state).toList();
    }

    @Test
    void whatIsInsideASealedBuildingIsRemoved() {
        BlockGrid grid = house();
        put(grid, 3, 1, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:air", at(hollow, 3, 1, 3));
    }

    @Test
    void theWallsAndRoofAreKept() {
        BlockGrid hollow = ShellCull.hollow(house());

        Assertions.assertEquals("minecraft:stone", at(hollow, 1, 2, 3));
        Assertions.assertEquals("minecraft:stone", at(hollow, 3, 4, 3));
        Assertions.assertEquals("minecraft:stone", at(hollow, 3, 0, 3));
    }

    @Test
    void aBlockTypeFoundOnlyInsideLeavesThePalette() {
        // The palette is served. Listing a spawner would say what the walls were hiding.
        BlockGrid grid = house();
        put(grid, 2, 1, 2, SPAWNER);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertFalse(states(hollow).contains("minecraft:spawner"));
        Assertions.assertEquals(List.of("minecraft:air", "minecraft:stone"), states(hollow));
    }

    @Test
    void aBlockUnderSomethingThatHidesNothingIsKept() {
        // The stair covers the roof block's only exposed face and occludes none of it.
        // Culling on touch alone left a hole under every stair, torch and flower.
        BlockGrid grid = house();
        put(grid, 3, 5, 3, STAIRS);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:stone", at(hollow, 3, 4, 3));
        Assertions.assertTrue(at(hollow, 3, 5, 3).startsWith("minecraft:oak_stairs"));
    }

    @Test
    void glassShowsTheBlockBehindItAndNothingFurtherIn() {
        BlockGrid grid = house();
        put(grid, 1, 2, 3, GLASS);
        put(grid, 2, 2, 3, CHEST);
        put(grid, 4, 2, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:glass", at(hollow, 1, 2, 3));
        Assertions.assertTrue(at(hollow, 2, 2, 3).startsWith("minecraft:chest"),
                "the block directly behind the window is visible through it");
        Assertions.assertEquals("minecraft:air", at(hollow, 4, 2, 3),
                "the room beyond is not");
    }

    @Test
    void anOpenDoorwayLetsTheViewIn() {
        BlockGrid grid = house();
        put(grid, 1, 1, 3, BlockGrid.AIR);
        put(grid, 1, 2, 3, BlockGrid.AIR);
        put(grid, 4, 1, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 4, 1, 3).startsWith("minecraft:chest"));
    }

    @Test
    void aSolidMassKeepsOnlyItsSkin() {
        BlockGrid grid = new BlockGrid(5, 5, 5, PALETTE, new int[125]);
        Arrays.fill(grid.cells(), STONE);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:air", at(hollow, 2, 2, 2));
        Assertions.assertEquals("minecraft:air", at(hollow, 1, 1, 1));
        Assertions.assertEquals("minecraft:stone", at(hollow, 0, 2, 2));
        Assertions.assertEquals("minecraft:stone", at(hollow, 2, 0, 2));
    }

    @Test
    void anEmptyBoxStaysEmpty() {
        BlockGrid hollow = ShellCull.hollow(new BlockGrid(2, 2, 2, PALETTE, new int[8]));

        Assertions.assertEquals(List.of("minecraft:air"), states(hollow));
        Assertions.assertArrayEquals(new int[8], hollow.cells());
    }

    @Test
    void theInputIsNotAltered() {
        BlockGrid grid = house();
        put(grid, 3, 1, 3, CHEST);
        int[] before = grid.cells().clone();

        ShellCull.hollow(grid);

        Assertions.assertArrayEquals(before, grid.cells());
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew :realty-paper:test --tests "*ShellCullTest*"`
Expected: compilation failure.

- [ ] **Step 3: Write `ShellCull`**

```java
package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Removes every block that cannot be seen from outside the captured box.
 *
 * <p>The preview's camera is held outside the plot, so nothing removed here was ever
 * going to be drawn. What it changes is the bytes: a capture is served from a public
 * endpoint, and a build pasted back from one is a shell with nothing in it.</p>
 *
 * <p>The view starts on all six faces of the box and spreads inward. Open air carries it
 * in every direction. A block that hides nothing -- glass, a stair, a torch, a leaf --
 * carries it into the blocks beside it, but never into the air beside it. So the wall
 * behind a torch is kept and the ground under a flower is kept, while the room behind a
 * window is not: the view reaches the block pressed against the glass and stops.</p>
 */
public final class ShellCull {

    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private ShellCull() {
    }

    /**
     * @return a new grid holding only what is visible, with a palette rebuilt from what
     *         remains. The palette is rebuilt because it is served too, and a block type
     *         that survives only in the palette still says what was inside.
     */
    public static @NotNull BlockGrid hollow(@NotNull BlockGrid grid) {
        boolean[] reached = reach(grid);
        int[] cells = grid.cells();
        List<BlockGrid.PaletteEntry> palette = grid.palette();

        int[] renumbered = new int[palette.size()];
        Arrays.fill(renumbered, -1);
        renumbered[BlockGrid.AIR] = BlockGrid.AIR;
        List<BlockGrid.PaletteEntry> kept = new ArrayList<>();
        kept.add(palette.get(BlockGrid.AIR));

        int[] visible = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int block = reached[i] ? cells[i] : BlockGrid.AIR;
            if (renumbered[block] < 0) {
                renumbered[block] = kept.size();
                kept.add(palette.get(block));
            }
            visible[i] = renumbered[block];
        }
        return new BlockGrid(grid.width(), grid.height(), grid.length(), kept, visible);
    }

    private static boolean @NotNull [] reach(@NotNull BlockGrid grid) {
        int width = grid.width();
        int height = grid.height();
        int length = grid.length();
        int[] cells = grid.cells();
        List<BlockGrid.PaletteEntry> palette = grid.palette();

        boolean[] reached = new boolean[cells.length];
        // A cell is queued once, when it is first reached, so the queue cannot outgrow
        // the grid. An array rather than a deque: a million boxed integers is a real cost.
        int[] queue = new int[cells.length];
        int head = 0;
        int tail = 0;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                for (int y = 0; y < height; y++) {
                    boolean onAFace = x == 0 || y == 0 || z == 0
                            || x == width - 1 || y == height - 1 || z == length - 1;
                    if (onAFace) {
                        int index = grid.index(x, y, z);
                        reached[index] = true;
                        queue[tail++] = index;
                    }
                }
            }
        }

        while (head < tail) {
            int index = queue[head++];
            boolean air = cells[index] == BlockGrid.AIR;
            if (!air && palette.get(cells[index]).occluding()) {
                continue;
            }
            int y = index % height;
            int z = (index / height) % length;
            int x = index / (height * length);
            for (int[] step : NEIGHBOURS) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= length) {
                    continue;
                }
                int neighbour = grid.index(nx, ny, nz);
                if (reached[neighbour]) {
                    continue;
                }
                // Through a block that hides nothing, the view reaches the next block
                // and not the next room.
                if (!air && cells[neighbour] == BlockGrid.AIR) {
                    continue;
                }
                reached[neighbour] = true;
                queue[tail++] = neighbour;
            }
        }
        return reached;
    }
}
```

- [ ] **Step 4: Run the test and see it pass**

Run: `./gradlew :realty-paper:test --tests "*ShellCullTest*"`
Expected: 9 tests pass.

**Commit message:** `feat(schematic): cull blocks that cannot be seen from outside`

---

### Task 4: The encoder

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/RealtySchematicEncoder.java`
- Create: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/RealtySchematicTestDecoder.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/RealtySchematicEncoderTest.java`

**Interfaces:**
- Consumes: `BlockGrid` from Task 2, `RealtySchematicFormat` from Task 1.
- Produces: `RealtySchematicEncoder.encode(BlockGrid grid, int dataVersion)` returning `byte[]`.

- [ ] **Step 1: Write the test decoder**

It lives in the test source set. The server never needs to read a capture back, and shipping a decoder in the plugin would be a way to restore one.

```java
package io.github.md5sha256.realty.schematic;

import io.github.md5sha256.realty.api.RealtySchematicFormat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

/** Reads the format back, for tests only. Follows the layout in the plan, not the encoder. */
final class RealtySchematicTestDecoder {

    record Decoded(int dataVersion, int width, int height, int length,
                   List<String> states, List<String> blockEntityIds, int[] cells, int runCount) {
    }

    private RealtySchematicTestDecoder() {
    }

    static Decoded decode(byte[] bytes) throws IOException {
        if (!RealtySchematicFormat.isReadable(bytes)) {
            throw new IOException("not a Realty schematic");
        }
        ByteArrayInputStream body = new ByteArrayInputStream(bytes,
                RealtySchematicFormat.HEADER_LENGTH, bytes.length - RealtySchematicFormat.HEADER_LENGTH);
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(body))) {
            int dataVersion = in.readInt();
            int width = in.readInt();
            int height = in.readInt();
            int length = in.readInt();
            int paletteSize = in.readInt();
            List<String> states = new ArrayList<>();
            List<String> blockEntityIds = new ArrayList<>();
            for (int i = 0; i < paletteSize; i++) {
                states.add(text(in));
                blockEntityIds.add(text(in));
            }
            int[] cells = new int[width * height * length];
            int runCount = in.readInt();
            int at = 0;
            for (int i = 0; i < runCount; i++) {
                int block = varint(in);
                int run = varint(in);
                for (int j = 0; j < run; j++) {
                    cells[at++] = block;
                }
            }
            if (at != cells.length) {
                throw new IOException("runs cover " + at + " of " + cells.length + " cells");
            }
            if (in.read() != -1) {
                throw new IOException("bytes after the last run");
            }
            return new Decoded(dataVersion, width, height, length, states, blockEntityIds, cells, runCount);
        }
    }

    private static String text(DataInputStream in) throws IOException {
        byte[] bytes = new byte[in.readUnsignedShort()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int varint(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int next = in.readUnsignedByte();
            value |= (next & 0x7f) << shift;
            if ((next & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("varint too long");
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package io.github.md5sha256.realty.schematic;

import io.github.md5sha256.realty.api.RealtySchematicFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

class RealtySchematicEncoderTest {

    /** The fixture from the plan. The explorer's tests decode the same bytes. */
    private static final String GOLDEN =
            "UkxUWQF4nFWNQQrCMBBFJ426E72G0BMIOYkUGcOkCbaJZGbTA/ceHUWILv5iHu//ATivAGA13Tc7OM4pk68Y5IqpKjo1"
                    + "wFIyKXINFXzeWdTkW0Cf8uhyqRL7iFNwjyJS5p4jvsixVExjlEEHLm3AR2L578ry1vWeaPh9/1G1vTcGbGfgYM0G"
                    + "3807YQ==";

    private static final List<BlockGrid.PaletteEntry> PALETTE = List.of(
            BlockGrid.PaletteEntry.AIR,
            new BlockGrid.PaletteEntry("minecraft:stone", "", true),
            new BlockGrid.PaletteEntry(
                    "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]", "", false),
            new BlockGrid.PaletteEntry(
                    "minecraft:chest[facing=north,type=single]", "minecraft:chest", false));

    private static BlockGrid sample() {
        BlockGrid grid = new BlockGrid(3, 2, 2, PALETTE, new int[12]);
        grid.cells()[grid.index(0, 0, 0)] = 1;
        grid.cells()[grid.index(1, 0, 0)] = 2;
        grid.cells()[grid.index(2, 1, 1)] = 3;
        return grid;
    }

    @Test
    void opensWithTheRealtyHeader() throws Exception {
        byte[] bytes = RealtySchematicEncoder.encode(sample(), 4325);

        Assertions.assertTrue(RealtySchematicFormat.isReadable(bytes));
        Assertions.assertArrayEquals(RealtySchematicFormat.header(),
                Arrays.copyOf(bytes, RealtySchematicFormat.HEADER_LENGTH));
    }

    @Test
    void isNotSomethingWorldEditWouldOpen() throws Exception {
        byte[] bytes = RealtySchematicEncoder.encode(sample(), 4325);

        // WorldEdit's readers all begin by un-gzipping.
        Assertions.assertFalse(bytes[0] == (byte) 0x1f && bytes[1] == (byte) 0x8b);
    }

    @Test
    void whatIsWrittenReadsBackTheSame() throws Exception {
        RealtySchematicTestDecoder.Decoded decoded =
                RealtySchematicTestDecoder.decode(RealtySchematicEncoder.encode(sample(), 4325));

        Assertions.assertEquals(4325, decoded.dataVersion());
        Assertions.assertEquals(3, decoded.width());
        Assertions.assertEquals(2, decoded.height());
        Assertions.assertEquals(2, decoded.length());
        Assertions.assertEquals(PALETTE.stream().map(BlockGrid.PaletteEntry::state).toList(),
                decoded.states());
        Assertions.assertEquals(List.of("", "", "", "minecraft:chest"), decoded.blockEntityIds());
        Assertions.assertArrayEquals(new int[]{1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 3}, decoded.cells());
    }

    @Test
    void theGoldenFixtureStillDecodes() throws Exception {
        // If this fails the layout has changed, and every capture already stored is
        // unreadable. Change the version byte; do not change the fixture.
        RealtySchematicTestDecoder.Decoded decoded =
                RealtySchematicTestDecoder.decode(Base64.getDecoder().decode(GOLDEN));

        Assertions.assertEquals(4325, decoded.dataVersion());
        Assertions.assertEquals(List.of("", "", "", "minecraft:chest"), decoded.blockEntityIds());
        Assertions.assertArrayEquals(new int[]{1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 3}, decoded.cells());
    }

    @Test
    void neighbouringCellsOfOneBlockShareARun() throws Exception {
        BlockGrid air = new BlockGrid(10, 10, 10, List.of(BlockGrid.PaletteEntry.AIR), new int[1000]);

        RealtySchematicTestDecoder.Decoded decoded =
                RealtySchematicTestDecoder.decode(RealtySchematicEncoder.encode(air, 4325));

        Assertions.assertEquals(1, decoded.runCount());
        Assertions.assertArrayEquals(new int[1000], decoded.cells());
    }

    @Test
    void aRunLongerThanOneVarintByteSurvives() throws Exception {
        int[] cells = new int[300];
        Arrays.fill(cells, 0, 200, 1);
        BlockGrid grid = new BlockGrid(300, 1, 1, PALETTE, cells);

        RealtySchematicTestDecoder.Decoded decoded =
                RealtySchematicTestDecoder.decode(RealtySchematicEncoder.encode(grid, 4325));

        Assertions.assertEquals(2, decoded.runCount());
        Assertions.assertArrayEquals(cells, decoded.cells());
    }

    @Test
    void theOccludingFlagIsNotWritten() throws Exception {
        List<BlockGrid.PaletteEntry> flipped = List.of(
                BlockGrid.PaletteEntry.AIR,
                new BlockGrid.PaletteEntry("minecraft:stone", "", false));
        List<BlockGrid.PaletteEntry> original = List.of(
                BlockGrid.PaletteEntry.AIR,
                new BlockGrid.PaletteEntry("minecraft:stone", "", true));

        Assertions.assertArrayEquals(
                RealtySchematicEncoder.encode(new BlockGrid(1, 1, 1, original, new int[]{1}), 1),
                RealtySchematicEncoder.encode(new BlockGrid(1, 1, 1, flipped, new int[]{1}), 1));
    }

    @Test
    void noWorldCoordinateAppearsInTheBody() throws Exception {
        // A grid cannot hold one, so this guards the encoder against growing a field.
        RealtySchematicTestDecoder.decode(RealtySchematicEncoder.encode(sample(), 4325));
        String printable = new String(
                RealtySchematicEncoder.encode(sample(), 4325), StandardCharsets.ISO_8859_1);
        Assertions.assertFalse(printable.contains("Offset"));
    }
}
```

- [ ] **Step 3: Run it and see it fail**

Run: `./gradlew :realty-paper:test --tests "*RealtySchematicEncoderTest*"`
Expected: compilation failure, `RealtySchematicEncoder` does not exist.

- [ ] **Step 4: Write the encoder**

```java
package io.github.md5sha256.realty.schematic;

import io.github.md5sha256.realty.api.RealtySchematicFormat;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes a grid in Realty's own layout.
 *
 * <p>Not a WorldEdit schematic, and not convertible to one by any existing tool: the
 * bytes are served from a public endpoint, and a saved response should load nowhere.
 * The explorer carries the matching decoder, so this is an obstacle and not a lock.</p>
 *
 * <p>Touches neither WorldEdit nor the world, so it runs wherever the caller likes.</p>
 */
public final class RealtySchematicEncoder {

    private RealtySchematicEncoder() {
    }

    /**
     * @param dataVersion the Minecraft data version the block states were read under;
     *                    the explorer's renderer needs it to interpret them
     */
    public static byte @NotNull [] encode(@NotNull BlockGrid grid, int dataVersion) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(RealtySchematicFormat.header());
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(dataVersion);
            out.writeInt(grid.width());
            out.writeInt(grid.height());
            out.writeInt(grid.length());

            out.writeInt(grid.palette().size());
            for (BlockGrid.PaletteEntry entry : grid.palette()) {
                writeText(out, entry.state());
                writeText(out, entry.blockEntityId());
            }

            int[] cells = grid.cells();
            out.writeInt(countRuns(cells));
            int start = 0;
            for (int i = 1; i <= cells.length; i++) {
                if (i == cells.length || cells[i] != cells[start]) {
                    writeVarint(out, cells[start]);
                    writeVarint(out, i - start);
                    start = i;
                }
            }
        }
        return bytes.toByteArray();
    }

    private static int countRuns(int @NotNull [] cells) {
        int runs = 0;
        for (int i = 0; i < cells.length; i++) {
            if (i == 0 || cells[i] != cells[i - 1]) {
                runs++;
            }
        }
        return runs;
    }

    /** Length-prefixed UTF-8, written by hand because {@code writeUTF} is not quite UTF-8. */
    private static void writeText(@NotNull DataOutputStream out, @NotNull String text) throws IOException {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 0xffff) {
            throw new IOException("Block state too long to write: " + encoded.length + " bytes");
        }
        out.writeShort(encoded.length);
        out.write(encoded);
    }

    private static void writeVarint(@NotNull DataOutputStream out, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7f) != 0) {
            out.writeByte((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte(remaining);
    }
}
```

- [ ] **Step 5: Run the test and see it pass**

Run: `./gradlew :realty-paper:test --tests "*RealtySchematicEncoderTest*"`
Expected: 8 tests pass.

**Commit message:** `feat(schematic): encode captures in Realty's own format`

---

### Task 5: From clipboard to grid

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/ClipboardGrids.java`
- Modify: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/WorldEditTestPlatform.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/ClipboardGridsTest.java`

**Interfaces:**
- Consumes: `BlockGrid`, `VisualState` from Task 2.
- Produces: `ClipboardGrids.fromClipboard(Clipboard clipboard, Predicate<BlockState> occluding)` returning `BlockGrid`.

The predicate is a parameter because whether a block occludes comes from WorldEdit's material registry, which on a server is filled by the platform adapter. Tests answer it directly and do not depend on which bundled data the stub platform happens to load.

- [ ] **Step 1: Read before writing**

Read `WorldEditTestPlatform.java` and `TickSlicedCopyTest.java`. The test platform registers only `air`, `stone`, `dirt` and `chest`. Add `minecraft:cave_air` and `minecraft:oak_leaves` to that array. `TickSlicedCopyTest` shows how a `BaseBlock` with a block entity is built.

- [ ] **Step 2: Write the failing test**

```java
package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Predicate;

class ClipboardGridsTest {

    /** Far from the origin, so a leaked world coordinate would be obvious. */
    private static final BlockVector3 MIN = BlockVector3.at(1200, 64, -3400);
    private static final BlockVector3 MAX = BlockVector3.at(1202, 65, -3399);

    private static final Predicate<BlockState> STONE_OCCLUDES =
            state -> state.getBlockType().id().equals("minecraft:stone");

    @BeforeAll
    static void bootWorldEdit() {
        WorldEditTestPlatform.ensureRegistered();
    }

    private static BlockArrayClipboard clipboard() {
        return new BlockArrayClipboard(new CuboidRegion(MIN, MAX));
    }

    private static List<String> states(BlockGrid grid) {
        return grid.palette().stream().map(BlockGrid.PaletteEntry::state).toList();
    }

    @Test
    void theGridIsTheSizeOfTheRegion() throws Exception {
        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard(), STONE_OCCLUDES);

        Assertions.assertEquals(3, grid.width());
        Assertions.assertEquals(2, grid.height());
        Assertions.assertEquals(2, grid.length());
    }

    @Test
    void blocksLandRelativeToTheRegionsCornerNotTheWorld() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.STONE.getDefaultState());
        clipboard.setBlock(MAX, BlockTypes.DIRT.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals("minecraft:stone",
                grid.palette().get(grid.cells()[grid.index(0, 0, 0)]).state());
        Assertions.assertEquals("minecraft:dirt",
                grid.palette().get(grid.cells()[grid.index(2, 1, 1)]).state());
    }

    @Test
    void airIsAlwaysPaletteEntryZero() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.STONE.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals("minecraft:air", grid.palette().getFirst().state());
        Assertions.assertEquals(BlockGrid.AIR, grid.cells()[grid.index(1, 0, 0)]);
    }

    @Test
    void everyKindOfAirIsTheSameAir() throws Exception {
        // The cull asks one question of a cell: is it air. Cave air that kept its own
        // palette entry would read as a block and seal whatever it surrounded.
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.CAVE_AIR.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals(List.of("minecraft:air"), states(grid));
    }

    @Test
    void theOccludingAnswerIsRecordedPerBlock() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.STONE.getDefaultState());
        clipboard.setBlock(MAX, BlockTypes.DIRT.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertTrue(grid.palette().get(grid.cells()[grid.index(0, 0, 0)]).occluding());
        Assertions.assertFalse(grid.palette().get(grid.cells()[grid.index(2, 1, 1)]).occluding());
    }

    @Test
    void aBlockEntityKeepsItsIdAndNothingElse() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        LinCompoundTag chest = LinCompoundTag.builder().putString("id", "minecraft:chest").build();
        clipboard.setBlock(MIN, BlockTypes.CHEST.getDefaultState()
                .toBaseBlock(LazyReference.computed(chest)));

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        BlockGrid.PaletteEntry entry = grid.palette().get(grid.cells()[grid.index(0, 0, 0)]);
        Assertions.assertTrue(entry.state().startsWith("minecraft:chest"));
        Assertions.assertEquals("minecraft:chest", entry.blockEntityId());
    }

    @Test
    void statesAreReducedBeforeTheyReachThePalette() {
        // The stub platform gives its blocks no properties, so a real leaf block prints
        // as bare "minecraft:oak_leaves" and would pass this whether or not anything was
        // reduced. The state is mocked to print the way a server's does.
        BlockState leaves = Mockito.mock(BlockState.class);
        Mockito.when(leaves.getBlockType()).thenReturn(BlockTypes.OAK_LEAVES);
        Mockito.when(leaves.getAsString())
                .thenReturn("minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]");
        BaseBlock block = Mockito.mock(BaseBlock.class);
        Mockito.when(block.toImmutableState()).thenReturn(leaves);
        Clipboard clipboard = Mockito.mock(Clipboard.class);
        Mockito.when(clipboard.getRegion()).thenReturn(new CuboidRegion(MIN, MIN));
        Mockito.when(clipboard.getFullBlock(ArgumentMatchers.any(BlockVector3.class))).thenReturn(block);

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals(
                List.of("minecraft:air", "minecraft:oak_leaves[waterlogged=false]"), states(grid));
    }

    @Test
    void manyCellsOfOneBlockShareOnePaletteEntry() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        for (BlockVector3 position : clipboard.getRegion()) {
            clipboard.setBlock(position, BlockTypes.STONE.getDefaultState());
        }

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals(List.of("minecraft:air", "minecraft:stone"), states(grid));
    }
}
```

- [ ] **Step 3: Run it and see it fail**

Run: `./gradlew :realty-paper:test --tests "*ClipboardGridsTest*"`
Expected: compilation failure.

- [ ] **Step 4: Write `ClipboardGrids`**

```java
package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads a filled clipboard into a {@link BlockGrid}.
 *
 * <p>This is the last place a WorldEdit type or a world coordinate exists. Everything
 * after it works on the grid, where positions are counted from the capture's own corner.</p>
 *
 * <p>Reads the clipboard, never the world, so it is safe off the main thread.</p>
 */
public final class ClipboardGrids {

    /** Drawn identically, and the cull needs to ask only "is this air". */
    private static final Set<String> AIR =
            Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private ClipboardGrids() {
    }

    /**
     * @param occluding whether a block hides what is behind it. Asked once per distinct
     *                  block state, not once per cell.
     */
    public static @NotNull BlockGrid fromClipboard(@NotNull Clipboard clipboard,
                                                   @NotNull Predicate<BlockState> occluding) {
        Region region = clipboard.getRegion();
        BlockVector3 corner = region.getMinimumPoint();
        int width = region.getWidth();
        int height = region.getHeight();
        int length = region.getLength();

        List<BlockGrid.PaletteEntry> palette = new ArrayList<>();
        Map<BlockGrid.PaletteEntry, Integer> numbered = new HashMap<>();
        palette.add(BlockGrid.PaletteEntry.AIR);
        numbered.put(BlockGrid.PaletteEntry.AIR, BlockGrid.AIR);
        // Printing a state and reducing it builds strings, and a capture is up to a
        // million cells of a few dozen distinct blocks. Blocks without a block entity
        // are looked up by state and never printed twice.
        Map<BlockState, Integer> plain = new HashMap<>();

        int[] cells = new int[Math.multiplyExact(Math.multiplyExact(width, height), length)];
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                for (int y = 0; y < height; y++) {
                    BaseBlock block = clipboard.getFullBlock(corner.add(x, y, z));
                    BlockState state = block.toImmutableState();
                    String blockEntityId = block.getNbtReference() == null ? "" : block.getNbtId();
                    Integer known = blockEntityId.isEmpty() ? plain.get(state) : null;
                    if (known == null) {
                        BlockGrid.PaletteEntry entry = entryFor(state, blockEntityId, occluding);
                        known = numbered.computeIfAbsent(entry, added -> {
                            palette.add(added);
                            return palette.size() - 1;
                        });
                        if (blockEntityId.isEmpty()) {
                            plain.put(state, known);
                        }
                    }
                    cells[index++] = known;
                }
            }
        }
        return new BlockGrid(width, height, length, palette, cells);
    }

    private static @NotNull BlockGrid.PaletteEntry entryFor(@NotNull BlockState state,
                                                            @NotNull String blockEntityId,
                                                            @NotNull Predicate<BlockState> occluding) {
        if (AIR.contains(state.getBlockType().id())) {
            return BlockGrid.PaletteEntry.AIR;
        }
        return new BlockGrid.PaletteEntry(
                VisualState.reduce(state.getAsString()), blockEntityId, occluding.test(state));
    }
}
```

The loop order must match `BlockGrid.index`. `index++` is correct only because x is outermost and y innermost. If you reorder the loops, compute the index with `grid.index` instead.

- [ ] **Step 5: Run the test and see it pass**

Run: `./gradlew :realty-paper:test --tests "*ClipboardGridsTest*"`
Expected: 8 tests pass.

`statesAreReducedBeforeTheyReachThePalette` mocks the block state. The stub platform gives its blocks no properties, so a real leaf block would pass that test whether or not anything was reduced. This was found during execution and the test was rewritten. It was checked by removing the `VisualState.reduce` call, which makes it fail.

- [ ] **Step 6: Run the whole schematic package**

Run: `./gradlew :realty-paper:test --tests "io.github.md5sha256.realty.schematic.*"`
Expected: every test passes, including the pre-existing `TickSlicedCopyTest`, `CaptureBoundsTest`, `CaptureCooldownTest` and `RegionVolumeTest`.

**Commit message:** `feat(schematic): read a captured clipboard into a block grid`

---

### Task 6: Wire the capture command

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/SchematicCommandGroup.java`
- Delete: `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/RegionSchematicWriter.java`

**Interfaces:**
- Consumes: `ClipboardGrids`, `ShellCull`, `RealtySchematicEncoder`.
- Produces: no new surface. The command's arguments, permissions and messages are unchanged.

- [ ] **Step 1: Read the command**

Read all of `SchematicCommandGroup.java`. The one line that matters is in `persist`:

```java
byte[] bytes = RegionSchematicWriter.writeClipboard(clipboard);
```

- [ ] **Step 2: Resolve the data version on the main thread**

In `executeCapture`, after `weWorld` is resolved, read the data version and pass it along through `beginCapture` to `persist` as an `int`:

```java
int dataVersion = WorldEdit.getInstance().getPlatformManager()
        .queryCapability(Capability.WORLD_EDITING).getDataVersion();
```

Imports: `com.sk89q.worldedit.WorldEdit`, `com.sk89q.worldedit.extension.platform.Capability`.

Add `int dataVersion` as a parameter to `beginCapture` and `persist`, and thread it through the lambda that calls `persist`.

- [ ] **Step 3: Replace the encode**

In `persist`, replace the `RegionSchematicWriter` line with:

```java
// Nothing here can be loaded by WorldEdit, and nothing that could be is kept.
// The grid drops the world position, the cull drops what cannot be seen from
// outside, and the encoder writes what is left in Realty's own layout.
BlockGrid grid = ShellCull.hollow(
        ClipboardGrids.fromClipboard(clipboard, SchematicCommandGroup::hidesWhatIsBehindIt));
byte[] bytes = RealtySchematicEncoder.encode(grid, dataVersion);
```

Add the predicate as a private static method:

```java
/**
 * A full, opaque cube. Anything less -- glass, a slab, a fence -- leaves something
 * behind it visible, and the cull has to keep that something.
 *
 * <p>A block the registry knows nothing about is treated as hiding nothing. The cost
 * of that guess is a block kept that need not have been; the other guess would punch
 * a hole in the preview.</p>
 */
private static boolean hidesWhatIsBehindIt(@NotNull BlockState state) {
    BlockMaterial material = state.getBlockType().getMaterial();
    return material != null && material.isFullCube() && material.isOpaque();
}
```

Imports: `com.sk89q.worldedit.world.block.BlockState`, `com.sk89q.worldedit.world.registry.BlockMaterial`, and the three schematic classes. Remove the `RegionSchematicWriter` import.

- [ ] **Step 4: Update the class Javadoc**

Add one paragraph to the class comment of `SchematicCommandGroup` saying that what is stored is a hollowed, position-less copy in Realty's own format, and that the command can therefore never be used to back a region up.

- [ ] **Step 5: Delete the old writer**

Delete `RegionSchematicWriter.java`. Then confirm nothing refers to it:

Run: `grep -rn "RegionSchematicWriter\|SPONGE_V3_SCHEMATIC\|BuiltInClipboardFormat" --include="*.java" realty-paper/src realty-backend/src realty-backend-api/src realty-web/realty-rest/src`
Expected: no output.

- [ ] **Step 6: Build and test the plugin**

Run: `./gradlew :realty-paper:build`
Expected: BUILD SUCCESSFUL.

The command itself has no unit test, because it needs a running server. State that in the report. Do not write a test that mocks the whole of Bukkit to claim coverage.

**Commit message:** `feat(schematic): capture a hollow shell in Realty's format, not a WorldEdit file`

---

### Task 7: Delete the old captures

> **Added during execution.** `PurgeWorldEditSchematicsMigrationTest` in `realty-backend`. It builds a database at version 17, stores a WorldEdit capture, migrates, and checks that the capture is gone and another table is untouched. The shared test database is already at the latest version when tests run, so without this the migration was only ever run against an empty table.

**Files:**
- Create: `realty-backend/src/main/resources/sql/migrations/V18__purge_worldedit_schematics.sql`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java`
- Modify: `realty-web/realty-rest/src/main/java/io/github/md5sha256/realty/rest/SchemaVersionCheck.java`
- Modify: `realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RealtySchematicEntity.java`

- [ ] **Step 1: Write the migration**

```sql
-- Every capture before this version is a WorldEdit schematic: a complete copy of the
-- region, interior included, at its world position. The format that replaces it holds
-- none of that, and nothing restores a region from a capture, so the old rows have no
-- use left. They are removed rather than left to be overwritten one recapture at a time.
DELETE FROM RealtySchematic;
```

- [ ] **Step 2: Register it**

In `MariaSchemaMigrator.DEFAULT_MIGRATIONS`, after the V17 entry:

```java
new MigrationStep(18, "purge worldedit schematics", "V18__purge_worldedit_schematics.sql")
```

Mind the comma on the V17 line.

- [ ] **Step 3: Bump the REST version gate**

In `SchemaVersionCheck`, set `EXPECTED_VERSION = 18`.

- [ ] **Step 4: Correct the entity's Javadoc**

In `RealtySchematicEntity`, the `data` parameter is described as "Sponge Schematic v3 bytes, as written by WorldEdit". Change it to say the bytes are in Realty's own format, and link `RealtySchematicFormat`.

- [ ] **Step 5: Find every test that pins the version**

Run: `grep -rn "EXPECTED_VERSION\|\b17\b" --include="*.java" realty-backend/src/test realty-web/realty-rest/src/test`

Update any assertion that expects 17 as the latest schema version. Report each one changed.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :realty-backend:test :realty-web:realty-rest:test`
Expected: pass. The `realty-backend` database tests need Docker. If Docker is not running, run `./gradlew :realty-web:realty-rest:test` and `./gradlew :realty-backend:compileTestJava`, and report that the migration was not exercised against a database.

**Commit message:** `feat(db): delete captures made in the WorldEdit format`

---

### Task 8: Refuse to serve anything else

**Files:**
- Modify: `realty-web/realty-rest/src/main/java/io/github/md5sha256/realty/rest/RegionSchematicHandler.java`
- Modify: `realty-web/realty-rest/src/test/java/io/github/md5sha256/realty/rest/RegionSchematicEndpointTest.java`

**Interfaces:**
- Consumes: `RealtySchematicFormat.isReadable` from Task 1.

The plugin and the REST service are deployed separately against one database. If the REST service is upgraded first, or an old plugin is still running somewhere, the table can hold WorldEdit bytes. This check is what keeps them from being served.

- [ ] **Step 1: Make the existing tests use Realty bytes**

Every test in `RegionSchematicEndpointTest` passes arbitrary bytes such as `new byte[]{1, 2, 3}` to `TestServers.withSchematic`. After this task those are refused. Add a helper to the test class and use it everywhere a body is expected to be served:

```java
/** A body behind the Realty header. The handler checks the header and nothing after it. */
private static byte[] realty(byte... body) {
    byte[] header = RealtySchematicFormat.header();
    byte[] bytes = Arrays.copyOf(header, header.length + body.length);
    System.arraycopy(body, 0, bytes, header.length, body.length);
    return bytes;
}
```

In `aLargeSchematicIsServedInFull`, build the megabyte of letters as now, pass `realty(large)`, and build the expected string from the full served array. The header is five ASCII-range bytes and survives the test client's string decoding.

Leave `TestServers` alone.

- [ ] **Step 2: Add the failing tests**

```java
@Test
void aWorldEditSchematicLeftInTheDatabaseIsNotServed() {
    // Gzip magic, which is how every Sponge schematic begins. A row like this is a
    // capture from before the format changed.
    byte[] sponge = {0x1f, (byte) 0x8b, 8, 0, 0, 0, 0, 0};
    JavalinTest.test(TestServers.withSchematic(sponge).javalin(), (app, client) -> {
        Response response = client.get("/v1/region/schematic?world=world&region=plot_a");
        Assertions.assertEquals(404, response.code());
        Assertions.assertTrue(response.body().string().contains("SCHEMATIC_NOT_FOUND"));
    });
}

@Test
void aRefusedSchematicSendsNoEntityTag() {
    // An ETag is a fingerprint of the bytes. Refusing the bytes and publishing their
    // hash would still let someone confirm a guess at them.
    byte[] sponge = {0x1f, (byte) 0x8b, 8, 0, 0, 0, 0, 0};
    JavalinTest.test(TestServers.withSchematic(sponge).javalin(), (app, client) -> {
        Response response = client.get("/v1/region/schematic?world=world&region=plot_a");
        Assertions.assertNull(firstHeader(response, "ETag"));
    });
}

@Test
void aFormatVersionThisBuildDoesNotKnowIsNotServed() {
    byte[] future = {'R', 'L', 'T', 'Y', 2, 1, 2, 3};
    JavalinTest.test(TestServers.withSchematic(future).javalin(), (app, client) ->
            Assertions.assertEquals(404,
                    client.get("/v1/region/schematic?world=world&region=plot_a").code()));
}
```

- [ ] **Step 3: Run and see the new tests fail**

Run: `./gradlew :realty-web:realty-rest:test --tests "*RegionSchematicEndpointTest*"`
Expected: the three new tests fail with 200 where 404 was expected. Every other test passes.

- [ ] **Step 4: Add the check**

In `RegionSchematicHandler.handle`, replace the null check:

```java
// A row without the Realty header is a capture from before the format changed, or
// from a plugin that has not been upgraded: a WorldEdit file. It is answered exactly
// as a region with no capture, because to the visitor that is what it is.
if (!RealtySchematicFormat.isReadable(schematic)) {
    throw ApiException.notFound("SCHEMATIC_NOT_FOUND",
            "No schematic captured for region '" + regionParam + "' in world '" + worldParam + "'");
}
```

`isReadable` returns false for null, so this one check covers both cases. It must come before the ETag is computed.

Rewrite the class Javadoc. It currently says the endpoint serves "the raw Sponge Schematic v3 bytes". It serves a capture in Realty's own format, decoded by the explorer. Keep the paragraph explaining why the response is bytes and not JSON.

- [ ] **Step 5: Run the tests and see them pass**

Run: `./gradlew :realty-web:realty-rest:test`
Expected: every test passes.

**Commit message:** `feat(rest): serve only captures in Realty's format`

---

### Task 9: The explorer's decoder

**Files:**
- Create: `realty-web/realty-explorer/src/api/realtySchematic.ts`
- Create: `realty-web/realty-explorer/src/api/realtySchematic.test.ts`
- Modify: `realty-web/realty-explorer/package.json`, `package-lock.json`

**Interfaces:**
- Produces: `isRealtySchematic(bytes)`, `decodeRealtySchematic(bytes)`, `toSpongeSchematic(decoded)`, `realtyToRenderable(bytes)`, `UnreadableSchematicError`, the type `DecodedSchematic`.

This module sits in `src/api`, not `src/viewer`, so that importing it never pulls Three.js or the WASM mesher into the main bundle.

**Why it rebuilds a Sponge schematic in memory.** The renderer's constructor takes loaders that return an `ArrayBuffer` it can parse. Rebuilding one uses the path the renderer already exercises and keeps WASM out of application code. The rebuilt bytes exist only in the page's memory and are never requested, stored or offered for download.

- [ ] **Step 1: Add the test-only dependency**

In `realty-web/realty-explorer`, run: `npm install --save-dev nucleation@^0.2.18`

`nucleation` is already installed as a dependency of `schematic-renderer`. This makes the test's import of it legitimate. Confirm `package-lock.json` still resolves a single copy:

Run: `npm ls nucleation`
Expected: one version, deduped under `schematic-renderer`.

- [ ] **Step 2: Write the failing test**

The file opens with a Vitest environment comment. The decoder uses `DecompressionStream`, which Node provides and jsdom may not pass through.

```ts
// @vitest-environment node
import { deflateSync, gunzipSync } from "node:zlib";
import { describe, expect, it } from "vitest";
import init, { SchematicWrapper } from "nucleation";
import {
  UnreadableSchematicError,
  decodeRealtySchematic,
  isRealtySchematic,
  realtyToRenderable,
  toSpongeSchematic,
} from "./realtySchematic";

/** The fixture from the plan. The plugin's tests decode the same bytes. */
const GOLDEN =
  "UkxUWQF4nFWNQQrCMBBFJ426E72G0BMIOYkUGcOkCbaJZGbTA/ceHUWILv5iHu//ATivAGA13Tc7OM4pk68Y5IqpKjo1" +
  "wFIyKXINFXzeWdTkW0Cf8uhyqRL7iFNwjyJS5p4jvsixVExjlEEHLm3AR2L578ry1vWeaPh9/1G1vTcGbGfgYM0G" +
  "3807YQ==";

const golden = (): ArrayBuffer => {
  const bytes = Buffer.from(GOLDEN, "base64");
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
};

const asBuffer = (bytes: Uint8Array): ArrayBuffer =>
  bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;

/** Builds a body by hand, so a test can write one the encoder never would. */
class Body {
  private readonly bytes: number[] = [];

  int(value: number): this {
    this.bytes.push((value >>> 24) & 0xff, (value >>> 16) & 0xff, (value >>> 8) & 0xff, value & 0xff);
    return this;
  }

  text(value: string): this {
    const encoded = new TextEncoder().encode(value);
    this.bytes.push((encoded.length >> 8) & 0xff, encoded.length & 0xff, ...encoded);
    return this;
  }

  varint(value: number): this {
    let remaining = value;
    while (remaining > 0x7f) {
      this.bytes.push((remaining & 0x7f) | 0x80);
      remaining >>>= 7;
    }
    this.bytes.push(remaining);
    return this;
  }

  framed(version = 1): ArrayBuffer {
    const body = deflateSync(Uint8Array.from(this.bytes));
    return asBuffer(Uint8Array.from([0x52, 0x4c, 0x54, 0x59, version, ...body]));
  }
}

/** One air cell and one stone cell, which is the smallest grid worth decoding. */
const twoCells = (): Body =>
  new Body().int(4325).int(2).int(1).int(1)
    .int(2).text("minecraft:air").text("").text("minecraft:stone").text("");

describe("isRealtySchematic", () => {
  it("recognises the header", () => {
    expect(isRealtySchematic(golden())).toBe(true);
  });

  it("does not recognise a WorldEdit schematic", () => {
    expect(isRealtySchematic(asBuffer(Uint8Array.from([0x1f, 0x8b, 8, 0, 0, 0])))).toBe(false);
  });

  it("does not recognise a version it cannot read", () => {
    expect(isRealtySchematic(twoCells().int(1).varint(0).varint(2).framed(2))).toBe(false);
  });

  it("does not recognise an empty response", () => {
    expect(isRealtySchematic(new ArrayBuffer(0))).toBe(false);
  });
});

describe("decodeRealtySchematic", () => {
  it("decodes the golden fixture", async () => {
    const decoded = await decodeRealtySchematic(golden());

    expect(decoded.dataVersion).toBe(4325);
    expect([decoded.width, decoded.height, decoded.length]).toEqual([3, 2, 2]);
    expect(decoded.palette).toEqual([
      { state: "minecraft:air", blockEntityId: "" },
      { state: "minecraft:stone", blockEntityId: "" },
      { state: "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]", blockEntityId: "" },
      { state: "minecraft:chest[facing=north,type=single]", blockEntityId: "minecraft:chest" },
    ]);
    expect(Array.from(decoded.cells)).toEqual([1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 3]);
  });

  it("refuses a WorldEdit schematic", async () => {
    await expect(decodeRealtySchematic(asBuffer(Uint8Array.from([0x1f, 0x8b, 8, 0, 0, 0]))))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a body that is not compressed data", async () => {
    await expect(decodeRealtySchematic(asBuffer(Uint8Array.from([0x52, 0x4c, 0x54, 0x59, 1, 9, 9, 9]))))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a body that ends early", async () => {
    await expect(decodeRealtySchematic(new Body().int(4325).int(2).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses dimensions that would allocate without limit", async () => {
    const huge = new Body().int(4325).int(100000).int(100000).int(100000)
      .int(1).text("minecraft:air").text("").int(1).varint(0).varint(1);
    await expect(decodeRealtySchematic(huge.framed())).rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a dimension of zero or less", async () => {
    const flat = new Body().int(4325).int(0).int(1).int(1)
      .int(1).text("minecraft:air").text("").int(0);
    await expect(decodeRealtySchematic(flat.framed())).rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a palette size it could never fill", async () => {
    const lying = new Body().int(4325).int(2).int(1).int(1).int(2000000000);
    await expect(decodeRealtySchematic(lying.framed())).rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a run that names a block outside the palette", async () => {
    await expect(decodeRealtySchematic(twoCells().int(1).varint(7).varint(2).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses runs that overfill the grid", async () => {
    await expect(decodeRealtySchematic(twoCells().int(1).varint(1).varint(3).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses runs that leave the grid short", async () => {
    await expect(decodeRealtySchematic(twoCells().int(1).varint(1).varint(1).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a run of no cells, which could otherwise loop for ever", async () => {
    await expect(decodeRealtySchematic(twoCells().int(2).varint(0).varint(0).varint(1).varint(2).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });
});

describe("toSpongeSchematic", () => {
  it("is gzipped, which the renderer's parser requires", async () => {
    const sponge = new Uint8Array(await toSpongeSchematic(await decodeRealtySchematic(golden())));
    expect([sponge[0], sponge[1]]).toEqual([0x1f, 0x8b]);
    expect(() => gunzipSync(sponge)).not.toThrow();
  });

  it("places no block at a world position", async () => {
    const sponge = gunzipSync(new Uint8Array(await toSpongeSchematic(await decodeRealtySchematic(golden()))));
    const offset = sponge.indexOf(Buffer.from("Offset"));
    expect(offset).toBeGreaterThan(0);
    // Tag name, then an int-array length of 3, then three zero ints.
    expect(Array.from(sponge.subarray(offset + 6, offset + 6 + 16)))
      .toEqual([0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
  });

  it("refuses a capture too long on one axis to be drawn", async () => {
    const row = new Body().int(4325).int(70000).int(1).int(1)
      .int(1).text("minecraft:air").text("").int(1).varint(0).varint(70000);
    const decoded = await decodeRealtySchematic(row.framed());
    await expect(toSpongeSchematic(decoded)).rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("is read by the renderer's own parser, block for block", async () => {
    const decoded = await decodeRealtySchematic(golden());
    await init();
    const parsed = new SchematicWrapper();
    parsed.from_schematic(new Uint8Array(await toSpongeSchematic(decoded)));

    for (let x = 0; x < decoded.width; x++) {
      for (let y = 0; y < decoded.height; y++) {
        for (let z = 0; z < decoded.length; z++) {
          const expected = decoded.palette[decoded.cells[(x * decoded.length + z) * decoded.height + y]].state;
          expect(parsed.get_block_string(x, y, z) ?? "minecraft:air", `at ${x},${y},${z}`).toBe(expected);
        }
      }
    }
    expect(parsed.get_block_entity(2, 1, 1)?.id).toBe("minecraft:chest");
  });
});

describe("realtyToRenderable", () => {
  it("goes from served bytes to something the renderer loads", async () => {
    const renderable = new Uint8Array(await realtyToRenderable(golden()));
    await init();
    const parsed = new SchematicWrapper();
    parsed.from_schematic(renderable);
    expect(parsed.get_block_count()).toBe(3);
  });
});
```

- [ ] **Step 3: Run it and see it fail**

Run, in `realty-web/realty-explorer`: `npx vitest run src/api/realtySchematic.test.ts`
Expected: failure, the module does not exist.

- [ ] **Step 4: Write the decoder**

```ts
/**
 * Reads a capture in Realty's own format and rebuilds something the renderer can load.
 *
 * The plugin does not store WorldEdit schematics. It stores a hollowed copy of the
 * region, without its world position, in a layout nothing else reads. This module is
 * the other half of that layout.
 *
 * It is an obstacle, not a lock: this code ships to every visitor. What it guarantees
 * is that a saved response loads in no existing tool, and that what can be recovered
 * with effort is a shell.
 */

const MAGIC = [0x52, 0x4c, 0x54, 0x59];
const VERSION = 1;
const HEADER_LENGTH = MAGIC.length + 1;

/**
 * The most cells a capture may claim. The plugin's default cap is a million; this
 * leaves room for an operator who raised it, and stops a response that claims a
 * billion from being believed.
 */
const MAX_CELLS = 16_777_216;

/** The longest any one axis may be and still be drawn. */
const MAX_DIMENSION = 0xffff;

/** The response is not a capture this build can read. Shown as "no preview", never as an error. */
export class UnreadableSchematicError extends Error {
  constructor(reason: string) {
    super(`Unreadable schematic: ${reason}`);
    this.name = "UnreadableSchematicError";
  }
}

export type PaletteEntry = {
  /** The block and the properties that shape it, such as `minecraft:oak_stairs[facing=north]`. */
  state: string;
  /** The block entity's id, or the empty string for none. */
  blockEntityId: string;
};

export type DecodedSchematic = {
  dataVersion: number;
  width: number;
  height: number;
  length: number;
  palette: PaletteEntry[];
  /** One palette index per cell: x outermost, then z, then y innermost. */
  cells: Int32Array;
};

/** Whether the bytes open with the header of a version this build reads. Says nothing of the body. */
export function isRealtySchematic(buffer: ArrayBuffer): boolean {
  const bytes = new Uint8Array(buffer);
  return bytes.length >= HEADER_LENGTH
    && MAGIC.every((byte, index) => bytes[index] === byte)
    && bytes[MAGIC.length] === VERSION;
}

/** Runs bytes through a compression or decompression stream and collects what comes out. */
async function through(
  bytes: Uint8Array,
  stream: CompressionStream | DecompressionStream,
): Promise<Uint8Array> {
  const writer = stream.writable.getWriter();
  // A failure here also fails the read below, which is where it is reported. Left
  // uncaught, the same failure would surface a second time as an unhandled rejection.
  writer.write(bytes as BufferSource).catch(() => undefined);
  writer.close().catch(() => undefined);

  const reader = stream.readable.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value as Uint8Array);
    total += (value as Uint8Array).length;
  }

  const joined = new Uint8Array(total);
  let at = 0;
  for (const chunk of chunks) {
    joined.set(chunk, at);
    at += chunk.length;
  }
  return joined;
}

class BodyReader {
  private readonly view: DataView;
  private at = 0;

  constructor(private readonly bytes: Uint8Array) {
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  }

  private need(count: number): void {
    if (this.at + count > this.bytes.length) throw new UnreadableSchematicError("ends early");
  }

  int(): number {
    this.need(4);
    const value = this.view.getInt32(this.at);
    this.at += 4;
    return value;
  }

  text(): string {
    this.need(2);
    const length = this.view.getUint16(this.at);
    this.at += 2;
    this.need(length);
    const value = new TextDecoder().decode(this.bytes.subarray(this.at, this.at + length));
    this.at += length;
    return value;
  }

  varint(): number {
    let value = 0;
    for (let shift = 0; shift <= 28; shift += 7) {
      this.need(1);
      const next = this.bytes[this.at++];
      value |= (next & 0x7f) << shift;
      if ((next & 0x80) === 0) return value >>> 0;
    }
    throw new UnreadableSchematicError("a number runs on too long");
  }

  get remaining(): number {
    return this.bytes.length - this.at;
  }
}

/**
 * Decodes a served capture.
 *
 * Every count in the body is checked against what the body could actually hold before
 * anything is allocated for it. The bytes come from the network, and a decoder that
 * believes a length field is a decoder that can be made to allocate gigabytes.
 *
 * @throws UnreadableSchematicError for anything that is not a well-formed capture
 */
export async function decodeRealtySchematic(buffer: ArrayBuffer): Promise<DecodedSchematic> {
  if (!isRealtySchematic(buffer)) {
    throw new UnreadableSchematicError("not a Realty capture");
  }

  let body: Uint8Array;
  try {
    body = await through(new Uint8Array(buffer).subarray(HEADER_LENGTH), new DecompressionStream("deflate"));
  } catch {
    throw new UnreadableSchematicError("the body is not compressed data");
  }

  const reader = new BodyReader(body);
  const dataVersion = reader.int();
  const width = reader.int();
  const height = reader.int();
  const length = reader.int();
  if (width <= 0 || height <= 0 || length <= 0 || width * height * length > MAX_CELLS) {
    throw new UnreadableSchematicError(`impossible size ${width}x${height}x${length}`);
  }
  const cellCount = width * height * length;

  const paletteSize = reader.int();
  // Each entry is at least two length prefixes, so the bytes left bound the count.
  if (paletteSize <= 0 || paletteSize > reader.remaining / 4) {
    throw new UnreadableSchematicError("impossible palette size");
  }
  const palette: PaletteEntry[] = [];
  for (let index = 0; index < paletteSize; index++) {
    palette.push({ state: reader.text(), blockEntityId: reader.text() });
  }

  const runCount = reader.int();
  // Each run is at least two bytes and covers at least one cell.
  if (runCount < 0 || runCount > reader.remaining / 2 || runCount > cellCount) {
    throw new UnreadableSchematicError("impossible run count");
  }
  const cells = new Int32Array(cellCount);
  let filled = 0;
  for (let run = 0; run < runCount; run++) {
    const block = reader.varint();
    const count = reader.varint();
    if (block >= paletteSize) throw new UnreadableSchematicError("a block outside the palette");
    if (count === 0) throw new UnreadableSchematicError("a run of no cells");
    if (filled + count > cellCount) throw new UnreadableSchematicError("more cells than the grid holds");
    cells.fill(block, filled, filled + count);
    filled += count;
  }
  if (filled !== cellCount) {
    throw new UnreadableSchematicError("fewer cells than the grid holds");
  }

  return { dataVersion, width, height, length, palette, cells };
}

/** Just enough of an NBT writer to describe a schematic. */
class NbtWriter {
  private chunk = new Uint8Array(1 << 16);
  private used = 0;

  private room(count: number): void {
    if (this.used + count <= this.chunk.length) return;
    const grown = new Uint8Array(Math.max(this.chunk.length * 2, this.used + count));
    grown.set(this.chunk.subarray(0, this.used));
    this.chunk = grown;
  }

  byte(value: number): void {
    this.room(1);
    this.chunk[this.used++] = value & 0xff;
  }

  private short(value: number): void {
    this.byte(value >> 8);
    this.byte(value);
  }

  int(value: number): void {
    this.byte(value >> 24);
    this.byte(value >> 16);
    this.byte(value >> 8);
    this.byte(value);
  }

  private text(value: string): void {
    const encoded = new TextEncoder().encode(value);
    this.short(encoded.length);
    this.room(encoded.length);
    this.chunk.set(encoded, this.used);
    this.used += encoded.length;
  }

  private tag(type: number, name: string): void {
    this.byte(type);
    this.text(name);
  }

  namedInt(name: string, value: number): void {
    this.tag(3, name);
    this.int(value);
  }

  namedShort(name: string, value: number): void {
    this.tag(2, name);
    this.short(value);
  }

  namedString(name: string, value: string): void {
    this.tag(8, name);
    this.text(value);
  }

  namedBytes(name: string, value: Uint8Array): void {
    this.tag(7, name);
    this.int(value.length);
    this.room(value.length);
    this.chunk.set(value, this.used);
    this.used += value.length;
  }

  namedInts(name: string, values: number[]): void {
    this.tag(11, name);
    this.int(values.length);
    for (const value of values) this.int(value);
  }

  /** A list of compounds. An empty list is typed as "end", as the format requires. */
  namedCompoundList(name: string, count: number): void {
    this.tag(9, name);
    this.byte(count === 0 ? 0 : 10);
    this.int(count);
  }

  open(name: string): void {
    this.tag(10, name);
  }

  close(): void {
    this.byte(0);
  }

  finish(): Uint8Array {
    return this.chunk.subarray(0, this.used);
  }
}

/**
 * Rebuilds a schematic the renderer can parse, in memory.
 *
 * The renderer takes bytes in a format it knows, so the capture is translated into one
 * for it. The result is handed straight to the renderer. It is never requested from
 * anywhere, stored, or offered as a download, and it holds only what the capture held:
 * a shell, placed at the origin.
 */
export async function toSpongeSchematic(decoded: DecodedSchematic): Promise<ArrayBuffer> {
  const { width, height, length, palette, cells } = decoded;
  // The target format stores each dimension in sixteen bits.
  if (width > MAX_DIMENSION || height > MAX_DIMENSION || length > MAX_DIMENSION) {
    throw new UnreadableSchematicError(`too long on one axis: ${width}x${height}x${length}`);
  }

  // Two entries can share a state and differ by block entity. The target format keys
  // its palette on state alone, so they fold together there.
  const spongeIndex = new Map<string, number>();
  for (const entry of palette) {
    if (!spongeIndex.has(entry.state)) spongeIndex.set(entry.state, spongeIndex.size);
  }

  const data = new NbtWriter();
  const blockEntities: { x: number; y: number; z: number; id: string }[] = [];
  // The target format runs y outermost, then z, then x: the reverse of the capture.
  for (let y = 0; y < height; y++) {
    for (let z = 0; z < length; z++) {
      for (let x = 0; x < width; x++) {
        const entry = palette[cells[(x * length + z) * height + y]];
        let index = spongeIndex.get(entry.state) as number;
        while (index > 0x7f) {
          data.byte((index & 0x7f) | 0x80);
          index >>>= 7;
        }
        data.byte(index);
        if (entry.blockEntityId !== "") blockEntities.push({ x, y, z, id: entry.blockEntityId });
      }
    }
  }

  const nbt = new NbtWriter();
  nbt.open("");
  nbt.open("Schematic");
  nbt.namedInt("Version", 3);
  nbt.namedInt("DataVersion", decoded.dataVersion);
  nbt.namedShort("Width", width);
  nbt.namedShort("Height", height);
  nbt.namedShort("Length", length);
  nbt.namedInts("Offset", [0, 0, 0]);
  nbt.open("Blocks");
  nbt.open("Palette");
  for (const [state, index] of spongeIndex) nbt.namedInt(state, index);
  nbt.close();
  nbt.namedBytes("Data", data.finish());
  nbt.namedCompoundList("BlockEntities", blockEntities.length);
  for (const entity of blockEntities) {
    nbt.namedInts("Pos", [entity.x, entity.y, entity.z]);
    nbt.namedString("Id", entity.id);
    nbt.open("Data");
    nbt.namedString("id", entity.id);
    nbt.close();
    nbt.close();
  }
  nbt.close();
  nbt.close();
  nbt.close();

  // Gzipped because the renderer's parser refuses NBT that is not.
  const zipped = await through(nbt.finish(), new CompressionStream("gzip"));
  return zipped.buffer.slice(zipped.byteOffset, zipped.byteOffset + zipped.byteLength) as ArrayBuffer;
}

/** From the bytes the API served to bytes the renderer loads. */
export async function realtyToRenderable(buffer: ArrayBuffer): Promise<ArrayBuffer> {
  return toSpongeSchematic(await decodeRealtySchematic(buffer));
}
```

- [ ] **Step 5: Run the test and see it pass**

Run: `npx vitest run src/api/realtySchematic.test.ts`
Expected: every test passes.

`nucleation` initialised under Vitest when this plan was checked. If it does not for you, do not delete or weaken the two tests that use `SchematicWrapper`. They are the only proof in the suite that the renderer accepts the output. Report the exact error instead.

- [ ] **Step 6: Typecheck**

Run: `npm run typecheck`
Expected: no errors.

**Commit message:** `feat(explorer): decode captures in Realty's format`

---

### Task 10: Wire the explorer

> **Changed during execution.** The steps below check only the header in the region page and decode in the viewer. That left a gap: a capture with a valid header and a corrupt body mounted the viewer, which showed a label for two seconds and then an empty canvas. As built, the region page calls `realtyToRenderable` on the served bytes before it mounts the viewer, and shows the no-preview panel if that fails for any reason. The viewer is handed bytes that are already decoded, and decodes only when it fetches for itself. The code in the repository is the record; the steps below are kept as they were written.

**Files:**
- Modify: `realty-web/realty-explorer/src/viewer/SchematicViewer.tsx`
- Modify: `realty-web/realty-explorer/src/viewer/SchematicViewer.test.tsx`
- Modify: `realty-web/realty-explorer/src/screens/region/RegionScreen.tsx`
- Modify: `realty-web/realty-explorer/src/screens/region/RegionScreen.test.tsx`

**Interfaces:**
- Consumes: `isRealtySchematic`, `realtyToRenderable` from Task 9.
- `fetchSchematic` in `src/api/client.ts` is **not** changed. It returns the served bytes as they are. Its tests stay as they are.

- [ ] **Step 1: Read both components and both test files in full**

- [ ] **Step 2: Update the viewer's tests first**

In `SchematicViewer.test.tsx`, mock the decoder so the test asserts the wiring and not the format:

```ts
const renderableSpy = vi.fn(async (bytes: ArrayBuffer) => bytes);

vi.mock("../api/realtySchematic", () => ({
  realtyToRenderable: (bytes: ArrayBuffer) => renderableSpy(bytes),
}));
```

Clear `renderableSpy` in `beforeEach` with the others.

Change the test named "uses bytes it is handed rather than fetching the schematic again". It asserts `loaders["plot_a"]()` resolves to the very bytes passed in. Keep the assertion that nothing was fetched. Replace the identity assertion with:

```ts
await loaders["plot_a"]();
expect(renderableSpy).toHaveBeenCalledWith(bytes);
```

Add:

```ts
it("decodes what it fetched before the renderer sees it", async () => {
  // The API serves Realty's format, which the renderer cannot parse. Handing it the
  // served bytes draws nothing and reports a corrupt file.
  const served = new ArrayBuffer(8);
  const decoded = new ArrayBuffer(32);
  renderableSpy.mockResolvedValueOnce(decoded);
  const get = vi.fn(async () => ({ data: served, error: undefined }));

  render(<SchematicViewer client={({ GET: get }) as unknown as ApiClient} world="world" region="plot_a" />);
  await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

  const loaders = constructorSpy.mock.calls[0][1] as Record<string, () => Promise<ArrayBuffer>>;
  await expect(loaders["plot_a"]()).resolves.toBe(decoded);
  expect(renderableSpy).toHaveBeenCalledWith(served);
});
```

The existing client stub in that file answers every `GET` with an `ArrayBuffer`, including the resource-pack call. Check how `fetchResourcePacks` is satisfied in the existing tests and match it.

- [ ] **Step 3: Run and see the viewer tests fail**

Run: `npx vitest run src/viewer/SchematicViewer.test.tsx`
Expected: the changed test and the new test fail.

- [ ] **Step 4: Decode in the viewer's loader**

In `SchematicViewer.tsx`, import `realtyToRenderable` from `"../api/realtySchematic"` and replace the loader:

```tsx
{
  // The API serves a capture in Realty's own format. The renderer reads formats
  // it knows, so the capture is translated for it here, in memory.
  [region]: async () =>
    realtyToRenderable(schematic ?? await fetchSchematic(client, world, region)()),
},
```

Update the `schematic` prop's doc comment: the bytes are the served capture, not yet decoded.

- [ ] **Step 5: Update the screen's tests**

In `RegionScreen.test.tsx`, the test "hands the probed bytes to the viewer instead of fetching them twice" serves `new ArrayBuffer(8)`. After this task those bytes read as no capture. Serve bytes with the header:

```ts
const capture = Uint8Array.from([0x52, 0x4c, 0x54, 0x59, 1, 0, 0, 0]).buffer;
```

Add:

```ts
it("shows the no-preview panel for a capture it cannot read", async () => {
  // An API older than this site still serves WorldEdit files. Mounting the viewer on
  // one downloads 12 MB to draw an error.
  const sponge = Uint8Array.from([0x1f, 0x8b, 8, 0, 0, 0, 0, 0]).buffer;
  renderScreen(regionRoutes({ "/v1/region/schematic": sponge }));
  await waitFor(() => expect(screen.getByText(/no preview captured/i)).toBeInTheDocument());
  expect(screen.queryByTestId("viewer")).toBeNull();
});
```

- [ ] **Step 6: Check the header in the probe**

In `RegionScreen.tsx`, import `isRealtySchematic` and change the probe's success branch:

```tsx
.then((bytes) => {
  if (cancelled) return;
  // Only the header is checked here. Decoding waits for the viewer, which is
  // the part that is lazy-loaded; this is just enough to not mount it in vain.
  if (!isRealtySchematic(bytes)) {
    setPreview("absent");
    return;
  }
  setSchematic(bytes);
  setPreview("present");
})
```

- [ ] **Step 7: Run everything**

Run: `npm test` and `npm run typecheck`
Expected: every test passes except the two known Node 26 failures named in Global Constraints, if running on Node 26.

- [ ] **Step 8: Confirm the main bundle did not grow a renderer**

Run: `npm run build`
Expected: success. In the output, `realtySchematic` must not appear in the chunk that holds `schematic-renderer`, and the main chunk's size must not have grown by megabytes. Report the main chunk's size before and after.

**Commit message:** `feat(explorer): render captures from Realty's format`

---

### Task 11: Documentation

**Files:**
- Modify: `README.md`
- Modify: `realty-web/README.md`
- Modify: `realty-web/realty-rest/README.md`
- Modify: `realty-web/realty-rest/src/main/resources/openapi.yaml`
- Regenerate: `realty-web/realty-explorer/src/api/schema.d.ts`

**Audience:** server operators deciding whether to enable capture, and developers calling the API. Both need to know what the endpoint gives out. Neither should come away thinking the format is a security boundary.

- [ ] **Step 1: Find every stale statement**

Run: `grep -rn -i "sponge\|worldedit schematic\|\.schem\b" README.md realty-web/README.md realty-web/realty-rest/README.md realty-web/realty-rest/src/main/resources/openapi.yaml realty-paper/src/main realty-backend/src/main realty-backend-api/src/main realty-web/realty-rest/src/main realty-web/realty-explorer/src`

Every hit that describes what is stored or served must be corrected. Hits inside `realtySchematic.ts` that describe the in-memory rebuild are correct and stay. The root README's dependency table says WorldEdit provides "the clipboard API behind `/realty schematic capture`". That is still true and stays.

- [ ] **Step 2: Root `README.md`, section "Schematic capture"**

Keep the permissions table. Replace the opening paragraphs so they state, in this order:

1. What the command does: it takes a snapshot for the web preview.
2. What is stored: only blocks visible from outside the region, with no world position, in a format of Realty's own.
3. What that means in practice, as a short list: interiors are not captured; rooms behind glass are not captured, though a block directly against a window is; the capture cannot be loaded by WorldEdit or Litematica; it cannot be used to back up or restore a region.
4. The limit, in one plain sentence: the web preview has to decode the capture to draw it, so someone determined can recover the exterior shell, which is what any player standing outside can already see.
5. Upgrading: captures from before this version are deleted on first start and each region must be captured again.

- [ ] **Step 3: `realty-web/realty-rest/README.md`**

Rewrite the `GET /v1/region/schematic` entry. It returns a capture in Realty's own binary format as `application/octet-stream`. Rows in any other format answer `404 SCHEMATIC_NOT_FOUND`. Point to the format description in this plan's **Wire Format** section by copying that section into the README under a heading of its own. A third party who wants to draw previews needs it, and publishing it costs nothing the JavaScript does not already give away.

- [ ] **Step 4: `openapi.yaml`**

Rewrite the `description` of `/v1/region/schematic`. Remove "raw Sponge Schematic v3 bytes". Keep the paragraph on why the response is bytes and not JSON. Keep the statement that block entity contents are never captured. Add that a capture in an unrecognised format is a 404.

Change no path, parameter, status code or schema.

- [ ] **Step 5: Regenerate the client types**

Run, in `realty-web/realty-explorer`: `npm run generate:api`
Then: `git diff --stat src/api/schema.d.ts`
Expected: changes to comments only. If a type changed, the OpenAPI edit went too far. Undo it.

- [ ] **Step 6: `realty-web/README.md`**

Wherever the preview is described, add that it shows the outside of a build only.

- [ ] **Step 7: Verify**

Run: `./gradlew :realty-web:realty-rest:test` and, in the explorer, `npm run typecheck`
Expected: pass. `realty-rest` has tests that read `openapi.yaml`.

**Commit message:** `docs: describe what a capture now holds, and what it does not`

---

### Task 12: Whole-build verification

Run by the orchestrator, not delegated.

- [ ] **Step 1:** `./gradlew build`. Expected: BUILD SUCCESSFUL. Compare failures with the baseline.
- [ ] **Step 2:** In `realty-web/realty-explorer`: `npm test`, `npm run typecheck`, `npm run build`. Compare with the baseline.
- [ ] **Step 3:** Confirm no server path can write a WorldEdit file:

```bash
grep -rn "BuiltInClipboardFormat\|ClipboardWriter\|SPONGE\|RegionSchematicWriter" --include="*.java" \
  realty-paper/src/main realty-backend/src/main realty-backend-api/src/main realty-web/realty-rest/src/main
```

Expected: no output.

- [ ] **Step 4:** Confirm the two copies of the golden fixture are identical:

```bash
grep -rho "UkxUWQF4[A-Za-z0-9+/=\" +]*" realty-paper/src/test realty-web/realty-explorer/src | tr -d '" +\n' | fold -w 300 | sort -u
```

Expected: one line.

- [ ] **Step 5:** From the top of the stack, confirm only planned files changed: `git diff --stat main...HEAD` lists nothing outside **File Structure** and this plan.
- [ ] **Step 5a:** Confirm each layer builds alone. For each of the six branches, `gh stack checkout <branch>`, then run `./gradlew build` for layers 1, 3 and 4, and `npm test` with `npm run typecheck` for layers 2 and 5. Finish with `gh stack top`.

- [ ] **Step 6: What cannot be verified here.** Nothing in this plan runs a Minecraft server. The following are unverified until someone captures a region on a real server, and the final report must say so:

  - That `BlockMaterial#isFullCube` and `isOpaque` give sensible answers from the live platform adapter.
  - That a real build's preview looks right after the cull.
  - That the renderer draws the rebuilt schematic, as opposed to parsing it, which the tests do prove.
  - How long the cull and encode take on a real build. On a synthetic million-block region they took under 100 ms together, once per capture, off the main thread. A real build has a larger palette and may be slower.

---

## Rollout

For whoever deploys this. Order matters.

0. **Merge the stack as a whole.** Layers 3 to 5 each leave previews broken if released alone: the plugin would write a format the site cannot yet read. Nothing leaks in that state, but nothing draws either.
1. **Deploy the plugin first.** On start it runs migration 18 and deletes every existing capture. From then on it writes the new format.
2. **Deploy `realty-rest` and the explorer together, straight after.** The old REST build refuses to start against schema 18, so the API is down between steps 1 and 2.
3. **Recapture regions in game.** Until a region is recaptured its page shows "No preview captured yet".

Local development against the production database will not start until step 1 has happened there, because the REST build expects schema 18.

There is no rollback that restores the deleted captures. That was decided: captures are never restored.

---

## Self-Review

**Coverage of what was promised to the owner:**

| Promise | Task |
|---|---|
| The plugin writes a format of its own, not a WorldEdit file | 4, 6 |
| No WorldEdit-loadable file in the database, on the wire, or in downloads | 6, 7, 8, 12 step 3 |
| No world position | 2, 5 (grid has no field for one), 4 and 9 (tested) |
| Only the properties that are drawn | 2 |
| Hollow shell | 3 |
| Rooms behind glass hidden | 3, `glassShowsTheBlockBehindItAndNothingFurtherIn` |
| Private layout: header, version, palette, compressed runs in own order | 1, 4 |
| Decoder in the explorer | 9, 10 |
| Old captures deleted | 7 |
| Explorer refuses what it cannot read | 9, 10 |
| No restorable copy kept | 4 (decoder is test-only), 6 (writer deleted) |

**An open build is captured inside.** Open air carries the view in every direction, so a doorway with no door lets the rooms behind it be captured. "Interiors are not captured" is true of a closed build only. The READMEs say so.

**Two departures from what was said in conversation, both deliberate:**

1. *"States such as powered or waterlogged are dropped."* `waterlogged` is **kept**, because the water is drawn and dropping it changes the preview. `powered` is dropped only on doors, trapdoors and fence gates, because on levers, buttons and rails it is the model. Note-block states are kept because resource packs retexture them. The owner should be told.
2. *"Keep only blocks touching outside air."* Replaced by the rule in **Shell Cull Rule**, after the prototype showed the simple rule deletes the block under every stair and flower.

**Placeholder scan:** no TBDs. Task 5 step 1, Task 6 step 1 and Task 10 step 1 tell the implementer to read specific files first. Those are instructions to check real code, not deferred decisions.

**Consistency:** `BlockGrid.index` is `(x * length + z) * height + y` in Task 2, in the loop order of Tasks 3 and 5, in the encoder's scan in Task 4, and in the decoder's lookup in Task 9. `PaletteEntry` has three components in Java and two on the wire; Task 4 tests that the third is not written. The header is `R L T Y 1` in Tasks 1, 8, 9 and 10. `isReadable` is the Java name and `isRealtySchematic` the TypeScript name for the same check. The golden fixture is the same string in Tasks 4 and 9, checked in Task 12.
