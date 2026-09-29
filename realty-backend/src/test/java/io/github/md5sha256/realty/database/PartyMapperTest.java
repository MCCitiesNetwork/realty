package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.mapper.PartyMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

class PartyMapperTest extends AbstractDatabaseTest {

    @Test
    void findOrInsert_isIdempotent() throws SQLException {
        Party.Personal party = new Party.Personal(UUID.randomUUID());
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            int firstId = wrapper.partyMapper().findOrInsert(party);
            int secondId = wrapper.partyMapper().findOrInsert(party);
            Assertions.assertEquals(firstId, secondId);

            try (Statement statement = wrapper.session().getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT (SELECT COUNT(*) FROM Party), (SELECT COUNT(*) FROM PersonalParty)")) {
                resultSet.next();
                Assertions.assertEquals(1, resultSet.getInt(1), "one base row");
                Assertions.assertEquals(1, resultSet.getInt(2), "one kind row");
            }
        }
    }

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
    void findOrInsert_account() {
        Party.Account account = new Party.Account(42, AccountKind.GOVERNMENT);
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            int id = wrapper.partyMapper().findOrInsert(account);
            Assertions.assertEquals(account, wrapper.partyMapper().selectById(id));
        }
    }

    @Test
    void findOrInsert_accountStoredUnderAnotherKind_throws() throws SQLException {
        Party.Account original = new Party.Account(42, AccountKind.GOVERNMENT);
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            wrapper.partyMapper().findOrInsert(original);

            Party.Account conflicting = new Party.Account(42, AccountKind.BUSINESS);
            PartyMapper mapper = wrapper.partyMapper();
            IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                    () -> mapper.findOrInsert(conflicting));
            Assertions.assertEquals("account #42 is stored as GOVERNMENT, not BUSINESS", exception.getMessage());

            try (Statement statement = wrapper.session().getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM Party")) {
                resultSet.next();
                Assertions.assertEquals(1, resultSet.getInt(1));
            }
        }
    }

    @Test
    void findId_accountStoredUnderAnotherKind_throws() {
        Party.Account original = new Party.Account(42, AccountKind.GOVERNMENT);
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            wrapper.partyMapper().findOrInsert(original);

            Party.Account conflicting = new Party.Account(42, AccountKind.BUSINESS);
            PartyMapper mapper = wrapper.partyMapper();
            IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                    () -> mapper.findId(conflicting));
            Assertions.assertEquals("account #42 is stored as GOVERNMENT, not BUSINESS", exception.getMessage());
        }
    }

    @Test
    void findOrInsert_unmappedGroupThrows() {
        Party.Group group = new Party.Group("police", 42, AccountKind.GOVERNMENT);
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            PartyMapper mapper = wrapper.partyMapper();
            Assertions.assertThrows(IllegalStateException.class, () -> mapper.findOrInsert(group));
        }
    }

    @Test
    void findId_groupIgnoresTheAccount() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            int insertedId = insertGroupRow(wrapper, "police", 42, AccountKind.GOVERNMENT);

            // The party is looked up by groupName alone: a mismatched accountId/accountKind
            // on the passed-in Group is ignored.
            Party.Group lookup = new Party.Group("police", 99, AccountKind.BUSINESS);
            Integer foundId = wrapper.partyMapper().findId(lookup);
            Assertions.assertEquals(insertedId, foundId);
        }
    }

    @Test
    void selectById_buildsEachKind() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            PartyMapper mapper = wrapper.partyMapper();

            Party.Personal personal = new Party.Personal(UUID.randomUUID());
            int personalId = mapper.findOrInsert(personal);
            Assertions.assertEquals(personal, mapper.selectById(personalId));

            Party.Account business = new Party.Account(1, AccountKind.BUSINESS);
            int businessId = mapper.findOrInsert(business);
            Assertions.assertEquals(business, mapper.selectById(businessId));

            Party.Account government = new Party.Account(2, AccountKind.GOVERNMENT);
            int governmentId = mapper.findOrInsert(government);
            Assertions.assertEquals(government, mapper.selectById(governmentId));

            Party.Account system = new Party.Account(3, AccountKind.SYSTEM);
            int systemId = mapper.findOrInsert(system);
            Assertions.assertEquals(system, mapper.selectById(systemId));

            // A Group row is never inserted by the mapper, so it is seeded directly.
            int groupId = insertGroupRow(wrapper, "police", 4, AccountKind.GOVERNMENT);
            Party.Group group = new Party.Group("police", 4, AccountKind.GOVERNMENT);
            Assertions.assertEquals(group, mapper.selectById(groupId));
        }
    }

    @Test
    void selectById_unknownIsNull() {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            Assertions.assertNull(wrapper.partyMapper().selectById(999_999));
        }
    }

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

    @Test
    void selectNonPersonal_returnsEveryAccountAndGroup() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            PartyMapper mapper = wrapper.partyMapper();
            mapper.findOrInsert(new Party.Personal(UUID.randomUUID()));
            Party.Account government = new Party.Account(1, AccountKind.GOVERNMENT);
            mapper.findOrInsert(government);
            insertGroupRow(wrapper, "police", 1, AccountKind.GOVERNMENT);
            Party.Account business = new Party.Account(2, AccountKind.BUSINESS);
            mapper.findOrInsert(business);

            Assertions.assertEquals(
                    List.of(government, new Party.Group("police", 1, AccountKind.GOVERNMENT), business),
                    mapper.selectNonPersonal());
        }
    }

    @Test
    void selectNonPersonal_emptyWhenOnlyPlayers() {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            wrapper.partyMapper().findOrInsert(new Party.Personal(UUID.randomUUID()));

            Assertions.assertEquals(List.of(), wrapper.partyMapper().selectNonPersonal());
        }
    }

    @Test
    void findGroupParty_returnsTheMappedParty() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            insertGroupRow(wrapper, "police", 42, AccountKind.GOVERNMENT);

            Assertions.assertEquals(new Party.Group("police", 42, AccountKind.GOVERNMENT),
                    wrapper.partyMapper().findGroupParty("police"));
        }
    }

    @Test
    void findGroupParty_isCaseInsensitive() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            insertGroupRow(wrapper, "police", 42, AccountKind.GOVERNMENT);

            Assertions.assertEquals(new Party.Group("police", 42, AccountKind.GOVERNMENT),
                    wrapper.partyMapper().findGroupParty("Police"));
        }
    }

    @Test
    void findGroupParty_unmappedIsNull() {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            Assertions.assertNull(wrapper.partyMapper().findGroupParty("mafia"));
        }
    }

    private static int insertGroupRow(SqlSessionWrapper wrapper, String groupName, int accountId,
                                      AccountKind accountKind) throws SQLException {
        return TestParties.insertGroup(wrapper.session().getConnection(), groupName, accountId, accountKind);
    }
}
