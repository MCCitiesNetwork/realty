# Per-Kind Party Tables Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single `Party` table with nullable columns and a shape check by a base `Party` table plus one table per kind, inside the stage 1 stack, so that the stack never contains the single-table design.

**Architecture:** A party is a `Party (partyId, kind)` row plus one row in `PersonalParty`, `AccountParty` or `GroupParty`, tied by a composite `(partyId, kind)` foreign key. Contracts and history keep pointing at `Party(partyId)`. Reads left-join the three kind tables and one shared MyBatis result map switches on the kind found; `MariaPartyMapper` is the only writer and inserts the base row (`INSERT … RETURNING partyId`, the repo's convention) then the kind row, in one transaction. A group points at its account's own party row.

**Tech Stack:** MariaDB 11.7 (testcontainers in tests), MyBatis annotations, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-29-treasury-parties-design.md`, sections *Schema (migration V19)*, *Entities and mappers*, *Upgrade*, *Testing*.

## Global Constraints

- The stack is `feat/treasury-parties-spec` → `feat/party-model` (#56) → `feat/party-payments` (#57) → `feat/party-authorization` (#58) → `feat/party-commands` (#59) → `feat/party-notifications` (#60) → `feat/party-rest` (#61) → `feat/party-review-fixes` (#62) → `feat/party-listing-parts` (#63). Each branch must compile and pass its tests on its own after this work.
- The change lands **inside #56**: every commit on `feat/party-model` that creates or reads the party tables is amended by a `git commit --fixup=<sha>` folded in with `git rebase -i --autosquash`, so no commit in the stack ever creates the single-table `Party`. Upper branches are rebased and their own commits fixed up the same way where they touch party rows.
- Commit messages: conventional, plain sentences (see `git log`), ending with a blank line and `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Never push. The owner pushes with `--force-with-lease`.
- `partyId` is `INT AUTO_INCREMENT` and never leaves the backend.
- Every child `kind` column is the same `ENUM ('PERSONAL','ACCOUNT','GROUP')` as `Party.kind` (InnoDB compares enum foreign keys by position) with a one-value `CHECK`.
- Docker Desktop must be running for the `realty-backend` tests (testcontainers MariaDB). Run backend tests with `./gradlew :realty-backend:test --tests '<pattern>'`; the full module takes ~2 minutes.
- Work in a worktree per branch (`git worktree add <dir> <branch>`), never on the main checkout, which stays on `feat/treasury-parties-spec`.

## Review Focus

1. **Two commands create the same new player party at the same moment.** Both must succeed and exactly one base row and one `PersonalParty` row exist afterwards. Pinned by `ConcurrencyTest.NewParty` (Task 9) after `partyIdCreatingIfAbsent` learns to roll back and re-read.
2. **A base `Party` row without a kind row** (only possible through hand-written SQL). A contract read joins to nothing and the discriminator meets a `NULL` kind. Pinned in Task 3: `selectById` on such a row returns `null` rather than throwing, and the manual fix script in Task 10 never leaves one behind (its two inserts are guarded by the same condition).
3. **`/realty group map` for an account that already has a party row** (because a contract names it, or another group maps to it). No second `AccountParty` row may be created, and the group must point at the existing one. Pinned by `PartyMapperTest.insertGroup_reusesTheAccountParty` (Task 4).
4. **The manual fix run twice for the same legacy UUID, or for an account stored under another kind.** It must move nothing and say so, as the release notes promise. Pinned by `PartiesMigrationTest.manualFix_*` (Task 10).
5. **A database that already lacks one of the two old indexes.** V19 must still complete. Kept by `PartiesMigrationTest.missingOldIndex_doesNotStopTheMigration` (Task 2), which moves into #56 with the `DROP INDEX IF EXISTS` it tests.

---

## Working method

Tasks 1–6 are done on `feat/party-model` (#56). Tasks 7–10 are done on the upper branch named in each, after rebasing it. Task 11 folds everything in and rebases the rest of the stack. Within a branch, make each change as `git commit --fixup=<target sha>`, where the target is the commit that introduced the code being changed; find it with `git log --format='%h %s' feat/treasury-parties-spec..HEAD -S'<distinctive snippet>'`. The known targets on `feat/party-model`:

| Change | Target commit | Subject |
|---|---|---|
| `V19__parties.sql`, `PartiesMigrationTest` | `b9816d0` | feat(party): store parties in their own table (V19) |
| `PartySql`, `MariaPartyMapper`, `PartyMapperTest`, its `insertGroupRow` | `7251c01` | feat(party): add the party mapper and one shared result mapping |
| joins in the leasehold mappers and `MapperTest` group seeds | `92a359a` | refactor(lease): the landlord of a lease is a party |
| joins in the freehold mappers | `d1cafe9` | refactor(freehold): the authority of a freehold is a party |

(SHAs are those at the time of writing; re-check with `git log` because Task 11 rewrites them.)

Until Task 3 is complete the mapper tests fail against the new tables; that is expected. Run only the tests each task names.

---

### Task 1: V19 creates the four tables and migrates the old UUIDs into them

**Files:**
- Modify: `realty-backend/src/main/resources/sql/migrations/V19__parties.sql`
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/PartiesMigrationTest.java`

**Interfaces:**
- Produces: tables `Party(partyId, kind)`, `PersonalParty(partyId, kind, playerUuid)`, `AccountParty(partyId, kind, accountId, accountKind)`, `GroupParty(partyId, kind, groupName, accountPartyId)` exactly as in the spec; the four contract/history columns and foreign keys unchanged in name.

- [ ] **Step 1: Set up the worktree**

```bash
cd "$(git rev-parse --show-toplevel)"
git fetch origin
git worktree add ../realty-party-model feat/party-model
cd ../realty-party-model
```

- [ ] **Step 2: Rewrite the migration tests for the new tables**

In `PartiesMigrationTest`, replace the body of `everyOldUuidBecomesOnePersonalParty` so it checks both tables:

```java
    @Test
    void everyOldUuidBecomesOnePersonalParty() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            Assertions.assertEquals(2, count(statement, "SELECT COUNT(*) FROM Party WHERE kind = 'PERSONAL'"),
                    "player A (landlord + authority) and player B (history landlord) should collapse to two PERSONAL parties");
            Assertions.assertEquals(2, count(statement, "SELECT COUNT(*) FROM Party"),
                    "no non-PERSONAL party should exist from the migration alone");
            Assertions.assertEquals(2, count(statement, """
                    SELECT COUNT(*) FROM Party p JOIN PersonalParty pp ON pp.partyId = p.partyId
                    """), "every base row should have its PersonalParty row");
            Assertions.assertEquals(0, count(statement, "SELECT COUNT(*) FROM AccountParty"));
            Assertions.assertEquals(0, count(statement, "SELECT COUNT(*) FROM GroupParty"));
        }
    }

    private static int count(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
```

In `repointedRowsMatchTheirOriginals`, change every `JOIN Party p ON p.partyId = …` to `JOIN PersonalParty p ON p.partyId = …` (four places).

Replace `shapeCheckRejectsMalformedRows` and `assertShapeRejected` with three tests:

```java
    /** The composite (partyId, kind) key: a kind row can only attach to a base row of its own kind. */
    @Test
    void aKindRowCannotAttachToABaseRowOfAnotherKind() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int personalId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('PERSONAL') RETURNING partyId");
                assertRejected(statement, """
                        INSERT INTO AccountParty (partyId, accountId, accountKind) VALUES (%d, 42, 'GOVERNMENT')
                        """.formatted(personalId), "an AccountParty row on a PERSONAL base row");
                assertRejected(statement, """
                        INSERT INTO GroupParty (partyId, groupName, accountPartyId) VALUES (%d, 'police', %d)
                        """.formatted(personalId, personalId), "a GroupParty row on a PERSONAL base row");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void aGroupCannotPointAtAPartyThatIsNotAnAccount() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int personalId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('PERSONAL') RETURNING partyId");
                statement.executeUpdate("INSERT INTO PersonalParty (partyId, playerUuid) VALUES (%d, '3a1c88f0-0000-0000-0000-0000000000b1')"
                        .formatted(personalId));
                int groupId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('GROUP') RETURNING partyId");
                assertRejected(statement, """
                        INSERT INTO GroupParty (partyId, groupName, accountPartyId) VALUES (%d, 'police', %d)
                        """.formatted(groupId, personalId), "a group pointing at a personal party");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void aChildKindCannotBeAnythingButItsOwn() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int accountId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('ACCOUNT') RETURNING partyId");
                assertRejected(statement, """
                        INSERT INTO PersonalParty (partyId, kind, playerUuid)
                        VALUES (%d, 'ACCOUNT', '3a1c88f0-0000-0000-0000-0000000000b2')
                        """.formatted(accountId), "a PersonalParty row whose kind says ACCOUNT");
            } finally {
                connection.rollback();
            }
        }
    }

    private static void assertRejected(Statement statement, String sql, String label) {
        Assertions.assertThrows(SQLException.class, () -> statement.executeUpdate(sql),
                "the database should reject " + label);
    }
