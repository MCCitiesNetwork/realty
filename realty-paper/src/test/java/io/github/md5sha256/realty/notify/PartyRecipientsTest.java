package io.github.md5sha256.realty.notify;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.settings.AccountManagers;
import io.github.md5sha256.realty.settings.Settings;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.AccountMember;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class PartyRecipientsTest {

    private static final Party.Account GOV = Party.account(42, AccountKind.GOVERNMENT);
    private static final Party.Group POLICE = Party.group("police", 43, AccountKind.GOVERNMENT);

    private final UUID member = UUID.randomUUID();
    private final UUID authorizer = UUID.randomUUID();
    private final UUID both = UUID.randomUUID();
    private Server server;
    private TreasuryApi treasury;
    private Permission vaultPermission;

    @BeforeEach
    void setUp() {
        server = Mockito.mock(Server.class);
        treasury = Mockito.mock(TreasuryApi.class);
        vaultPermission = Mockito.mock(Permission.class);
        Mockito.when(treasury.getMembers(42)).thenReturn(List.of(row(member), row(both)));
        Mockito.when(treasury.getAuthorizers(42)).thenReturn(List.of(row(authorizer), row(both)));
    }

    private static AccountMember row(UUID uuid) {
        return new AccountMember(0, uuid, UUID.randomUUID(), Instant.EPOCH);
    }

    private PartyRecipients recipients(TreasuryApi treasury, AccountManagers accountManagers) {
        return new PartyRecipients(server, treasury, vaultPermission, new AtomicReference<>(
                new Settings(null, null, null, new java.text.SimpleDateFormat("yyyy"),
                        0, 0, 0, 0, List.of(), null, 0, 0, 0, 0, accountManagers)));
    }

    private Player online(UUID uuid, boolean inPolice) {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(uuid);
        Mockito.when(vaultPermission.playerInGroup((String) null, player, "police")).thenReturn(inPolice);
        return player;
    }

    @Test
    void player_isTheOnlyRecipient() {
        UUID id = UUID.randomUUID();

        Assertions.assertEquals(List.of(id),
                recipients(treasury, AccountManagers.MEMBERS).expand(Party.personal(id)));
    }

    @Test
    void account_underMembers_reachesMembersAndAuthorizers_onceEach() {
        List<UUID> result = recipients(treasury, AccountManagers.MEMBERS).expand(GOV);

        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals(Set.of(member, authorizer, both), Set.copyOf(result));
    }

    @Test
    void account_underAuthorizers_reachesAuthorizersOnly() {
        List<UUID> result = recipients(treasury, AccountManagers.AUTHORIZERS).expand(GOV);

        Assertions.assertEquals(Set.of(authorizer, both), Set.copyOf(result));
        Assertions.assertEquals(2, result.size());
    }

    @Test
    void account_includesOfflineMembers() {
        // Nobody is online: the server is never asked, and the members still come back.
        Mockito.when(server.getOnlinePlayers()).thenReturn(List.of());

        Assertions.assertTrue(recipients(treasury, AccountManagers.MEMBERS).expand(GOV).contains(member));
    }

    @Test
    void group_reachesOnlineMembersOnly() {
        UUID inGroup = UUID.randomUUID();
        UUID outOfGroup = UUID.randomUUID();
        Mockito.doReturn(List.of(online(inGroup, true), online(outOfGroup, false)))
                .when(server).getOnlinePlayers();

        Assertions.assertEquals(List.of(inGroup),
                recipients(treasury, AccountManagers.MEMBERS).expand(POLICE));
    }

    @Test
    void group_withNobodyOnline_isEmpty() {
        Mockito.doReturn(List.of()).when(server).getOnlinePlayers();

        Assertions.assertTrue(recipients(treasury, AccountManagers.MEMBERS).expand(POLICE).isEmpty());
    }

    @Test
    void withoutTreasury_anAccountHasNoRecipients() {
        Assertions.assertTrue(recipients(null, AccountManagers.MEMBERS).expand(GOV).isEmpty());
    }
}
