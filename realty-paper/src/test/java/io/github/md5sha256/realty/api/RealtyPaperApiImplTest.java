package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.economy.EconomyProvider;
import io.github.md5sha256.realty.economy.PaymentResult;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtyPaperApiImplTest {

    @Mock
    private RealtyBackend realtyApi;
    @Mock
    private EconomyProvider economyProvider;
    @Mock
    private Database database;
    @Mock
    private RegionProfileService regionProfileService;
    @Mock
    private SignTextApplicator signTextApplicator;
    @Mock
    private World world;

    private SignCache signCache;
    private RealtyPaperApiImpl api;
    private MockedStatic<Bukkit> bukkitMock;
    private MockedStatic<WorldGuard> worldGuardMock;
    private MockedStatic<BukkitAdapter> bukkitAdapterMock;

    private ProtectedRegion protectedRegion;
    private WorldGuardRegion wgRegion;

    private static final String REGION_ID = "test_region";
    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID BUYER_ID = UUID.randomUUID();
    private static final UUID AUTHORITY_ID = UUID.randomUUID();
    private static final UUID TITLE_HOLDER_ID = UUID.randomUUID();
    private static final UUID LANDLORD_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        signCache = new SignCache();
        ExecutorState executorState = new ExecutorState(Runnable::run, sameThreadExecutorService());
        api = new RealtyPaperApiImpl(realtyApi, economyProvider, executorState, database,
                regionProfileService, signTextApplicator, signCache);

        lenient().when(world.getUID()).thenReturn(WORLD_ID);

        protectedRegion = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(0, 0, 0), BlockVector3.at(100, 100, 100));
        wgRegion = new WorldGuardRegion(protectedRegion, world);

        bukkitMock = mockStatic(Bukkit.class);

        // Mock WorldGuard static chain: getInstance() -> platform -> regionContainer -> get() -> null
        // Returning null for RegionManager makes updateChildLandlords return early
        WorldGuard worldGuardInstance = org.mockito.Mockito.mock(WorldGuard.class);
        WorldGuardPlatform platform = org.mockito.Mockito.mock(WorldGuardPlatform.class);
        RegionContainer regionContainer = org.mockito.Mockito.mock(RegionContainer.class);
        worldGuardMock = mockStatic(WorldGuard.class);
        worldGuardMock.when(WorldGuard::getInstance).thenReturn(worldGuardInstance);
        lenient().when(worldGuardInstance.getPlatform()).thenReturn(platform);
        lenient().when(platform.getRegionContainer()).thenReturn(regionContainer);
        lenient().when(regionContainer.get(any())).thenReturn(null);

        bukkitAdapterMock = mockStatic(BukkitAdapter.class);
        bukkitAdapterMock.when(() -> BukkitAdapter.adapt(any(World.class))).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
        worldGuardMock.close();
        bukkitAdapterMock.close();
    }

    private static ExecutorService sameThreadExecutorService() {
        return new AbstractExecutorService() {
            private volatile boolean shutdown;

            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
                shutdown = true;
            }

            @Override
            public List<Runnable> shutdownNow() {
                shutdown = true;
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return shutdown;
            }

            @Override
            public boolean isTerminated() {
                return shutdown;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
    }

    // ═══════════════════════════════════════════════════
    // buy()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("buy")
    class Buy {

        @Test
        @DisplayName("returns NoFreeholdContract when no contract exists")
        void noFreeholdContract() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.BuyResult.NoFreeholdContract());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.NoFreeholdContract.class, result);
        }

        @Test
        @DisplayName("returns NotForSale when region is not for sale")
        void notForSale() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.BuyResult.NotForFreehold());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.NotForSale.class, result);
        }

        @Test
        @DisplayName("returns IsAuthority when buyer is the authority")
        void isAuthority() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.BuyResult.IsAuthority());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.IsAuthority.class, result);
        }

        @Test
        @DisplayName("returns IsTitleHolder when buyer already owns")
        void isTitleHolder() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.BuyResult.IsTitleHolder());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.IsTitleHolder.class, result);
        }

        @Test
        @DisplayName("returns InsufficientFunds and rolls back DB when balance is too low")
        void insufficientFunds() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.BuyResult.Success(1000.0, AUTHORITY_ID, TITLE_HOLDER_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(BUYER_ID)).thenReturn(500.0);

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.InsufficientFunds.class, result);
            RealtyPaperApi.BuyResult.InsufficientFunds insufficient =
                    (RealtyPaperApi.BuyResult.InsufficientFunds) result;
            Assertions.assertEquals(1000.0, insufficient.price());
            Assertions.assertEquals(500.0, insufficient.balance());
            verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, TITLE_HOLDER_ID, 1000.0);
        }

        @Test
        @DisplayName("returns PaymentFailed and rolls back DB when economy withdraw fails")
        void paymentFailed() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.BuyResult.Success(1000.0, AUTHORITY_ID, TITLE_HOLDER_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(BUYER_ID)).thenReturn(2000.0);
            when(economyProvider.transfer(eq(BUYER_ID), any(UUID.class), eq(1000.0), any()))
                    .thenReturn(new PaymentResult.Failure("Bank error"));

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.PaymentFailed.class, result);
            verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, TITLE_HOLDER_ID, 1000.0);
        }

        @Test
        @DisplayName("success transfers ownership and applies flags")
        void success() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.BuyResult.Success(1000.0, AUTHORITY_ID, TITLE_HOLDER_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of("price", "1000"));
            when(economyProvider.getBalance(BUYER_ID)).thenReturn(2000.0);
            when(economyProvider.transfer(eq(BUYER_ID), any(UUID.class), eq(1000.0), any()))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.Success.class, result);
            RealtyPaperApi.BuyResult.Success success = (RealtyPaperApi.BuyResult.Success) result;
            Assertions.assertEquals(1000.0, success.price());
            Assertions.assertEquals(REGION_ID, success.regionId());

            // Verify region ownership updated
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
            Assertions.assertEquals(1, protectedRegion.getOwners().size());
            Assertions.assertEquals(0, protectedRegion.getMembers().size());

            // Verify flags applied
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.SOLD), any());
            verify(signTextApplicator).updateLoadedSigns(eq(world), eq(REGION_ID),
                    eq(RegionState.SOLD), any());
        }

        @Test
        @DisplayName("returns TransferFailed when atomic buy fails")
        void transferFailedOnAtomicBuyFailure() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.BuyResult.UpdateFailed());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.TransferFailed.class, result);
        }
    }

    // ═══════════════════════════════════════════════════
    // rent()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("rent")
    class Rent {

        @Test
        @DisplayName("returns NoLeaseholdContract when no contract exists")
        void noLeaseholdContract() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RentResult.NoLeaseholdContract());

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.NoLeaseholdContract.class, result);
        }

        @Test
        @DisplayName("returns AlreadyOccupied when region is occupied")
        void alreadyOccupied() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RentResult.AlreadyOccupied());

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.AlreadyOccupied.class, result);
        }

        @Test
        @DisplayName("returns InsufficientFunds and rolls back DB when balance is too low")
        void insufficientFunds() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RentResult.Success(500.0, 3600, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT_ID)).thenReturn(100.0);

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.InsufficientFunds.class, result);
            verify(realtyApi).rollbackRent(REGION_ID, WORLD_ID);
        }

        @Test
        @DisplayName("success sets tenant as owner and applies LEASED flags")
        void success() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RentResult.Success(500.0, 3600, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT_ID)).thenReturn(1000.0);
            when(economyProvider.transfer(eq(TENANT_ID), eq(LANDLORD_ID), eq(500.0), any()))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.Success.class, result);
            Assertions.assertTrue(protectedRegion.getOwners().contains(TENANT_ID));
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.LEASED), any());
        }

        @Test
        @DisplayName("skips payment when price is zero")
        void zeroPriceSkipsPayment() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RentResult.Success(0.0, 3600, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.Success.class, result);
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any());
        }

        @Test
        @DisplayName("returns UpdateFailed when backend fails")
        void updateFailedOnBackendFailure() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RentResult.UpdateFailed());

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.UpdateFailed.class, result);
        }
    }

    // ═══════════════════════════════════════════════════
    // unrent()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("unrent")
    class Unrent {

        @Test
        @DisplayName("returns NoLeaseholdContract when no contract exists")
        void noLeaseholdContract() {
            when(realtyApi.unrentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.UnrentResult.NoLeaseholdContract());

            RealtyPaperApi.UnrentResult result = api.unrent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.UnrentResult.NoLeaseholdContract.class, result);
        }

        @Test
        @DisplayName("success clears owners and applies FOR_LEASE flags")
        void success() {
            when(realtyApi.unrentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.UnrentResult.Success(100.0, TENANT_ID, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.transfer(eq(LANDLORD_ID), eq(TENANT_ID), eq(100.0), any()))
                    .thenReturn(new PaymentResult.Success());

            protectedRegion.getOwners().addPlayer(TENANT_ID);

            RealtyPaperApi.UnrentResult result = api.unrent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.UnrentResult.Success.class, result);
            Assertions.assertEquals(0, protectedRegion.getOwners().size());
            Assertions.assertEquals(0, protectedRegion.getMembers().size());
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_LEASE), any());
        }

        @Test
        @DisplayName("returns RefundFailed and rolls back DB when landlord withdraw fails")
        void refundFailedOnLandlordWithdraw() {
            when(realtyApi.unrentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.UnrentResult.Success(100.0, TENANT_ID, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RentResult.Success(100.0, 3600, LANDLORD_ID));
            when(economyProvider.transfer(eq(LANDLORD_ID), eq(TENANT_ID), eq(100.0), any()))
                    .thenReturn(new PaymentResult.Failure("Insufficient funds"));

            RealtyPaperApi.UnrentResult result = api.unrent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.UnrentResult.RefundFailed.class, result);
            verify(realtyApi).rentRegion(REGION_ID, WORLD_ID, TENANT_ID);
        }
    }

    // ═══════════════════════════════════════════════════
    // extend()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("extend")
    class Extend {

        @Test
        @DisplayName("returns NoLeaseholdContract when no contract exists")
        void noLeaseholdContract() {
            when(realtyApi.renewLeasehold(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RenewLeaseholdResult.NoLeaseholdContract());

            RealtyPaperApi.ExtendResult result = api.extend(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.ExtendResult.NoLeaseholdContract.class, result);
        }

        @Test
        @DisplayName("returns NoExtensionsRemaining when exhausted")
        void noExtensionsRemaining() {
            when(realtyApi.renewLeasehold(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RenewLeaseholdResult.NoExtensionsRemaining());

            RealtyPaperApi.ExtendResult result = api.extend(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.ExtendResult.NoExtensionsRemaining.class, result);
        }

        @Test
        @DisplayName("returns InsufficientFunds and rolls back DB when balance is too low")
        void insufficientFunds() {
            when(realtyApi.renewLeasehold(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RenewLeaseholdResult.Success(200.0, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT_ID)).thenReturn(50.0);

            RealtyPaperApi.ExtendResult result = api.extend(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.ExtendResult.InsufficientFunds.class, result);
            verify(realtyApi).rollbackRenewLeasehold(REGION_ID, WORLD_ID, TENANT_ID);
        }

        @Test
        @DisplayName("success extends lease and updates signs")
        void success() {
            when(realtyApi.renewLeasehold(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.RenewLeaseholdResult.Success(200.0, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT_ID)).thenReturn(500.0);
            when(economyProvider.transfer(eq(TENANT_ID), eq(LANDLORD_ID), eq(200.0), any()))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.ExtendResult result = api.extend(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.ExtendResult.Success.class, result);
            RealtyPaperApi.ExtendResult.Success success = (RealtyPaperApi.ExtendResult.Success) result;
            Assertions.assertEquals(200.0, success.price());
            verify(signTextApplicator).updateLoadedSigns(eq(world), eq(REGION_ID),
                    eq(RegionState.LEASED), any());
        }
    }

    // ═══════════════════════════════════════════════════
    // setTitleHolder()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("setTitleHolder")
    class SetTitleHolder {

        @Test
        @DisplayName("returns NoFreeholdContract when no contract exists")
        void noFreeholdContract() {
            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.NoFreeholdContract());

            RealtyPaperApi.SetTitleHolderResult result =
                    api.setTitleHolder(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.NoFreeholdContract.class, result);
        }

        @Test
        @DisplayName("success with holder sets owner and applies SOLD")
        void successWithHolder() {
            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.Success(TITLE_HOLDER_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.SetTitleHolderResult result =
                    api.setTitleHolder(wgRegion, BUYER_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.Success.class, result);
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.SOLD), any());
        }

        @Test
        @DisplayName("success with null clears owner and applies FOR_SALE")
        void successWithNull() {
            protectedRegion.getOwners().addPlayer(TITLE_HOLDER_ID);

            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, null))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.Success(TITLE_HOLDER_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.SetTitleHolderResult result =
                    api.setTitleHolder(wgRegion, null).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.Success.class, result);
            Assertions.assertEquals(0, protectedRegion.getOwners().size());
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_SALE), any());
        }
    }

    // ═══════════════════════════════════════════════════
    // setTenant()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("setTenant")
    class SetTenant {

        @Test
        @DisplayName("success with tenant sets owner and applies LEASED")
        void successWithTenant() {
            when(realtyApi.setTenant(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.SetTenantResult.Success(null, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.SetTenantResult result =
                    api.setTenant(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.Success.class, result);
            Assertions.assertTrue(protectedRegion.getOwners().contains(TENANT_ID));
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.LEASED), any());
        }

        @Test
        @DisplayName("success with null clears owner and applies FOR_LEASE")
        void successWithNull() {
            protectedRegion.getOwners().addPlayer(TENANT_ID);

            when(realtyApi.setTenant(REGION_ID, WORLD_ID, null))
                    .thenReturn(new RealtyBackend.SetTenantResult.Success(TENANT_ID, LANDLORD_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.SetTenantResult result =
                    api.setTenant(wgRegion, null).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.Success.class, result);
            Assertions.assertEquals(0, protectedRegion.getOwners().size());
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_LEASE), any());
        }

        @Test
        @DisplayName("returns NoLeaseholdContract when no contract exists")
        void noLeaseholdContract() {
            when(realtyApi.setTenant(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.SetTenantResult.NoLeaseholdContract());

            RealtyPaperApi.SetTenantResult result =
                    api.setTenant(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.NoLeaseholdContract.class, result);
        }
    }

    // ═══════════════════════════════════════════════════
    // setLandlord()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("setLandlord")
    class SetLandlord {

        @Test
        @DisplayName("success clears members")
        void success() {
            protectedRegion.getMembers().addPlayer(UUID.randomUUID());

            when(realtyApi.setLandlord(REGION_ID, WORLD_ID, LANDLORD_ID))
                    .thenReturn(new RealtyBackend.SetLandlordResult.Success(UUID.randomUUID()));

            RealtyPaperApi.SetLandlordResult result =
                    api.setLandlord(wgRegion, LANDLORD_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetLandlordResult.Success.class, result);
            Assertions.assertEquals(0, protectedRegion.getMembers().size());
        }

        @Test
        @DisplayName("returns NoLeaseholdContract when no contract exists")
        void noLeaseholdContract() {
            when(realtyApi.setLandlord(REGION_ID, WORLD_ID, LANDLORD_ID))
                    .thenReturn(new RealtyBackend.SetLandlordResult.NoLeaseholdContract());

            RealtyPaperApi.SetLandlordResult result =
                    api.setLandlord(wgRegion, LANDLORD_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetLandlordResult.NoLeaseholdContract.class, result);
        }
    }

    // ═══════════════════════════════════════════════════
    // createFreehold()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("createFreehold")
    class CreateFreehold {

        @Test
        @DisplayName("success adds authority as member and applies flags")
        void success() {
            when(realtyApi.createFreehold(REGION_ID, WORLD_ID, 1000.0, AUTHORITY_ID, null))
                    .thenReturn(true);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.CreateFreeholdResult result =
                    api.createFreehold(wgRegion, 1000.0, AUTHORITY_ID, null).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateFreeholdResult.Success.class, result);
            Assertions.assertTrue(protectedRegion.getMembers().contains(AUTHORITY_ID));
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_SALE), any());
        }

        @Test
        @DisplayName("success with title holder applies SOLD state")
        void successWithTitleHolder() {
            when(realtyApi.createFreehold(REGION_ID, WORLD_ID, 1000.0, AUTHORITY_ID, TITLE_HOLDER_ID))
                    .thenReturn(true);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.CreateFreeholdResult result =
                    api.createFreehold(wgRegion, 1000.0, AUTHORITY_ID, TITLE_HOLDER_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateFreeholdResult.Success.class, result);
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.SOLD), any());
        }

        @Test
        @DisplayName("returns AlreadyRegistered when region exists")
        void alreadyRegistered() {
            when(realtyApi.createFreehold(REGION_ID, WORLD_ID, 1000.0, AUTHORITY_ID, null))
                    .thenReturn(false);

            RealtyPaperApi.CreateFreeholdResult result =
                    api.createFreehold(wgRegion, 1000.0, AUTHORITY_ID, null).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateFreeholdResult.AlreadyRegistered.class, result);
        }
    }

    // ═══════════════════════════════════════════════════
    // createLeasehold()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("createLeasehold")
    class CreateLeasehold {

        @Test
        @DisplayName("success applies FOR_LEASE flags")
        void success() {
            when(realtyApi.createLeasehold(REGION_ID, WORLD_ID, 500.0, 3600, 3, LANDLORD_ID))
                    .thenReturn(true);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.CreateLeaseholdResult result =
                    api.createLeasehold(wgRegion, 500.0, 3600, 3, LANDLORD_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateLeaseholdResult.Success.class, result);
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_LEASE), any());
        }

        @Test
        @DisplayName("returns AlreadyRegistered when region exists")
        void alreadyRegistered() {
            when(realtyApi.createLeasehold(REGION_ID, WORLD_ID, 500.0, 3600, 3, LANDLORD_ID))
                    .thenReturn(false);

            RealtyPaperApi.CreateLeaseholdResult result =
                    api.createLeasehold(wgRegion, 500.0, 3600, 3, LANDLORD_ID).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateLeaseholdResult.AlreadyRegistered.class, result);
        }
    }
}