```

Rewrite `twoGroupsMayShareOneAccount` to insert the account's rows once and two groups pointing at it:

```java
    @Test
    void twoGroupsMayShareOneAccount() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int accountPartyId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('ACCOUNT') RETURNING partyId");
                statement.executeUpdate("INSERT INTO AccountParty (partyId, accountId, accountKind) VALUES (%d, 42, 'GOVERNMENT')"
                        .formatted(accountPartyId));
                for (String group : List.of("police", "rangers")) {
                    int groupId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('GROUP') RETURNING partyId");
                    statement.executeUpdate("INSERT INTO GroupParty (partyId, groupName, accountPartyId) VALUES (%d, '%s', %d)"
                            .formatted(groupId, group, accountPartyId));
                }
                Assertions.assertEquals(2, count(statement,
                        "SELECT COUNT(*) FROM GroupParty WHERE accountPartyId = " + accountPartyId));
            } finally {
                connection.rollback();
            }
        }
    }
```

Keep `newPartyColumnsAreNotNull`, `everyPartyColumnHasAForeignKey`, `missingOldIndex_doesNotStopTheMigration`, `oldColumnsAndIndexesAreGone`, `historyTenantMayBeNull`, `freeholdHistoryBuyerMayBeNull` as they are.

- [ ] **Step 3: Run the migration tests to see them fail**

Run: `./gradlew :realty-backend:test --tests '*PartiesMigrationTest*'`
Expected: FAIL — `PersonalParty` does not exist (`repointedRowsMatchTheirOriginals`, `everyOldUuidBecomesOnePersonalParty`), and the three constraint tests fail because the inserts are accepted or the tables are missing.

- [ ] **Step 4: Rewrite V19**

Replace the `CREATE TABLE Party … ;` block and the `INSERT INTO Party (kind, playerUuid) …` statement with:

```sql
CREATE TABLE Party (
    partyId INT PRIMARY KEY AUTO_INCREMENT,
    kind    ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL,
    UNIQUE (partyId, kind)
);

-- Each child's kind column is the same enum as Party.kind: InnoDB compares enum foreign keys by
-- position, so a one-value enum here would not match. The CHECK pins the child to its own kind, and
-- the composite key then lets a kind row attach only to a base row of that kind.
CREATE TABLE PersonalParty (
    partyId    INT PRIMARY KEY,
    kind       ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'PERSONAL' CHECK (kind = 'PERSONAL'),
    playerUuid UUID NOT NULL UNIQUE,
    CONSTRAINT fk_personal_party FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind)
);

CREATE TABLE AccountParty (
    partyId     INT PRIMARY KEY,
    kind        ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'ACCOUNT' CHECK (kind = 'ACCOUNT'),
    accountId   INT NOT NULL UNIQUE,
    accountKind ENUM ('BUSINESS','GOVERNMENT','SYSTEM') NOT NULL,
    CONSTRAINT fk_account_party FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind)
);

CREATE TABLE GroupParty (
    partyId        INT PRIMARY KEY,
    kind           ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'GROUP' CHECK (kind = 'GROUP'),
    groupName      VARCHAR(64) NOT NULL UNIQUE,
    accountPartyId INT NOT NULL,
    CONSTRAINT fk_group_party FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind),
    CONSTRAINT fk_group_party_account FOREIGN KEY (accountPartyId) REFERENCES AccountParty (partyId)
);

-- A set-based insert cannot hand back one generated id per row, so the distinct UUIDs are numbered
-- in a temporary table first and inserted into Party with those ids. AUTO_INCREMENT continues
-- after the highest one. The temporary table lives on this connection only.
CREATE TEMPORARY TABLE OldParty (
    partyId    INT PRIMARY KEY AUTO_INCREMENT,
    playerUuid UUID NOT NULL UNIQUE
);
INSERT INTO OldParty (playerUuid)
SELECT u FROM (
    SELECT landlordId AS u FROM LeaseholdContract
    UNION SELECT landlordId FROM LeaseholdHistory
    UNION SELECT authorityId FROM FreeholdContract
    UNION SELECT authorityId FROM FreeholdHistory
) AS oldParties;
INSERT INTO Party (partyId, kind) SELECT partyId, 'PERSONAL' FROM OldParty;
INSERT INTO PersonalParty (partyId, playerUuid) SELECT partyId, playerUuid FROM OldParty;
DROP TEMPORARY TABLE OldParty;
```

Then in the four `UPDATE … JOIN Party p ON p.playerUuid = …` statements change `Party p` to `PersonalParty p`. Leave the rest of the file (column adds, `DROP INDEX IF EXISTS`, foreign keys, nullable `tenantId`/`buyerId`) as it is. Update the header comment's last paragraph: replace "introduces Party, a first-class row a contract can point to" with "introduces Party, a base row that gives each party its id and kind, and one table per kind for its details".

- [ ] **Step 5: Run the migration tests to see them pass**

Run: `./gradlew :realty-backend:test --tests '*PartiesMigrationTest*'`
Expected: all 12 PASS.

- [ ] **Step 6: Commit as a fix-up of the V19 commit**

```bash
git add realty-backend/src/main/resources/sql/migrations/V19__parties.sql \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/PartiesMigrationTest.java
git commit --fixup=$(git log --format=%h -1 -S'CREATE TABLE Party' --reverse feat/treasury-parties-spec..HEAD | head -1)
```

---

### Task 2: `PartySql` joins and column lists for the kind tables

**Files:**
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/PartySql.java`

**Interfaces:**
- Produces (all `static final String`, usable inside `@Select` text blocks):
  - `LANDLORD_JOINS_CONTRACT` — joins for `lc.landlordPartyId`, aliases `lpp`, `lap`, `lgp`, `lga`
  - `LANDLORD_JOINS_HISTORY` — same for `lh.landlordPartyId`
  - `AUTHORITY_JOINS_CONTRACT` — for `fc.authorityPartyId`, aliases `app`, `aap`, `agp`, `aga`
  - `AUTHORITY_JOINS_HISTORY` — for `fh.authorityPartyId`
  - `LANDLORD_COLUMNS`, `AUTHORITY_COLUMNS`, `LANDLORD_AS_SECOND_COLUMNS`, `AUTHORITY_AS_SECOND_COLUMNS`, `NULL_SECOND_ACCOUNT_COLUMNS` — select lists under the prefixes `landlord_`, `authority_`, `second_`, with the columns `kind, playerUuid, accountId, accountKind, groupName, groupAccountId, groupAccountKind`
  - `LANDLORD_IS_PLAYER` = `"lpp.playerUuid = #{playerId}"`, `AUTHORITY_IS_PLAYER` = `"app.playerUuid = #{playerId}"` for the history providers' player filter

No test of its own; Task 3's result-map tests and Task 5's mapper tests exercise every constant.

- [ ] **Step 1: Replace the file's constants**

Keep the class javadoc (update "joins `Party`" to "joins the party kind tables"), `RESULT_MAP` and `SELECT_BY_ID`. Replace everything from `LANDLORD_COLUMNS` down with:

