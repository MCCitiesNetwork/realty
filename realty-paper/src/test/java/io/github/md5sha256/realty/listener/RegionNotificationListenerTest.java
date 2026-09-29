package io.github.md5sha256.realty.listener;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.LeaseExpiredEvent;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.api.event.RegionRentedEvent;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A non-player landlord (an admin-assigned {@code Party.Account}) must never crash a
 * notification, and must never suppress the notice going to the other, player, party.
 */
@ExtendWith(MockitoExtension.class)
class RegionNotificationListenerTest {

    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private RealtyEventDispatch events;

    private RegionNotificationListener listener;
    private WorldGuardRegion region;

    @BeforeEach
    void setUp() {
        // A real MessageContainer never throws: an unset key just renders as itself.
        listener = new RegionNotificationListener(events, new MessageContainer());
        ProtectedRegion protectedRegion = mock(ProtectedRegion.class);
        // Unused by the account-landlord case, which returns before rendering any text.
        lenient().when(protectedRegion.getId()).thenReturn("region-1");
        region = new WorldGuardRegion(protectedRegion, mock(World.class));
    }

    @Test
    void rentedFromAPlayerLandlord_notifiesTheLandlord() {
        RegionRentedEvent event = new RegionRentedEvent(
                region, TENANT, new Party.Personal(LANDLORD), 10.0, 60L);

        // The rendered notice names the tenant by player name, so Bukkit's offline-player
        // lookup has to resolve to something rather than NPE on an uninitialised server.
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            OfflinePlayer offlineTenant = mock(OfflinePlayer.class);
            when(offlineTenant.getName()).thenReturn("Tenant");
            bukkit.when(() -> Bukkit.getOfflinePlayer(TENANT)).thenReturn(offlineTenant);

            listener.onRegionRented(event);
        }

        ArgumentCaptor<RealtyNotificationEvent> captor = ArgumentCaptor.forClass(RealtyNotificationEvent.class);
        verify(events, times(1)).fireSync(captor.capture());
        Assertions.assertEquals(List.of(LANDLORD), captor.getValue().getTargets());
    }

    @Test
    void rentedFromAnAccountLandlord_firesNothingAndDoesNotThrow() {
        RegionRentedEvent event = new RegionRentedEvent(
                region, TENANT, new Party.Account(42, AccountKind.GOVERNMENT), 10.0, 60L);

        Assertions.assertDoesNotThrow(() -> listener.onRegionRented(event));

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
