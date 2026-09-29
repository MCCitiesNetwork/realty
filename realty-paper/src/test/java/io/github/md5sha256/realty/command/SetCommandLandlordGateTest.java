package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.settings.AccountManagers;
import io.github.md5sha256.realty.settings.Settings;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.AccountMember;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Covers {@link SetCommandGroup.LandlordGate}, the landlord check {@code /realty set} makes on a
 * vacant leasehold before calling the backend. Moving the lease follows the money; the terms follow
 * whoever acts for the landlord.
 */
class SetCommandLandlordGateTest {

    private static final Party.Group POLICE = new Party.Group("police", 43, AccountKind.GOVERNMENT);

    private final UUID playerId = UUID.randomUUID();
    private TreasuryApi treasury;
    private Permission vaultPermission;
    private OfflinePlayer player;

    @BeforeEach
    void setUp() {
        treasury = Mockito.mock(TreasuryApi.class);
        vaultPermission = Mockito.mock(Permission.class);
        player = Mockito.mock(OfflinePlayer.class);
        Mockito.when(player.getUniqueId()).thenReturn(playerId);
        Mockito.when(treasury.getMembers(Mockito.anyInt())).thenReturn(List.of());
        Mockito.when(treasury.getAuthorizers(Mockito.anyInt())).thenReturn(List.of());
    }

    private ActorContext contextFor(Party landlord) {
        AtomicReference<Settings> settings = new AtomicReference<>(new Settings(null, null,
                null, new java.text.SimpleDateFormat("yyyy"), 0, 0, 0, 0, List.of(), null,
                0, 0, 0, 0, AccountManagers.AUTHORIZERS));
        ActorContexts contexts = new ActorContexts(treasury, vaultPermission, settings,
                Mockito.mock(RealtyBackend.class));
        return contexts.of(player, false, List.of(landlord));
    }

    private static AccountMember row(UUID uuid) {
        return new AccountMember(0, uuid, UUID.randomUUID(), Instant.EPOCH);
    }

    @Test
    void setLandlord_usesTheReassignGate() {
        Assertions.assertEquals(SetCommandGroup.LandlordGate.REASSIGNS, SetCommandGroup.SET_LANDLORD_GATE);
    }

    @Test
    void authorizerOfTheGroupsAccount_notInTheGroup_passesTheSetLandlordGate() {
        Mockito.when(treasury.getAuthorizers(43)).thenReturn(List.of(row(playerId)));
        ActorContext actor = contextFor(POLICE);

        Assertions.assertTrue(SetCommandGroup.SET_LANDLORD_GATE.admits(actor, POLICE));
        // Not in the group, so the lease's terms are not theirs to set.
        Assertions.assertFalse(SetCommandGroup.LandlordGate.MANAGES.admits(actor, POLICE));
    }

    @Test
    void groupMember_notAnAuthorizer_failsTheSetLandlordGate() {
        Mockito.when(vaultPermission.playerInGroup((String) null, player, "police")).thenReturn(true);
        ActorContext actor = contextFor(POLICE);

        Assertions.assertFalse(SetCommandGroup.SET_LANDLORD_GATE.admits(actor, POLICE));
        // Still acts for the group, so the other set subcommands let them through.
        Assertions.assertTrue(SetCommandGroup.LandlordGate.MANAGES.admits(actor, POLICE));
    }

    @Test
    void admin_passesEitherGate() {
        ActorContext admin = ActorContext.player(playerId, true);

        Assertions.assertTrue(SetCommandGroup.LandlordGate.REASSIGNS.admits(admin, POLICE));
        Assertions.assertTrue(SetCommandGroup.LandlordGate.MANAGES.admits(admin, POLICE));
    }
}
