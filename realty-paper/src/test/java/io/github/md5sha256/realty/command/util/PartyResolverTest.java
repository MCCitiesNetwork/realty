package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.localisation.MessageKeys;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyResolverTest {

    @Mock
    private Server server;

    @Mock
    private TreasuryApi treasury;

    @Mock
    private RealtyBackend backend;

    @Mock
    private Player onlinePlayer;

    @Mock
    private OfflinePlayer offlinePlayer;

    private PartyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PartyResolver(server, treasury, backend);
    }

    private static Account account(int id, AccountType type, String displayName) {
        Account account = new Account();
        account.setAccountId(id);
        account.setAccountType(type);
        account.setDisplayName(displayName);
        return account;
    }

    // --- No flag: a player ---

    @Test
    void plainName_isAPlayer() {
        UUID uuid = UUID.randomUUID();
        when(server.getPlayerExact("Steve")).thenReturn(onlinePlayer);
        when(onlinePlayer.getUniqueId()).thenReturn(uuid);

        PartyResolver.Resolution result = resolver.resolve("Steve", null, null);

        assertEquals(new PartyResolver.Resolution.Resolved(new Party.Personal(uuid)), result);
    }

    @Test
    void plainName_offlineButHasPlayed_isAPlayer() {
        UUID uuid = UUID.randomUUID();
        when(server.getPlayerExact("Steve")).thenReturn(null);
        when(server.getOfflinePlayerIfCached("Steve")).thenReturn(offlinePlayer);
        when(offlinePlayer.hasPlayedBefore()).thenReturn(true);
        when(offlinePlayer.getUniqueId()).thenReturn(uuid);

        PartyResolver.Resolution result = resolver.resolve("Steve", null, null);

        assertEquals(new PartyResolver.Resolution.Resolved(new Party.Personal(uuid)), result);
    }

    @Test
    void plainName_neverPlayed_isRefused() {
        when(server.getPlayerExact("Ghost")).thenReturn(null);
        when(server.getOfflinePlayerIfCached("Ghost")).thenReturn(null);

        PartyResolver.Resolution result = resolver.resolve("Ghost", null, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.COMMON_PLAYER_NOT_FOUND, "Ghost"), result);
    }

    @Test
    void plainNameThatLooksLikeAnId_isStillAPlayerName() {
        when(server.getPlayerExact("#12")).thenReturn(null);
        when(server.getOfflinePlayerIfCached("#12")).thenReturn(null);

        PartyResolver.Resolution result = resolver.resolve("#12", null, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.COMMON_PLAYER_NOT_FOUND, "#12"), result);
        verifyNoInteractions(treasury, backend);
    }

    // --- Any flag without Treasury ---

    @Test
    void anyFlag_withoutTreasury_isRefused() {
        PartyResolver noTreasury = new PartyResolver(server, null, backend);

        PartyResolver.Resolution result = noTreasury.resolve("GovSecurity", PartyFlag.GOVERNMENT, UUID.randomUUID());

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_REQUIRES_TREASURY, "GovSecurity"), result);
    }

    // --- GROUP ---

    @Test
    void group_mapped() {
        Party.Group group = new Party.Group("police", 42, AccountKind.GOVERNMENT);
        when(backend.findGroupParty("police")).thenReturn(group);
        when(treasury.getAccountById(42)).thenReturn(account(42, AccountType.GOVERNMENT, "police"));

        PartyResolver.Resolution result = resolver.resolve("police", PartyFlag.GROUP, null);

        assertEquals(new PartyResolver.Resolution.Resolved(group), result);
    }

    @Test
    void group_inCapitals_findsTheMappedGroup() {
        Party.Group group = new Party.Group("police", 42, AccountKind.GOVERNMENT);
        when(backend.findGroupParty("Police")).thenReturn(group);
        when(treasury.getAccountById(42)).thenReturn(account(42, AccountType.GOVERNMENT, "police"));

        PartyResolver.Resolution result = resolver.resolve("Police", PartyFlag.GROUP, null);

        assertEquals(new PartyResolver.Resolution.Resolved(group), result);
    }

    @Test
    void group_notMapped_isRefused() {
        when(backend.findGroupParty("mafia")).thenReturn(null);

        PartyResolver.Resolution result = resolver.resolve("mafia", PartyFlag.GROUP, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_GROUP_NOT_MAPPED, "mafia"), result);
    }

    @Test
    void group_accountNoLongerExists_isRefused() {
        Party.Group group = new Party.Group("police", 42, AccountKind.GOVERNMENT);
        when(backend.findGroupParty("police")).thenReturn(group);
        when(treasury.getAccountById(42)).thenReturn(null);

        PartyResolver.Resolution result = resolver.resolve("police", PartyFlag.GROUP, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, "police"), result);
    }

    @Test
    void group_archivedAccount_isRefused() {
        Party.Group group = new Party.Group("police", 42, AccountKind.GOVERNMENT);
        when(backend.findGroupParty("police")).thenReturn(group);
        Account archived = account(42, AccountType.GOVERNMENT, "police");
        archived.setArchived(true);
        when(treasury.getAccountById(42)).thenReturn(archived);

        PartyResolver.Resolution result = resolver.resolve("police", PartyFlag.GROUP, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_ARCHIVED_ACCOUNT, "police"), result);
    }

    @Test
    void group_requiresAuthorization_isRefused() {
        Party.Group group = new Party.Group("police", 42, AccountKind.GOVERNMENT);
        when(backend.findGroupParty("police")).thenReturn(group);
        Account requiresAuth = account(42, AccountType.GOVERNMENT, "police");
        requiresAuth.setRequiresAuthorization(true);
        when(treasury.getAccountById(42)).thenReturn(requiresAuth);

        PartyResolver.Resolution result = resolver.resolve("police", PartyFlag.GROUP, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_REQUIRES_AUTHORIZATION, "police"), result);
    }

    @Test
    void group_accountNoLongerOfTheStoredType_isRefused() {
        Party.Group group = new Party.Group("police", 42, AccountKind.GOVERNMENT);
        when(backend.findGroupParty("police")).thenReturn(group);
        when(treasury.getAccountById(42)).thenReturn(account(42, AccountType.BUSINESS, "police"));

        PartyResolver.Resolution result = resolver.resolve("police", PartyFlag.GROUP, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_TYPE_MISMATCH, "police"), result);
    }

    @Test
    void group_accountNowPersonal_isRefused() {
        Party.Group group = new Party.Group("police", 42, AccountKind.GOVERNMENT);
        when(backend.findGroupParty("police")).thenReturn(group);
        when(treasury.getAccountById(42)).thenReturn(account(42, AccountType.PERSONAL, "Steve"));

        PartyResolver.Resolution result = resolver.resolve("police", PartyFlag.GROUP, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_TYPE_MISMATCH, "police"), result);
    }

    // --- GOVERNMENT by name ---

    @Test
    void government_byName() {
        when(treasury.getGovernmentAccountByName("GovSecurity"))
                .thenReturn(account(42, AccountType.GOVERNMENT, "GovSecurity"));

        PartyResolver.Resolution result = resolver.resolve("GovSecurity", PartyFlag.GOVERNMENT, null);

        assertEquals(new PartyResolver.Resolution.Resolved(new Party.Account(42, AccountKind.GOVERNMENT)), result);
    }

    @Test
    void government_notFound_isUnknown() {
        when(treasury.getGovernmentAccountByName("Nope")).thenReturn(null);

        PartyResolver.Resolution result = resolver.resolve("Nope", PartyFlag.GOVERNMENT, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, "Nope"), result);
    }

    // --- BUSINESS / SYSTEM by name, among the sender's own accounts ---

    @Test
    void business_byName_amongTheSendersAccounts() {
        UUID sender = UUID.randomUUID();
        when(treasury.getAccountsByMember(sender)).thenReturn(List.of(account(7, AccountType.BUSINESS, "Acme")));

        // Matched without regard to case.
        PartyResolver.Resolution result = resolver.resolve("acme", PartyFlag.BUSINESS, sender);

        assertEquals(new PartyResolver.Resolution.Resolved(new Party.Account(7, AccountKind.BUSINESS)), result);
    }

    @Test
    void business_byName_notAMember_isUnknown() {
        UUID sender = UUID.randomUUID();
        when(treasury.getAccountsByMember(sender)).thenReturn(List.of());

        PartyResolver.Resolution result = resolver.resolve("Acme", PartyFlag.BUSINESS, sender);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, "Acme"), result);
    }

    @Test
    void business_twoAccountsWithOneName_isAmbiguous() {
        UUID sender = UUID.randomUUID();
        when(treasury.getAccountsByMember(sender)).thenReturn(List.of(
                account(7, AccountType.BUSINESS, "Acme"),
                account(8, AccountType.BUSINESS, "Acme")));

        PartyResolver.Resolution result = resolver.resolve("Acme", PartyFlag.BUSINESS, sender);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_ACCOUNT_AMBIGUOUS, "Acme"), result);
    }

    @Test
    void business_byId_needsNoMembership() {
        when(treasury.getAccountById(7)).thenReturn(account(7, AccountType.BUSINESS, "Acme"));

        PartyResolver.Resolution result = resolver.resolve("#7", PartyFlag.BUSINESS, null);

        assertEquals(new PartyResolver.Resolution.Resolved(new Party.Account(7, AccountKind.BUSINESS)), result);
        verify(treasury, never()).getAccountsByMember(any(UUID.class));
    }

    @Test
    void business_fromConsole_needsAnId() {
        PartyResolver.Resolution result = resolver.resolve("Acme", PartyFlag.BUSINESS, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, "Acme"), result);
        verifyNoInteractions(treasury);
    }

    // --- Account flag with "#<id>" ---

    @Test
    void idThatIsNotANumber_isUnknown() {
        PartyResolver.Resolution result = resolver.resolve("#abc", PartyFlag.GOVERNMENT, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, "#abc"), result);
        verifyNoInteractions(treasury);
    }

    @Test
    void idThatDoesNotExist_isUnknown() {
        when(treasury.getAccountById(99)).thenReturn(null);

        PartyResolver.Resolution result = resolver.resolve("#99", PartyFlag.SYSTEM, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, "#99"), result);
    }

    // --- Type and availability checks common to every account found ---

    @Test
    void flagAndTypeDiffer_isAMismatch() {
        when(treasury.getAccountById(42)).thenReturn(account(42, AccountType.GOVERNMENT, "GovSecurity"));

        PartyResolver.Resolution result = resolver.resolve("#42", PartyFlag.BUSINESS, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_TYPE_MISMATCH, "#42"), result);
    }

    @Test
    void personalAccount_isAMismatch() {
        when(treasury.getAccountById(5)).thenReturn(account(5, AccountType.PERSONAL, "Steve"));

        PartyResolver.Resolution result = resolver.resolve("#5", PartyFlag.BUSINESS, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_TYPE_MISMATCH, "#5"), result);
    }

    @Test
    void archivedAccount_isRefused() {
        Account archived = account(42, AccountType.GOVERNMENT, "GovSecurity");
        archived.setArchived(true);
        when(treasury.getGovernmentAccountByName("GovSecurity")).thenReturn(archived);

        PartyResolver.Resolution result = resolver.resolve("GovSecurity", PartyFlag.GOVERNMENT, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_ARCHIVED_ACCOUNT, "GovSecurity"), result);
    }

    @Test
    void accountThatRequiresAuthorization_isRefused() {
        Account requiresAuth = account(42, AccountType.GOVERNMENT, "GovSecurity");
        requiresAuth.setRequiresAuthorization(true);
        when(treasury.getGovernmentAccountByName("GovSecurity")).thenReturn(requiresAuth);

        PartyResolver.Resolution result = resolver.resolve("GovSecurity", PartyFlag.GOVERNMENT, null);

        assertEquals(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_REQUIRES_AUTHORIZATION, "GovSecurity"), result);
    }
}
