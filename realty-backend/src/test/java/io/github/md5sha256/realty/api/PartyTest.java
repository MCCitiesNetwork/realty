package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

class PartyTest {

    @Test
    void group_nameIsLowerCased() {
        Assertions.assertEquals(
                Party.group("police", 42, AccountKind.GOVERNMENT),
                Party.group("Police", 42, AccountKind.GOVERNMENT)
        );
    }

    @Test
    void partyKind_matchesEachKind() {
        Assertions.assertEquals(PartyKind.PERSONAL, Party.personal(UUID.randomUUID()).partyKind());
        Assertions.assertEquals(PartyKind.SYSTEM, Party.account(7, AccountKind.SYSTEM).partyKind());
        Assertions.assertEquals(PartyKind.GROUP, Party.group("police", 42, AccountKind.GOVERNMENT).partyKind());
    }

    @Test
    void playerUuidOf_isEmptyForNonPlayers() {
        UUID id = UUID.randomUUID();
        Assertions.assertEquals(Optional.of(id), Party.playerUuidOf(Party.personal(id)));
        Assertions.assertEquals(Optional.empty(), Party.playerUuidOf(Party.account(7, AccountKind.BUSINESS)));
        Assertions.assertEquals(Optional.empty(), Party.playerUuidOf(null));
    }

    @Test
    void partyKindOf_mapsEachAccountKind() {
        Assertions.assertEquals(PartyKind.BUSINESS, PartyKind.of(AccountKind.BUSINESS));
        Assertions.assertEquals(PartyKind.GOVERNMENT, PartyKind.of(AccountKind.GOVERNMENT));
        Assertions.assertEquals(PartyKind.SYSTEM, PartyKind.of(AccountKind.SYSTEM));
    }

    @Test
    void partyKind_ofAnAccountUsesTheAccountKind() {
        for (AccountKind kind : AccountKind.values()) {
            Assertions.assertEquals(PartyKind.of(kind), Party.account(7, kind).partyKind());
        }
    }
}
