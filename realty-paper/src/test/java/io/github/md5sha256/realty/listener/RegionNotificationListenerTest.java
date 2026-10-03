package io.github.md5sha256.realty.listener;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.LeaseholdRoles;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.LeaseExpiredEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationResolvedEvent;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.api.event.RegionRentedEvent;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.notify.PartyRecipients;
import io.github.md5sha256.realty.settings.AccountManagers;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.PartyNames;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.AccountMember;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A non-player landlord (an account or a group) is told through the people who act for it, and
 * must never crash a notification or suppress the notice going to the other, player, party.
 */
@ExtendWith(MockitoExtension.class)
class RegionNotificationListenerTest {

    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private RealtyEventDispatch events;

    @Mock
    private Server server;

    private TreasuryApi treasury;
    private Permission vaultPermission;
    private Logger logger;
    private RegionNotificationListener listener;
    private WorldGuardRegion region;

    @BeforeEach
    void setUp() {
        // A real MessageContainer never throws: an unset key just renders as itself.
        treasury = mock(TreasuryApi.class);
        vaultPermission = mock(Permission.class);
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        ExecutorService sameThread = new AbstractExecutorService() {
            @Override public void shutdown() { }
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
            @Override public void execute(Runnable command) { command.run(); }
        };
        Settings settings = new Settings(null, null, null, new SimpleDateFormat("yyyy"),
                0, 0, 0, 0, List.of(), null, 0, 0, 0, 0, AccountManagers.MEMBERS);
        listener = new RegionNotificationListener(events, new MessageContainer(),
                new PartyNames(server, null, Clock.systemUTC()),
                new PartyRecipients(server, treasury, vaultPermission, new AtomicReference<>(settings)),
                new ExecutorState(Runnable::run, sameThread, sameThread), logger);
        ProtectedRegion protectedRegion = mock(ProtectedRegion.class);
        // Unused by the account-landlord case, which returns before rendering any text.
        lenient().when(protectedRegion.getId()).thenReturn("region-1");
        region = new WorldGuardRegion(protectedRegion, mock(World.class));
        // A rendered notice names the tenant by player name, so the offline-player lookup has to resolve.
        OfflinePlayer offlineTenant = mock(OfflinePlayer.class);
        lenient().when(offlineTenant.getName()).thenReturn("Tenant");
        lenient().when(server.getOfflinePlayer(TENANT)).thenReturn(offlineTenant);
    }

    @Test
    void rentedFromAPlayerLandlord_notifiesTheLandlord() {
        RegionRentedEvent event = new RegionRentedEvent(
                region, TENANT, new Party.Personal(LANDLORD), 10.0, 60L);

        listener.onRegionRented(event);

        ArgumentCaptor<RealtyNotificationEvent> captor = ArgumentCaptor.forClass(RealtyNotificationEvent.class);
        verify(events, times(1)).fireSync(captor.capture());
        Assertions.assertEquals(List.of(LANDLORD), captor.getValue().getTargets());
    }

    private static AccountMember row(UUID uuid) {
        return new AccountMember(0, uuid, UUID.randomUUID(), Instant.EPOCH);
    }

    private RegionRentedEvent rentedFrom(Party landlord) {
        return new RegionRentedEvent(region, TENANT, landlord, 10.0, 60L);
    }

    @Test
    void rentedFromAnAccountLandlord_notifiesEveryManager() {
        UUID authorizer = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        when(treasury.getAuthorizers(42)).thenReturn(List.of(row(authorizer)));
        when(treasury.getMembers(42)).thenReturn(List.of(row(member)));

        listener.onRegionRented(rentedFrom(new Party.Account(42, AccountKind.GOVERNMENT)));

        ArgumentCaptor<RealtyNotificationEvent> captor = ArgumentCaptor.forClass(RealtyNotificationEvent.class);
        verify(events, times(1)).fireSync(captor.capture());
        Assertions.assertEquals(Set.of(authorizer, member), Set.copyOf(captor.getValue().getTargets()));
    }

    @Test
    void rentedFromAGroupWithNobodyOnline_firesNothing() {
        when(server.getOnlinePlayers()).thenReturn(List.of());

        Assertions.assertDoesNotThrow(() -> listener.onRegionRented(
                rentedFrom(new Party.Group("police", 43, AccountKind.GOVERNMENT))));

        verify(events, never()).fireSync(any(RealtyNotificationEvent.class));
    }

    @Test
    void rentedFromAnAccountWhoseLookupFails_firesNothingAndDoesNotThrow() {
        when(treasury.getAuthorizers(42)).thenThrow(new IllegalStateException("treasury is down"));

        Assertions.assertDoesNotThrow(() -> listener.onRegionRented(
                rentedFrom(new Party.Account(42, AccountKind.GOVERNMENT))));

        verify(events, never()).fireSync(any(RealtyNotificationEvent.class));
    }

    @Test
    void modificationWithdrawnOnAVacantLease_notifiesNoTenant() {
        LeaseModificationResolvedEvent event = new LeaseModificationResolvedEvent(
                region, "WITHDRAWN", LeaseholdRoles.LANDLORD, new Party.Personal(LANDLORD), null);

        Assertions.assertDoesNotThrow(() -> listener.onModificationResolved(event));

        verify(events, never()).fireSync(any(RealtyNotificationEvent.class));
    }

    @Test
    void leaseExpiredWithAnAccountLandlord_stillNotifiesTheTenant() {
        LeaseExpiredEvent event = new LeaseExpiredEvent(
                region, TENANT, new Party.Account(42, AccountKind.GOVERNMENT));

        Assertions.assertDoesNotThrow(() -> listener.onLeaseExpired(event));

        ArgumentCaptor<RealtyNotificationEvent> captor = ArgumentCaptor.forClass(RealtyNotificationEvent.class);
        verify(events, times(1)).fireSync(captor.capture());
        Assertions.assertEquals(List.of(TENANT), captor.getValue().getTargets());
    }
}