```java
    // The three kind tables left-joined on a party id, plus the account behind a group. Exactly one
    // of pp, ap, gp matches; the base table is not joined. The prefix letter (l or a) keeps the
    // landlord's and the authority's aliases apart in one statement. Written out four times rather
    // than built by a method: an annotation value must be a compile-time constant.

    static final String LANDLORD_JOINS_CONTRACT =
            " LEFT JOIN PersonalParty lpp ON lpp.partyId = lc.landlordPartyId"
                    + " LEFT JOIN AccountParty lap ON lap.partyId = lc.landlordPartyId"
                    + " LEFT JOIN GroupParty lgp ON lgp.partyId = lc.landlordPartyId"
                    + " LEFT JOIN AccountParty lga ON lga.partyId = lgp.accountPartyId ";

    static final String LANDLORD_JOINS_HISTORY =
            " LEFT JOIN PersonalParty lpp ON lpp.partyId = lh.landlordPartyId"
                    + " LEFT JOIN AccountParty lap ON lap.partyId = lh.landlordPartyId"
                    + " LEFT JOIN GroupParty lgp ON lgp.partyId = lh.landlordPartyId"
                    + " LEFT JOIN AccountParty lga ON lga.partyId = lgp.accountPartyId ";

    static final String AUTHORITY_JOINS_CONTRACT =
            " LEFT JOIN PersonalParty app ON app.partyId = fc.authorityPartyId"
                    + " LEFT JOIN AccountParty aap ON aap.partyId = fc.authorityPartyId"
                    + " LEFT JOIN GroupParty agp ON agp.partyId = fc.authorityPartyId"
                    + " LEFT JOIN AccountParty aga ON aga.partyId = agp.accountPartyId ";

    static final String AUTHORITY_JOINS_HISTORY =
            " LEFT JOIN PersonalParty app ON app.partyId = fh.authorityPartyId"
                    + " LEFT JOIN AccountParty aap ON aap.partyId = fh.authorityPartyId"
                    + " LEFT JOIN GroupParty agp ON agp.partyId = fh.authorityPartyId"
                    + " LEFT JOIN AccountParty aga ON aga.partyId = agp.accountPartyId ";

    /** The party columns of the joins with prefix letter {@code l}, aliased under {@code landlord_}. */
    static final String LANDLORD_COLUMNS =
            " COALESCE(lpp.kind, lap.kind, lgp.kind) AS landlord_kind, lpp.playerUuid AS landlord_playerUuid,"
                    + " lap.accountId AS landlord_accountId, lap.accountKind AS landlord_accountKind,"
                    + " lgp.groupName AS landlord_groupName,"
                    + " lga.accountId AS landlord_groupAccountId, lga.accountKind AS landlord_groupAccountKind ";

    /** The party columns of the joins with prefix letter {@code a}, aliased under {@code authority_}. */
    static final String AUTHORITY_COLUMNS =
            " COALESCE(app.kind, aap.kind, agp.kind) AS authority_kind, app.playerUuid AS authority_playerUuid,"
                    + " aap.accountId AS authority_accountId, aap.accountKind AS authority_accountKind,"
                    + " agp.groupName AS authority_groupName,"
                    + " aga.accountId AS authority_groupAccountId, aga.accountKind AS authority_groupAccountKind ";

    /** The landlord's columns under the {@code second_} prefix the activity feed reads its second party with. */
    static final String LANDLORD_AS_SECOND_COLUMNS =
            " COALESCE(lpp.kind, lap.kind, lgp.kind) AS second_kind, lpp.playerUuid AS second_playerUuid,"
                    + " lap.accountId AS second_accountId, lap.accountKind AS second_accountKind,"
                    + " lgp.groupName AS second_groupName,"
                    + " lga.accountId AS second_groupAccountId, lga.accountKind AS second_groupAccountKind ";

    /** The authority's columns under the {@code second_} prefix. */
    static final String AUTHORITY_AS_SECOND_COLUMNS =
            " COALESCE(app.kind, aap.kind, agp.kind) AS second_kind, app.playerUuid AS second_playerUuid,"
                    + " aap.accountId AS second_accountId, aap.accountKind AS second_accountKind,"
                    + " agp.groupName AS second_groupName,"
                    + " aga.accountId AS second_groupAccountId, aga.accountKind AS second_groupAccountKind ";

    /**
     * The non-player columns of a party under the {@code second_} prefix, all {@code NULL}. A branch
     * of the activity feed whose second party is always a player selects {@code 'PERSONAL' AS
     * second_kind} and the player's UUID {@code AS second_playerUuid}, then this.
     */
    static final String NULL_SECOND_ACCOUNT_COLUMNS =
            " CAST(NULL AS SIGNED) AS second_accountId, CAST(NULL AS CHAR(16)) AS second_accountKind,"
                    + " CAST(NULL AS CHAR(64)) AS second_groupName,"
                    + " CAST(NULL AS SIGNED) AS second_groupAccountId, CAST(NULL AS CHAR(16)) AS second_groupAccountKind ";

    /** The landlord is this player: only a personal party has a UUID, so no kind test is needed. */
    static final String LANDLORD_IS_PLAYER = "lpp.playerUuid = #{playerId}";

    /** The authority is this player. */
    static final String AUTHORITY_IS_PLAYER = "app.playerUuid = #{playerId}";
```

- [ ] **Step 2: Compile**

Run: `./gradlew :realty-backend:compileJava -q`
Expected: errors only in the mappers still using the old names (fixed in Task 5), or none if the old constants were kept by name. If the build fails on `PartySql` itself, fix that before going on.

- [ ] **Step 3: Commit as a fix-up of the mapper commit**

```bash
git add realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/PartySql.java
git commit --fixup=$(git log --format=%h -1 -S'RESULT_MAP' --reverse feat/treasury-parties-spec..HEAD | head -1)
```

---

### Task 3: The shared result map reads the kind tables

**Files:**
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaPartyMapper.java` (`selectById`, `selectNonPersonal`)
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/PartyMapperTest.java`

**Interfaces:**
- Consumes: the tables from Task 1.
- Produces: result map id `party` (unchanged name) with discriminator cases `PERSONAL`, `ACCOUNT`, `GROUP` reading columns `kind, playerUuid, accountId, accountKind, groupName, groupAccountId, groupAccountKind`; `Party selectById(int)` returning `null` for an unknown id or a base row with no kind row.

- [ ] **Step 1: Add a test for a base row with no kind row**

In `PartyMapperTest`, add:

```java
    /** Hand-written SQL could leave a base row without its kind row; reading it must not throw. */
    @Test
    void selectById_baseRowWithoutKindRow_isNull() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true);
             Statement statement = wrapper.session().getConnection().createStatement()) {
            int orphanId;
            try (ResultSet rs = statement.executeQuery("INSERT INTO Party (kind) VALUES ('PERSONAL') RETURNING partyId")) {
                rs.next();
                orphanId = rs.getInt(1);
            }
            Assertions.assertNull(wrapper.partyMapper().selectById(orphanId));
        }
    }
```

- [ ] **Step 2: Run the result-map tests to see them fail**

Run: `./gradlew :realty-backend:test --tests '*PartyMapperTest.selectById*'`
Expected: FAIL — the old `SELECT … FROM Party` names columns that no longer exist.

- [ ] **Step 3: Rewrite `selectById` and `selectNonPersonal`**

```java
    @Override
    @Select("""
            SELECT COALESCE(pp.kind, ap.kind, gp.kind) AS kind, pp.playerUuid,
                   ap.accountId, ap.accountKind, gp.groupName,
                   ga.accountId AS groupAccountId, ga.accountKind AS groupAccountKind
            FROM Party p
            LEFT JOIN PersonalParty pp ON pp.partyId = p.partyId
            LEFT JOIN AccountParty ap ON ap.partyId = p.partyId
            LEFT JOIN GroupParty gp ON gp.partyId = p.partyId
            LEFT JOIN AccountParty ga ON ga.partyId = gp.accountPartyId
            WHERE p.partyId = #{partyId}
            """)
    @Results(id = "party")
    @TypeDiscriminator(column = "kind", javaType = String.class, cases = {
            @Case(value = "PERSONAL", type = Party.Personal.class, constructArgs = {
                    @Arg(column = "playerUuid", javaType = UUID.class)}),
            @Case(value = "ACCOUNT", type = Party.Account.class, constructArgs = {
                    @Arg(column = "accountId", javaType = int.class),
                    @Arg(column = "accountKind", javaType = AccountKind.class)}),
            @Case(value = "GROUP", type = Party.Group.class, constructArgs = {
                    @Arg(column = "groupName", javaType = String.class),
                    @Arg(column = "groupAccountId", javaType = int.class),
                    @Arg(column = "groupAccountKind", javaType = AccountKind.class)})
    })
    @Nullable Party selectById(@Param("partyId") int partyId);

    @Override
    @Select("""
            SELECT COALESCE(pp.kind, ap.kind, gp.kind) AS kind, pp.playerUuid,
                   ap.accountId, ap.accountKind, gp.groupName,
                   ga.accountId AS groupAccountId, ga.accountKind AS groupAccountKind
            FROM Party p
            LEFT JOIN PersonalParty pp ON pp.partyId = p.partyId
            LEFT JOIN AccountParty ap ON ap.partyId = p.partyId
            LEFT JOIN GroupParty gp ON gp.partyId = p.partyId
            LEFT JOIN AccountParty ga ON ga.partyId = gp.accountPartyId
            WHERE p.kind <> 'PERSONAL'
            ORDER BY p.partyId
            """)
    @ResultMap("party")
    @NotNull List<Party> selectNonPersonal();
```

