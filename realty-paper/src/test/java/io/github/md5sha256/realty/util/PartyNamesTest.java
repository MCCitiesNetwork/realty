package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyNamesTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private Server server;

    @Mock
    private TreasuryApi treasury;

    @Mock
    private Player online;

    @Mock
    private OfflinePlayer offline;

    private MutableClock clock;
    private PartyNames names;

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-29T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        names = new PartyNames(server, treasury, clock);
    }

    private static Account account(int id, AccountType type, String displayName) {
        Account account = new Account();
        account.setAccountId(id);
        account.setAccountType(type);
        account.setDisplayName(displayName);
        return account;
    }

    @Test
    void player_isTheName() {
        when(server.getPlayer(PLAYER)).thenReturn(online);
        when(online.getName()).thenReturn("Steve");
        assertEquals("Steve", names.display(new Party.Personal(PLAYER)));
    }

    @Test
    void unknownPlayer_isTheUuid() {
        when(server.getPlayer(PLAYER)).thenReturn(null);
        when(server.getOfflinePlayer(PLAYER)).thenReturn(offline);
        when(offline.getName()).thenReturn(null);
        assertEquals(PLAYER.toString(), names.display(PLAYER));
    }

    @Test
    void eachAccountKind_hasItsSuffix() {
        when(treasury.getAccountById(1)).thenReturn(account(1, AccountType.GOVERNMENT, "GovSecurity"));
        when(treasury.getAccountById(2)).thenReturn(account(2, AccountType.BUSINESS, "Acme"));
        when(treasury.getAccountById(3)).thenReturn(account(3, AccountType.SYSTEM, "Mint"));
        assertEquals("GovSecurity (government)", names.display(new Party.Account(1, AccountKind.GOVERNMENT)));
        assertEquals("Acme (business)", names.display(new Party.Account(2, AccountKind.BUSINESS)));
        assertEquals("Mint (system)", names.display(new Party.Account(3, AccountKind.SYSTEM)));
    }

    @Test
    void group_hasItsSuffix() {
        assertEquals("police (group)", names.display(new Party.Group("police", 7, AccountKind.GOVERNMENT)));
        assertEquals("police (group)", PartyNames.group("police"));
    }

    @Test
    void missingAccount_showsTheId() {
        when(treasury.getAccountById(42)).thenReturn(null);
        assertEquals("#42 (government)", names.display(new Party.Account(42, AccountKind.GOVERNMENT)));
    }

    @Test
    void absentTreasury_showsTheId() {
        PartyNames withoutTreasury = new PartyNames(server, null, clock);
        assertEquals("#42 (government)", withoutTreasury.display(new Party.Account(42, AccountKind.GOVERNMENT)));
    }

    @Test
    void accountName_isReadOnceWithinAMinute() {
        when(treasury.getAccountById(42)).thenReturn(account(42, AccountType.GOVERNMENT, "GovSecurity"));
        Party party = new Party.Account(42, AccountKind.GOVERNMENT);
        names.display(party);
        clock.advance(Duration.ofSeconds(59));
        names.display(party);
        verify(treasury, times(1)).getAccountById(42);
    }

    @Test
    void accountName_isReadAgainAfterAMinute() {
        when(treasury.getAccountById(42))
                .thenReturn(account(42, AccountType.GOVERNMENT, "GovSecurity"))
                .thenReturn(account(42, AccountType.GOVERNMENT, "Guard"));
        Party party = new Party.Account(42, AccountKind.GOVERNMENT);
        assertEquals("GovSecurity (government)", names.display(party));
        clock.advance(Duration.ofSeconds(61));
        assertEquals("Guard (government)", names.display(party));
        verify(treasury, times(2)).getAccountById(42);
    }
}
