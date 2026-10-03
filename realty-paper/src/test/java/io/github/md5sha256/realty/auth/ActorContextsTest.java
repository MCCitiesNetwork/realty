package io.github.md5sha256.realty.auth;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.settings.AccountManagers;
import io.github.md5sha256.realty.settings.Settings;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.AccountMember;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class ActorContextsTest {

    private static final Party.Account GOV = Party.account(42, AccountKind.GOVERNMENT);
    private static final Party.Account ACME = Party.account(7, AccountKind.BUSINESS);
    private static final Party.Group POLICE = Party.group("police", 43, AccountKind.GOVERNMENT);

    private final UUID playerId = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();
    private TreasuryApi treasury;
    private Permission vaultPermission;
    private RealtyBackend backend;
    private OfflinePlayer player;

    @BeforeEach
    void setUp() {
        treasury = Mockito.mock(TreasuryApi.class);
        vaultPermission = Mockito.mock(Permission.class);
        backend = Mockito.mock(RealtyBackend.class);
        player = Mockito.mock(OfflinePlayer.class);
        Mockito.when(player.getUniqueId()).thenReturn(playerId);
        Mockito.when(treasury.getMembers(Mockito.anyInt())).thenReturn(List.of());
        Mockito.when(treasury.getAuthorizers(Mockito.anyInt())).thenReturn(List.of());
    }

    private AccountMember member(UUID uuid) {
        return new AccountMember(0, uuid, someoneElse, Instant.EPOCH);
    }

    private ActorContexts contexts(AccountManagers accountManagers) {
        return new ActorContexts(treasury, vaultPermission, settingsWith(accountManagers), backend);
    }

    private static AtomicReference<Settings> settingsWith(AccountManagers accountManagers) {
        return new AtomicReference<>(new Settings(null, null, null,
                new java.text.SimpleDateFormat("yyyy"), 0, 0, 0, 0, List.of(), null, 0, 0, 0, 0,
                accountManagers));
    }

    @Test
    void member_managesButDoesNotReassign_underMembers() {
        Mockito.when(treasury.getMembers(42)).thenReturn(List.of(member(playerId)));

        ActorContext ctx = contexts(AccountManagers.MEMBERS).of(player, false, List.of(GOV));

        Assertions.assertTrue(ctx.manages().contains(GOV));
        Assertions.assertFalse(ctx.reassigns().contains(GOV));
    }

    @Test
    void member_doesNotManage_underAuthorizers() {
        Mockito.when(treasury.getMembers(42)).thenReturn(List.of(member(playerId)));

        ActorContext ctx = contexts(AccountManagers.AUTHORIZERS).of(player, false, List.of(GOV));

        Assertions.assertFalse(ctx.manages().contains(GOV));
        Assertions.assertFalse(ctx.reassigns().contains(GOV));
    }

    @Test
    void authorizer_managesAndReassigns_underBothSettings() {
        Mockito.when(treasury.getAuthorizers(42)).thenReturn(List.of(member(someoneElse), member(playerId)));

        for (AccountManagers setting : AccountManagers.values()) {
            ActorContext ctx = contexts(setting).of(player, false, List.of(GOV));

            Assertions.assertTrue(ctx.manages().contains(GOV), setting.name());
            Assertions.assertTrue(ctx.reassigns().contains(GOV), setting.name());
        }
    }

    @Test
    void groupMember_managesTheGroup() {
        Mockito.when(vaultPermission.playerInGroup((String) null, player, "police")).thenReturn(true);

        ActorContext ctx = contexts(AccountManagers.MEMBERS).of(player, false, List.of(POLICE));

        Assertions.assertTrue(ctx.manages().contains(POLICE));
        Assertions.assertFalse(ctx.reassigns().contains(POLICE));
    }

    @Test
    void groupMember_reassignsOnlyAsAuthorizerOfTheGroupsAccount() {
        Mockito.when(vaultPermission.playerInGroup((String) null, player, "police")).thenReturn(true);
        // A member of the group's account is not enough to move the role away from the group.
        Mockito.when(treasury.getMembers(43)).thenReturn(List.of(member(playerId)));
        ActorContexts contexts = contexts(AccountManagers.MEMBERS);

        Assertions.assertFalse(contexts.of(player, false, List.of(POLICE)).reassigns().contains(POLICE));

        Mockito.when(treasury.getAuthorizers(43)).thenReturn(List.of(member(playerId)));

        Assertions.assertTrue(contexts.of(player, false, List.of(POLICE)).reassigns().contains(POLICE));
    }

    @Test
    void withoutTreasury_onlyThePlayerIsManaged() {
        Mockito.when(vaultPermission.playerInGroup((String) null, player, "police")).thenReturn(true);
        ActorContexts contexts = new ActorContexts(null, vaultPermission,
                settingsWith(AccountManagers.MEMBERS), backend);

        ActorContext ctx = contexts.of(player, false, List.of(GOV, POLICE));

        Assertions.assertEquals(Set.of(Party.personal(playerId)), ctx.manages());
        Assertions.assertEquals(Set.of(Party.personal(playerId)), ctx.reassigns());
    }

    @Test
    void withoutVaultPermission_groupsAreNotManaged() {
        Mockito.when(treasury.getMembers(43)).thenReturn(List.of(member(playerId)));
        Mockito.when(treasury.getAuthorizers(43)).thenReturn(List.of(member(playerId)));
        ActorContexts contexts = new ActorContexts(treasury, null,
                settingsWith(AccountManagers.MEMBERS), backend);

        ActorContext ctx = contexts.of(player, false, List.of(POLICE));

        Assertions.assertFalse(ctx.manages().contains(POLICE));
    }

    @Test
    void onlyCandidatesAreTested() {
        contexts(AccountManagers.MEMBERS).of(player, false, List.of(GOV));

        Mockito.verify(treasury).getMembers(42);
        Mockito.verify(treasury, Mockito.never()).getMembers(Mockito.intThat(id -> id != 42));
        Mockito.verify(treasury, Mockito.never()).getAuthorizers(Mockito.intThat(id -> id != 42));
    }

    @Test
    void anotherPlayer_isNeverManaged() {
        Party.Personal other = Party.personal(someoneElse);

        ActorContext ctx = contexts(AccountManagers.MEMBERS).of(player, false, List.of(other));

        Assertions.assertFalse(ctx.manages().contains(other));
        Assertions.assertFalse(ctx.reassigns().contains(other));
    }

    @Test
    void forRegion_testsTheLandlordTheAuthorityAndTheExtra() {
        World world = Mockito.mock(World.class);
        UUID worldId = UUID.randomUUID();
        Mockito.when(world.getUID()).thenReturn(worldId);
        ProtectedRegion protectedRegion = Mockito.mock(ProtectedRegion.class);
        Mockito.when(protectedRegion.getId()).thenReturn("plot");
        WorldGuardRegion region = new WorldGuardRegion(protectedRegion, world);
        LeaseholdContractEntity lease = Mockito.mock(LeaseholdContractEntity.class);
        Mockito.when(lease.landlord()).thenReturn(GOV);
        Mockito.when(backend.getLeaseholdContract("plot", worldId)).thenReturn(lease);
        Mockito.when(backend.getFreeholdContract("plot", worldId))
                .thenReturn(new FreeholdContractEntity(1, ACME, null, null, false));
        Mockito.when(treasury.getMembers(42)).thenReturn(List.of(member(playerId)));
        Mockito.when(treasury.getMembers(7)).thenReturn(List.of(member(playerId)));
        Mockito.when(vaultPermission.playerInGroup((String) null, player, "police")).thenReturn(true);

        ActorContext ctx = contexts(AccountManagers.MEMBERS).forRegion(player, true, region, POLICE);

        Assertions.assertEquals(Set.of(Party.personal(playerId), GOV, ACME, POLICE), ctx.manages());
        Assertions.assertTrue(ctx.bypass());
    }

    @Test
    void forEveryParty_testsEveryNonPlayerParty() {
        Mockito.when(backend.listNonPlayerParties()).thenReturn(List.of(GOV, ACME));
        Mockito.when(treasury.getAuthorizers(7)).thenReturn(List.of(member(playerId)));

        ActorContext ctx = contexts(AccountManagers.MEMBERS).forEveryParty(player);

        Assertions.assertEquals(Set.of(Party.personal(playerId), ACME), ctx.manages());
        Assertions.assertFalse(ctx.bypass());
    }
}