A `NULL` kind matches no case, and MyBatis then builds nothing, so `selectById` returns `null` for an orphan base row; `selectNonPersonal` would return a `null` element for one, which is acceptable because only hand-written SQL can create it.

- [ ] **Step 4: Run the result-map tests**

Run: `./gradlew :realty-backend:test --tests '*PartyMapperTest.selectById*'`
Expected: `selectById_unknownIsNull` and `selectById_baseRowWithoutKindRow_isNull` PASS; `selectById_buildsEachKind` still FAILS because `findOrInsert` and `insertGroupRow` write the old columns (Task 4).

- [ ] **Step 5: Commit as a fix-up of the mapper commit**

```bash
git add realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaPartyMapper.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/PartyMapperTest.java
git commit --fixup=$(git log --format=%h -1 -S'RESULT_MAP' --reverse feat/treasury-parties-spec..HEAD | head -1)
```

---

### Task 4: `MariaPartyMapper` writes a base row and a kind row

**Files:**
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaPartyMapper.java` (everything except the two selects from Task 3)
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/PartyMapper.java` (javadoc of `insertGroup`, `updateGroupAccount`)
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/GroupMappingRow.java` (javadoc only)
- Create: `realty-backend/src/test/java/io/github/md5sha256/realty/database/TestParties.java`
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/PartyMapperTest.java`

**Interfaces:**
- Consumes: `PartyAccountRow(int partyId, AccountKind kind)` unchanged.
- Produces on `MariaPartyMapper`: `int insertBase(String kind)` (returns the new id via `RETURNING`), `int insertPersonal(int partyId, UUID playerUuid)`, `int insertAccount(int partyId, int accountId, AccountKind accountKind)`, `int insertGroupRow(int partyId, String groupName, int accountPartyId)`; `int insertGroup(String, int, AccountKind)` now returns the group's party id; `PartyMapper.findOrInsert` throws `org.apache.ibatis.exceptions.PersistenceException` when the kind row's unique key is violated (a race), which Task 6 handles.
- Produces for tests: `TestParties.insertGroup(Connection, String groupName, int accountId, AccountKind)` → the group's party id, creating the account's party if absent; `TestParties.insertAccount(Connection, int accountId, AccountKind)` → the account's party id.

- [ ] **Step 1: Write the test helper**

```java
package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Writes party rows the way the mapper does, for tests that need a party the backend does not
 * create for them: a mapped group before the group commands exist, or an account party on its own.
 */
final class TestParties {

    private TestParties() {
    }

    /** The account's party id, creating its base and kind rows if it has none. */
    static int insertAccount(Connection connection, int accountId, AccountKind accountKind) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT partyId FROM AccountParty WHERE accountId = " + accountId)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            int partyId = insertBase(statement, "ACCOUNT");
            statement.executeUpdate("INSERT INTO AccountParty (partyId, accountId, accountKind) VALUES (%d, %d, '%s')"
                    .formatted(partyId, accountId, accountKind.name()));
            return partyId;
        }
    }

    /** The new group's party id; the group points at the account's party, created if absent. */
    static int insertGroup(Connection connection, String groupName, int accountId, AccountKind accountKind)
            throws SQLException {
        int accountPartyId = insertAccount(connection, accountId, accountKind);
        try (Statement statement = connection.createStatement()) {
            int partyId = insertBase(statement, "GROUP");
            statement.executeUpdate("INSERT INTO GroupParty (partyId, groupName, accountPartyId) VALUES (%d, '%s', %d)"
                    .formatted(partyId, groupName, accountPartyId));
            return partyId;
        }
    }

    private static int insertBase(Statement statement, String kind) throws SQLException {
        try (ResultSet rs = statement.executeQuery("INSERT INTO Party (kind) VALUES ('" + kind + "') RETURNING partyId")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
```

- [ ] **Step 2: Update `PartyMapperTest`**

Replace the private `insertGroupRow(SqlSessionWrapper, …)` helper's body with a call to the shared helper:

```java
    private static int insertGroupRow(SqlSessionWrapper wrapper, String groupName, int accountId,
                                      AccountKind accountKind) throws SQLException {
        return TestParties.insertGroup(wrapper.session().getConnection(), groupName, accountId, accountKind);
    }
```

Change every `SELECT COUNT(*) FROM Party` in this class that means "how many parties exist" to keep counting `Party`; it still counts base rows, which is what those assertions mean. In `findOrInsert_isIdempotent`, add a second count so the kind row is checked too:

```java
            try (Statement statement = wrapper.session().getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT (SELECT COUNT(*) FROM Party), (SELECT COUNT(*) FROM PersonalParty)")) {
                resultSet.next();
                Assertions.assertEquals(1, resultSet.getInt(1), "one base row");
                Assertions.assertEquals(1, resultSet.getInt(2), "one kind row");
            }
```

Add these tests:

```java
    @Test
    void findOrInsert_account_leavesOneBaseRowAndOneKindRow() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            int id = wrapper.partyMapper().findOrInsert(new Party.Account(42, AccountKind.GOVERNMENT));
            try (Statement statement = wrapper.session().getConnection().createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT p.kind, a.accountId, a.accountKind FROM Party p
                         JOIN AccountParty a ON a.partyId = p.partyId WHERE p.partyId = %d
                         """.formatted(id))) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals("ACCOUNT", rs.getString(1));
                Assertions.assertEquals(42, rs.getInt(2));
                Assertions.assertEquals("GOVERNMENT", rs.getString(3));
            }
        }
    }

    @Test
    void insertGroup_createsTheAccountPartyWhenMissing() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            int groupId = wrapper.partyMapper().insertGroup("police", 42, AccountKind.GOVERNMENT);
            Assertions.assertEquals(new Party.Group("police", 42, AccountKind.GOVERNMENT),
                    wrapper.partyMapper().selectById(groupId));
            Assertions.assertEquals(new Party.Account(42, AccountKind.GOVERNMENT),
                    wrapper.partyMapper().findAccountParty(42));
        }
    }

    @Test
    void insertGroup_reusesTheAccountParty() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            int accountPartyId = wrapper.partyMapper().findOrInsert(new Party.Account(42, AccountKind.GOVERNMENT));
            wrapper.partyMapper().insertGroup("police", 42, AccountKind.GOVERNMENT);
            wrapper.partyMapper().insertGroup("rangers", 42, AccountKind.GOVERNMENT);
            try (Statement statement = wrapper.session().getConnection().createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT (SELECT COUNT(*) FROM AccountParty WHERE accountId = 42),
                                (SELECT COUNT(*) FROM GroupParty WHERE accountPartyId = %d)
                         """.formatted(accountPartyId))) {
                rs.next();
                Assertions.assertEquals(1, rs.getInt(1), "one account party");
                Assertions.assertEquals(2, rs.getInt(2), "both groups point at it");
            }
        }
    }

    @Test
    void insertGroup_accountStoredUnderAnotherKind_throws() {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            PartyMapper mapper = wrapper.partyMapper();
            mapper.findOrInsert(new Party.Account(42, AccountKind.GOVERNMENT));
            IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                    () -> mapper.insertGroup("police", 42, AccountKind.BUSINESS));
            Assertions.assertEquals("account #42 is stored as GOVERNMENT, not BUSINESS", exception.getMessage());
        }
    }

    @Test
    void updateGroupAccount_repointsTheGroup() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            PartyMapper mapper = wrapper.partyMapper();
            int groupId = mapper.insertGroup("police", 42, AccountKind.GOVERNMENT);
            mapper.updateGroupAccount(groupId, 7, AccountKind.BUSINESS);
            Assertions.assertEquals(new Party.Group("police", 7, AccountKind.BUSINESS), mapper.selectById(groupId));
            Assertions.assertEquals(new Party.Account(42, AccountKind.GOVERNMENT), mapper.findAccountParty(42),
                    "the old account keeps its party");
        }
    }

    @Test
    void deleteGroup_removesBothRowsAndKeepsTheAccountParty() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            PartyMapper mapper = wrapper.partyMapper();
            int groupId = mapper.insertGroup("police", 42, AccountKind.GOVERNMENT);
            Assertions.assertEquals(1, mapper.deleteGroup(groupId));
            Assertions.assertNull(mapper.selectById(groupId));
            try (Statement statement = wrapper.session().getConnection().createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM Party WHERE partyId = " + groupId)) {
                rs.next();
                Assertions.assertEquals(0, rs.getInt(1), "the base row is gone too");
            }
            Assertions.assertEquals(new Party.Account(42, AccountKind.GOVERNMENT), mapper.findAccountParty(42));
        }
    }
```

- [ ] **Step 3: Run `PartyMapperTest` to see the new tests fail**

Run: `./gradlew :realty-backend:test --tests '*PartyMapperTest*'`
Expected: compile error (`insertGroup` returns rows affected today and the new methods don't exist) or FAIL on the old column names.

- [ ] **Step 4: Rewrite the write side of `MariaPartyMapper`**

Replace `findOrInsert`, `findId`, `findAccountParty`, the personal/account/group statements, `findGroupParty`, `lockGroupId`, `insertGroup`, `updateGroupAccount`, `deleteGroup` and `selectGroupMappingRows` with:

```java
    @Override
    default int findOrInsert(@NotNull Party party) {
        return switch (party) {
            case Party.Personal personal -> {
                Integer existing = selectPersonalId(personal.playerUuid());
                if (existing != null) {
                    yield existing;
                }
                // Base row first, then the kind row. If another caller made the same party since
                // the read above, the kind row's unique key refuses ours; the caller rolls back
                // and reads theirs.
                int partyId = insertBase("PERSONAL");
                insertPersonal(partyId, personal.playerUuid());
                yield partyId;
            }
            case Party.Account account -> {
                // An account's identity is its accountId alone, so a row stored under another kind
                // is a genuine conflict rather than "not found".
                PartyAccountRow existing = selectAccountRow(account.accountId());
                if (existing != null) {
                    yield existing.requirePartyId(account.accountId(), account.kind());
                }
                int partyId = insertBase("ACCOUNT");
                insertAccount(partyId, account.accountId(), account.kind());
                yield partyId;
            }
            case Party.Group group -> {
                Integer id = selectGroupId(group.groupName());
                if (id == null) {
                    throw new IllegalStateException("group not mapped: " + group.groupName());
                }
                yield id;
            }
        };
    }

    @Override
    default @Nullable Integer findId(@NotNull Party party) {
        return switch (party) {
            case Party.Personal personal -> selectPersonalId(personal.playerUuid());
            case Party.Account account -> {
                PartyAccountRow row = selectAccountRow(account.accountId());
                yield row == null ? null : row.requirePartyId(account.accountId(), account.kind());
            }
            case Party.Group group -> selectGroupId(group.groupName());
        };
    }

    @Override
    default @Nullable Party.Account findAccountParty(int accountId) {
        PartyAccountRow row = selectAccountRow(accountId);
        return row == null ? null : new Party.Account(accountId, row.kind());
    }

    /** The base row of a new party; its id comes back with {@code RETURNING}, as every insert here does. */
    @Select("""
            INSERT INTO Party (kind)
            VALUES (#{kind})
            RETURNING partyId
            """)
    int insertBase(@Param("kind") @NotNull String kind);

    @Insert("""
            INSERT INTO PersonalParty (partyId, playerUuid)
            VALUES (#{partyId}, #{playerUuid})
            """)
    int insertPersonal(@Param("partyId") int partyId, @Param("playerUuid") @NotNull UUID playerUuid);

    @Select("""
            SELECT partyId
            FROM PersonalParty
            WHERE playerUuid = #{playerUuid}
            """)
    @Nullable Integer selectPersonalId(@Param("playerUuid") @NotNull UUID playerUuid);

    @Insert("""
            INSERT INTO AccountParty (partyId, accountId, accountKind)
            VALUES (#{partyId}, #{accountId}, #{accountKind})
            """)
    int insertAccount(@Param("partyId") int partyId,
                      @Param("accountId") int accountId,
                      @Param("accountKind") @NotNull AccountKind accountKind);

    /** The row stored for this accountId, whatever kind it was stored under. */
    @Select("""
            SELECT partyId, accountKind AS kind
            FROM AccountParty
            WHERE accountId = #{accountId}
            """)
    @ConstructorArgs({
            @Arg(column = "partyId", javaType = int.class),
            @Arg(column = "kind", javaType = AccountKind.class)
    })
    @Nullable PartyAccountRow selectAccountRow(@Param("accountId") int accountId);

    @Select("""
            SELECT partyId
            FROM GroupParty
            WHERE groupName = #{groupName}
            """)
    @Nullable Integer selectGroupId(@Param("groupName") @NotNull String groupName);

    @Insert("""
            INSERT INTO GroupParty (partyId, groupName, accountPartyId)
            VALUES (#{partyId}, #{groupName}, #{accountPartyId})
            """)
    int insertGroupRow(@Param("partyId") int partyId,
                       @Param("groupName") @NotNull String groupName,
                       @Param("accountPartyId") int accountPartyId);

    @Override
    default @Nullable Party.Group findGroupParty(@NotNull String groupName) {
        Integer partyId = selectGroupId(groupName.toLowerCase(Locale.ROOT));
        if (partyId == null) {
            return null;
        }
        Party party = selectById(partyId);
        return party instanceof Party.Group group ? group : null;
    }

    @Override
    @Select("""
            SELECT partyId
            FROM GroupParty
            WHERE groupName = #{groupName}
            FOR UPDATE
            """)
    @Nullable Integer lockGroupId(@Param("groupName") @NotNull String groupName);

    @Override
    default int insertGroup(@NotNull String groupName, int accountId, @NotNull AccountKind accountKind) {
        // The account's party first, so that the group's row can point at it.
        int accountPartyId = findOrInsert(new Party.Account(accountId, accountKind));
        int partyId = insertBase("GROUP");
        insertGroupRow(partyId, groupName, accountPartyId);
        return partyId;
    }

    @Override
    default int updateGroupAccount(int partyId, int accountId, @NotNull AccountKind accountKind) {
        int accountPartyId = findOrInsert(new Party.Account(accountId, accountKind));
        return repointGroup(partyId, accountPartyId);
    }

    @Update("""
            UPDATE GroupParty
            SET accountPartyId = #{accountPartyId}
            WHERE partyId = #{partyId}
            """)
    int repointGroup(@Param("partyId") int partyId, @Param("accountPartyId") int accountPartyId);

    @Override
    default int deleteGroup(int partyId) {
        int rows = deleteGroupRow(partyId);
        if (rows > 0) {
            deleteBase(partyId);
        }
        return rows;
    }

    @Delete("""
            DELETE FROM GroupParty
            WHERE partyId = #{partyId}
            """)
    int deleteGroupRow(@Param("partyId") int partyId);

    @Delete("""
            DELETE FROM Party
            WHERE partyId = #{partyId}
            """)
    int deleteBase(@Param("partyId") int partyId);

    @Select("""
            SELECT gp.partyId, gp.groupName, ga.accountId AS groupAccountId, ga.accountKind AS groupAccountKind,
                   (SELECT COUNT(*) FROM LeaseholdContract lc WHERE lc.landlordPartyId = gp.partyId)
                 + (SELECT COUNT(*) FROM FreeholdContract fc WHERE fc.authorityPartyId = gp.partyId)
                   AS contractCount
            FROM GroupParty gp
            JOIN AccountParty ga ON ga.partyId = gp.accountPartyId
            ORDER BY gp.groupName
            """)
    @ConstructorArgs({
            @Arg(column = "partyId", javaType = int.class, id = true),
            @Arg(column = "groupName", javaType = String.class),
            @Arg(column = "groupAccountId", javaType = int.class),
            @Arg(column = "groupAccountKind", javaType = AccountKind.class),
            @Arg(column = "contractCount", javaType = int.class)
    })
    @NotNull List<GroupMappingRow> selectGroupMappingRows();
```

Keep `countContractsNaming`, `countHistoryNaming` and `selectGroupMappings` unchanged. Remove `insertPersonalIfAbsent` and `insertAccountIfAbsent`. Update the `PartyMapper` javadoc: `insertGroup` "Creates the group's party, and the account's party first if it has none; returns the group's party id." and `updateGroupAccount` "Points the group at another account's party, created if absent; its own party id does not change." Update `PartyAccountRow`'s javadoc to say "looked up by `accountId` in `AccountParty`". In `GroupMappingRow`, no code change; its doc still holds.

- [ ] **Step 5: Run `PartyMapperTest`**

Run: `./gradlew :realty-backend:test --tests '*PartyMapperTest*'`
Expected: all PASS (the earlier 15 plus the 7 added in Tasks 3 and 4).

- [ ] **Step 6: Commit as a fix-up of the mapper commit**

```bash
git add realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaPartyMapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/PartyAccountRow.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/PartyMapper.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/TestParties.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/PartyMapperTest.java
git commit --fixup=$(git log --format=%h -1 -S'RESULT_MAP' --reverse feat/treasury-parties-spec..HEAD | head -1)
```

---

### Task 5: The contract, history and activity mappers join the kind tables

**Files:**
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaLeaseholdContractMapper.java` (three statements at ~66-76, ~206-216, ~295-306)
- Modify: `…/MariaFreeholdContractMapper.java` (~82-92; the locking read at ~115 is unchanged)
- Modify: `…/LeaseholdHistorySqlProvider.java`, `…/FreeholdHistorySqlProvider.java`
- Modify: `…/MariaActivityMapper.java` (~61-75)
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/MapperTest.java` (the group seeds at ~1635 and any other `INSERT INTO Party (kind, groupName…`)

**Interfaces:**
- Consumes: `PartySql` constants from Task 2; `TestParties` from Task 4.

- [ ] **Step 1: Point the `MapperTest` group seeds at the helper**

Find each `INSERT INTO Party (kind, groupName, groupAccountId, groupAccountKind)` in `MapperTest` (`grep -n 'groupAccountKind' MapperTest.java`). Replace each seed with `TestParties.insertGroup(connection, "<name>", <accountId>, AccountKind.<KIND>)`, where `connection` is the JDBC connection the surrounding code already opens (`wrapper.session().getConnection()`). Keep every `SELECT COUNT(*) FROM Party` assertion: it counts base rows, which is what those tests mean.

- [ ] **Step 2: Run the mapper tests to see the joins fail**

Run: `./gradlew :realty-backend:test --tests '*MapperTest*'`
Expected: FAIL — `Party lp` has no `playerUuid` column.

- [ ] **Step 3: Rewrite the joins**

In each `MariaLeaseholdContractMapper` statement, replace the line `INNER JOIN Party lp ON lp.partyId = lc.landlordPartyId` with `""" + PartySql.LANDLORD_JOINS_CONTRACT + """` (the text block is split around the constant, as the column list already is). For example the first statement becomes:

```java
    @Select("""
            SELECT lc.leaseholdContractId, lc.tenantId, lc.price, lc.durationSeconds,
                   lc.startDate, lc.endDate, lc.currentMaxExtensions, lc.maxExtensions,
                   lc.terminationEffectiveDate, lc.terminatedByRole, lc.acceptingTenants,
            """ + PartySql.LANDLORD_COLUMNS + """
            FROM LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            """ + PartySql.LANDLORD_JOINS_CONTRACT + """
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
```

In `MariaFreeholdContractMapper`, replace `INNER JOIN Party ap ON ap.partyId = fc.authorityPartyId` with `""" + PartySql.AUTHORITY_JOINS_CONTRACT + """`.

In the two history providers, replace each `INNER_JOIN("Party lp ON lp.partyId = lh.landlordPartyId");` with the four left joins, and the player filter:

```java
    /** A player is on a record as its tenant, or as its landlord. */
    private static final String PLAYER_FILTER = "(lh.tenantId = #{playerId} OR " + PartySql.LANDLORD_IS_PLAYER + ")";
    …
            FROM("LeaseholdHistory lh");
            LEFT_OUTER_JOIN("PersonalParty lpp ON lpp.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("AccountParty lap ON lap.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("GroupParty lgp ON lgp.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("AccountParty lga ON lga.partyId = lgp.accountPartyId");
```

and in `FreeholdHistorySqlProvider` the same with `fh`, prefix `a`, `AUTHORITY_IS_PLAYER`, and `(fh.buyerId = #{playerId} OR …)`. The `countHistory` methods get the same joins (they filter on the party for `playerId`).

In `MariaActivityMapper`, replace `INNER JOIN Party ap ON ap.partyId = fh.authorityPartyId` with `""" + PartySql.AUTHORITY_JOINS_HISTORY + """` and `INNER JOIN Party lp ON lp.partyId = lh.landlordPartyId` with `""" + PartySql.LANDLORD_JOINS_HISTORY + """`.

- [ ] **Step 4: Run the whole backend module**

Run: `./gradlew :realty-backend:test`
Expected: PASS except tests that seed group rows by SQL in files not yet touched on this branch — there should be none on `feat/party-model` after Step 1 (`grep -rl 'groupAccountKind)' realty-backend/src/test` returns nothing). If any remain, fix them the same way.

- [ ] **Step 5: Commit as fix-ups of the two refactor commits**

```bash
git add realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaLeaseholdContractMapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/LeaseholdHistorySqlProvider.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaActivityMapper.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/MapperTest.java
git commit --fixup=$(git log --format=%h -1 --grep='the landlord of a lease is a party' feat/treasury-parties-spec..HEAD)
git add realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaFreeholdContractMapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/FreeholdHistorySqlProvider.java
git commit --fixup=$(git log --format=%h -1 --grep='the authority of a freehold is a party' feat/treasury-parties-spec..HEAD)
```

If `MariaActivityMapper`'s party join was introduced by a different commit (check `git log -S'AUTHORITY_AS_SECOND_COLUMNS'`), target that one for it instead.

---

### Task 6: Fold the fix-ups into #56 and verify the branch

**Files:** none new.

- [ ] **Step 1: Autosquash**

```bash
GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash feat/treasury-parties-spec
git log --oneline feat/treasury-parties-spec..HEAD
```

Expected: the same list of subjects as before, no `fixup!` entries.

- [ ] **Step 2: Every commit compiles**

```bash
for c in $(git rev-list --reverse feat/treasury-parties-spec..HEAD); do
  git checkout -q $c && ./gradlew :realty-backend:compileJava :realty-backend:compileTestJava -q >/dev/null 2>&1 && echo "ok  $c" || echo "BAD $c"
done
git checkout -q feat/party-model
```

Expected: every line `ok`. A `BAD` line means a fix-up landed on the wrong commit; `git rebase -i` to move the hunk, then re-run.

- [ ] **Step 3: Full module test on the branch tip**

Run: `./gradlew :realty-backend:test`
Expected: PASS. Then `grep -rn "chk_party_shape\|groupAccountKind" realty-backend/src/main` returns nothing.

---

### Task 7: #57 — the tenancy payment test seeds its group through the helper

**Branch:** `feat/party-payments`, after `git rebase --onto feat/party-model <old feat/party-model tip> feat/party-payments` (take the old tip from `git rev-parse origin/feat/party-model` before Task 6 rewrote it; record it first).

**Files:**
- Modify: `realty-backend/src/test/java/io/github/md5sha256/realty/database/FailedTenancyPaymentTest.java` (`mappedGroup`, ~line 55)

- [ ] **Step 1: Rebase and see the test fail**

```bash
git worktree add ../realty-party-payments feat/party-payments && cd ../realty-party-payments
git rebase --onto feat/party-model <old-model-tip> feat/party-payments
./gradlew :realty-backend:test --tests '*FailedTenancyPaymentTest*'
```

Expected: FAIL — unknown column `groupName` in `Party`.

- [ ] **Step 2: Use the helper**

```java
    /** A group is mapped by a command, not by the backend, so its rows are written directly. */
    private static Party.Group mappedGroup(String groupName, int accountId, AccountKind accountKind)
            throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            TestParties.insertGroup(wrapper.session().getConnection(), groupName, accountId, accountKind);
        }
        return new Party.Group(groupName, accountId, accountKind);
    }
```

- [ ] **Step 3: Run, then fold in**

Run: `./gradlew :realty-backend:test --tests '*FailedTenancyPaymentTest*'` → PASS.

```bash
git add realty-backend/src/test/java/io/github/md5sha256/realty/database/FailedTenancyPaymentTest.java
git commit --fixup=$(git log --format=%h -1 -S'mappedGroup' --reverse feat/party-model..HEAD | head -1)
GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash feat/party-model
./gradlew :realty-backend:test
```

Expected: full module PASS.

---

### Task 8: #58 — the backend tests seed groups through the helper

**Branch:** `feat/party-authorization`, rebased onto the new `feat/party-payments` the same way.

**Files:**
- Modify: `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtyBackendImplTest.java` (two seeds, ~1792 and ~1934, plus the `groupLeasehold` helper added by the set-landlord fix)

- [ ] **Step 1: Rebase, run, see the seeds fail**

Run: `./gradlew :realty-backend:test --tests '*RealtyBackendImplTest*'` → FAIL on `groupName`.

- [ ] **Step 2: Replace each raw insert** with `TestParties.insertGroup(<connection>, "<name>", <accountId>, AccountKind.<KIND>)`. Where the test later selects `partyId FROM Party WHERE groupName = …`, use the id the helper returns instead.

- [ ] **Step 3: Run, fold in, full module**

```bash
git add realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtyBackendImplTest.java
git commit --fixup=$(git log --format=%h -1 -S"INSERT INTO Party (kind, groupName" --reverse feat/party-payments..HEAD | head -1)
GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash feat/party-payments
./gradlew :realty-backend:test
```

If the two seeds came from different commits, make two fix-ups. Expected: PASS.

---

### Task 9: #59, #60 and #62 — group listing, portfolio counts, and the concurrent-creation fix

**Branches:** `feat/party-commands`, `feat/party-notifications`, `feat/party-rest`, `feat/party-review-fixes`, each rebased onto its new parent in turn. #59 and #60 need only checks; #61 has no backend change; #62 changes `RealtyBackendImpl` and two tests.

**Files:**
- Check on #59: `GroupMappingTest` (`SELECT COUNT(*) FROM Party WHERE kind = 'GROUP'` still correct: base rows carry the kind).
- Check on #60/#63: `PartyPortfolioTest` (`SELECT COUNT(*) FROM Party` still correct).
- Modify on #62: `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java` (`partyIdCreatingIfAbsent`, ~89), `ConcurrencyTest` (`NewParty`, ~414-495), and drop or resolve the commit `fix(migration): V19 tolerates a missing index…` whose content now lives in #56.

**Interfaces:**
- Produces: `partyIdCreatingIfAbsent` commits the two inserts as one transaction and, on a `PersistenceException`, rolls back and re-reads.

- [ ] **Step 1: Rebase #59, #60, #61 in turn and run the backend module on each**

Expected: PASS on each with no code change. If `GroupMappingTest` or `PartyPortfolioTest` fails on a `Party` column, replace the SQL with the equivalent on the kind table and fix-up the commit that introduced it.

- [ ] **Step 2: Rebase #62**

The commit `fix(migration): V19 tolerates a missing index, and its test checks every table` conflicts, because #56 now contains its `DROP INDEX IF EXISTS` and its tests. Resolve by taking #56's versions of `V19__parties.sql` and `PartiesMigrationTest.java` (`git checkout --ours -- <files>` during the rebase, then `git add`). If the commit becomes empty, `git rebase --skip` it. Continue.

- [ ] **Step 3: Write the race test**

In `ConcurrencyTest.NewParty`, `nameOneNewPartyAtOnce(Party landlord, String partyColumnCondition)` asserts `SELECT COUNT(*) FROM Party WHERE <condition>`. Change the parameter to a full count query and the two callers to pass, for the player case, `"SELECT COUNT(*) FROM PersonalParty WHERE playerUuid = '" + uuid + "'"` and for the account case `"SELECT COUNT(*) FROM AccountParty WHERE accountId = 42"`. Add, next to the existing assertion:

```java
            Assertions.assertEquals(1, queryInt(countQuery));
            Assertions.assertEquals(queryInt("SELECT COUNT(*) FROM Party"),
                    queryInt("SELECT (SELECT COUNT(*) FROM PersonalParty) + (SELECT COUNT(*) FROM AccountParty) + (SELECT COUNT(*) FROM GroupParty)"),
                    "no base row without its kind row survives a race");
```

- [ ] **Step 4: Run it to see it fail**

Run: `./gradlew :realty-backend:test --tests '*ConcurrencyTest*NewParty*'`
Expected: FAIL — with autocommit on, the loser's base row survives, or the loser throws.

- [ ] **Step 5: Make `partyIdCreatingIfAbsent` transactional with a retry**

```java
    /**
     * The id of the party's row, creating its base row and kind row if it has none. Runs in a short
     * transaction of its own, before the caller's, so that two commands naming the same new party
     * at the same moment both succeed: the loser's kind row is refused by its unique key, the two
     * rows it made are rolled back, and it reads the winner's. A party created here stays if the
     * caller's transaction then rolls back; a party that nothing names does no harm.
     */
    private int partyIdCreatingIfAbsent(@NotNull Party party) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            try {
                int partyId = wrapper.partyMapper().findOrInsert(party);
                session.commit();
                return partyId;
            } catch (PersistenceException raced) {
                session.rollback();
                Integer partyId = wrapper.partyMapper().findId(party);
                if (partyId == null) {
                    throw raced;
                }
                return partyId;
            }
        }
    }
```

Add `import org.apache.ibatis.exceptions.PersistenceException;` and `import org.apache.ibatis.session.SqlSession;` if missing.

- [ ] **Step 6: Run, fold in, full module**

```bash
./gradlew :realty-backend:test --tests '*ConcurrencyTest*'
git add realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/ConcurrencyTest.java
git commit --fixup=$(git log --format=%h -1 --grep='naming a new party at the same moment' feat/party-rest..HEAD)
GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash feat/party-rest
./gradlew :realty-backend:test
```

Expected: PASS.

---

### Task 10: #61 — the release notes' SQL writes the new tables, and the manual fix is tested

**Branch:** `feat/party-rest` (rebased in Task 9, Step 1). The test goes in `PartiesMigrationTest`, a file #56 created, but it pins #61's script, so it lands in #61 with the script.

**Files:**
- Modify: `docs/release-notes/2.0.0.md` — "Find the values" query (~44), the manual fix script (~76-87), the "wrong account id" undo script (~123-133), the wrong-kind repair (~240)
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/PartiesMigrationTest.java`

- [ ] **Step 1: Write the manual-fix tests**

```java
    /** The manual fix in docs/release-notes/2.0.0.md, values substituted. Keep the two in step. */
    private static final String MANUAL_FIX = """
            SET @account = NULL, @legacy = NULL, @new = NULL, @old = NULL;
            SET @account = %d;
            SET @legacy = '%s';
            SET @old = (SELECT partyId FROM PersonalParty WHERE playerUuid = @legacy);
            SET @new = (SELECT partyId FROM AccountParty WHERE accountId = @account);
            INSERT INTO Party (kind) SELECT 'ACCOUNT' FROM DUAL WHERE @old IS NOT NULL AND @new IS NULL;
            INSERT INTO AccountParty (partyId, accountId, accountKind)
                SELECT LAST_INSERT_ID(), @account, 'GOVERNMENT' FROM DUAL WHERE @old IS NOT NULL AND @new IS NULL;
            SET @new = (SELECT partyId FROM AccountParty WHERE accountId = @account AND accountKind = 'GOVERNMENT');
            UPDATE LeaseholdContract SET landlordPartyId  = @new WHERE landlordPartyId  = @old AND @new IS NOT NULL;
            UPDATE LeaseholdHistory  SET landlordPartyId  = @new WHERE landlordPartyId  = @old AND @new IS NOT NULL;
            UPDATE FreeholdContract  SET authorityPartyId = @new WHERE authorityPartyId = @old AND @new IS NOT NULL;
            UPDATE FreeholdHistory   SET authorityPartyId = @new WHERE authorityPartyId = @old AND @new IS NOT NULL;
            """;

    private static void runManualFix(Connection connection, int accountId, UUID legacy) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : MANUAL_FIX.formatted(accountId, legacy).split(";\\s*\\n")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    @Test
    void manualFix_movesTheLegacyUuidToTheAccountParty() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                runManualFix(connection, 42, LANDLORD_AND_AUTHORITY);
                int accountPartyId = count(statement, "SELECT partyId FROM AccountParty WHERE accountId = 42 AND accountKind = 'GOVERNMENT'");
                Assertions.assertEquals(1, count(statement, "SELECT COUNT(*) FROM Party WHERE partyId = " + accountPartyId + " AND kind = 'ACCOUNT'"));
                Assertions.assertEquals(1, count(statement, "SELECT COUNT(*) FROM LeaseholdContract WHERE landlordPartyId = " + accountPartyId));
                Assertions.assertEquals(1, count(statement, "SELECT COUNT(*) FROM FreeholdContract WHERE authorityPartyId = " + accountPartyId));
                Assertions.assertEquals(1, count(statement, "SELECT COUNT(*) FROM FreeholdHistory WHERE authorityPartyId = " + accountPartyId));
                Assertions.assertEquals(0, count(statement, "SELECT COUNT(*) FROM LeaseholdHistory WHERE landlordPartyId = " + accountPartyId),
                        "player B's history row is not the legacy UUID's");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void manualFix_runTwice_changesNothingTheSecondTime() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                runManualFix(connection, 42, LANDLORD_AND_AUTHORITY);
                int parties = count(statement, "SELECT COUNT(*) FROM Party");
                runManualFix(connection, 42, LANDLORD_AND_AUTHORITY);
                Assertions.assertEquals(parties, count(statement, "SELECT COUNT(*) FROM Party"));
                Assertions.assertEquals(1, count(statement, "SELECT COUNT(*) FROM AccountParty WHERE accountId = 42"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void manualFix_unknownUuid_createsNoParty() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int parties = count(statement, "SELECT COUNT(*) FROM Party");
                runManualFix(connection, 42, UUID.fromString("3a1c88f0-0000-0000-0000-0000000000ff"));
                Assertions.assertEquals(parties, count(statement, "SELECT COUNT(*) FROM Party"), "no base row without a kind row, and no account party");
                Assertions.assertEquals(0, count(statement, "SELECT COUNT(*) FROM AccountParty"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void manualFix_accountStoredUnderAnotherKind_movesNothing() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int business = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('ACCOUNT') RETURNING partyId");
                statement.executeUpdate("INSERT INTO AccountParty (partyId, accountId, accountKind) VALUES (%d, 42, 'BUSINESS')".formatted(business));
                runManualFix(connection, 42, LANDLORD_AND_AUTHORITY);
                Assertions.assertEquals(0, count(statement, "SELECT COUNT(*) FROM LeaseholdContract WHERE landlordPartyId = " + business));
                Assertions.assertEquals(1, count(statement, "SELECT COUNT(*) FROM AccountParty WHERE accountId = 42"), "no second row for the account");
            } finally {
                connection.rollback();
            }
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :realty-backend:test --tests '*PartiesMigrationTest.manualFix*'`
Expected: they PASS already if the SQL above is right, because the test carries the script. That is fine: the test's job is to pin the script the notes will copy. If any fails, fix the SQL in the test first.

- [ ] **Step 3: Update the release notes**

"Find the values" query: `FROM Party p` → `FROM PersonalParty p`, and delete the line `WHERE p.kind = 'PERSONAL'`.

Manual fix script, between `SET @legacy = '<legacy uuid>';` and the `SELECT @new AS newParty …` line, replace the `INSERT IGNORE` and the two `SET` lines with:

```sql
SET @old = (SELECT partyId FROM PersonalParty WHERE playerUuid = @legacy);
SET @new = (SELECT partyId FROM AccountParty WHERE accountId = @account);
-- The account's party is created only when the legacy UUID is known and the account has no party yet:
-- first its base row, then its account row with the id the base row got.
INSERT INTO Party (kind) SELECT 'ACCOUNT' FROM DUAL WHERE @old IS NOT NULL AND @new IS NULL;
INSERT INTO AccountParty (partyId, accountId, accountKind)
    SELECT LAST_INSERT_ID(), @account, 'GOVERNMENT' FROM DUAL WHERE @old IS NOT NULL AND @new IS NULL;
SET @new = (SELECT partyId FROM AccountParty WHERE accountId = @account AND accountKind = 'GOVERNMENT');
```

Keep the `SELECT … AS result` line and the four `UPDATE`s. The explanation below the script still holds word for word ("Only `newParty` is `NULL`: the account is already stored under another kind" is still what happens).

Undo script: `SET @wrong = (SELECT partyId FROM AccountParty WHERE accountId = @wrongAccount AND accountKind = 'GOVERNMENT');`, then replace the `INSERT IGNORE` with:

```sql
SET @right = (SELECT partyId FROM AccountParty WHERE accountId = @rightAccount);
INSERT INTO Party (kind) SELECT 'ACCOUNT' FROM DUAL WHERE @wrong IS NOT NULL AND @right IS NULL;
INSERT INTO AccountParty (partyId, accountId, accountKind)
    SELECT LAST_INSERT_ID(), @rightAccount, 'GOVERNMENT' FROM DUAL WHERE @wrong IS NOT NULL AND @right IS NULL;
SET @right = (SELECT partyId FROM AccountParty WHERE accountId = @rightAccount AND accountKind = 'GOVERNMENT');
```

and add `@right = NULL` handling is already in the `SET … = NULL` line. The sentence "The row of the wrong account stays in the `Party` table" → "The wrong account keeps its party; no contract names it any more."

Wrong-kind repair: `UPDATE Party SET kind = 'BUSINESS' WHERE accountId = 42;` → `UPDATE AccountParty SET accountKind = 'BUSINESS' WHERE accountId = 42;`.

Search the notes for any other `Party` SQL: `grep -n 'Party' docs/release-notes/2.0.0.md | grep -i 'select\|insert\|update'` and fix likewise.

- [ ] **Step 4: Commit**

Both the notes and the test are a fix-up of #61's script commit:

```bash
git add docs/release-notes/2.0.0.md \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/PartiesMigrationTest.java
git commit --fixup=$(git log --format=%h -1 -S'INSERT IGNORE INTO Party' --reverse feat/party-notifications..HEAD | head -1)
GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash feat/party-notifications
./gradlew :realty-backend:test --tests '*PartiesMigrationTest*'
```

Expected: PASS. Then rebase #62 and #63 onto the new `feat/party-rest` (Task 11 does this for every branch anyway).

---

### Task 11: Restack, verify every branch, update #56's description

- [ ] **Step 1: Rebase the stack bottom-up**

Record each branch's old tip before rebasing it, then for each branch in order (`feat/party-payments` … `feat/party-listing-parts`): `git rebase --onto <new parent> <old parent tip> <branch>`. Conflicts to expect: none beyond those handled in Tasks 7–10, unless Task 10 moved the test commit.

- [ ] **Step 2: Every branch compiles and the top passes**

```bash
for b in feat/party-model feat/party-payments feat/party-authorization feat/party-commands feat/party-notifications feat/party-rest feat/party-review-fixes feat/party-listing-parts; do
  git checkout -q $b && ./gradlew compileJava compileTestJava -q >/dev/null 2>&1 && echo "ok  $b" || echo "BAD $b"
done
git checkout -q feat/party-listing-parts && ./gradlew test --continue
```

Expected: every branch `ok`; the full suite passes except the two `StaticSiteTest` tests that already fail on `main`.

- [ ] **Step 3: Nothing single-table remains**

`git grep -n 'chk_party_shape\|groupAccountKind\|INSERT IGNORE INTO Party' feat/party-listing-parts -- ':!docs/superpowers'` returns nothing. (The spec's *Alternatives considered* may still name the rejected design in prose; that is fine.)

- [ ] **Step 4: Update the PR descriptions**

#56's body: change "Migration V19 adds the Party table" to "Migration V19 adds a Party table that gives each party its id and kind, and one table per kind for its details,". Its **Spec** line links the schema section at the new spec commit. #62's body loses the sentence about V19 tolerating a missing index (now in #56). The spec links in every description move from `ab4086b…` to the commit that holds the per-kind schema (`git rev-parse feat/treasury-parties-spec`).

- [ ] **Step 5: Hand over**

List the branches to force-push. Do not push.
