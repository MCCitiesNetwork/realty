package io.github.md5sha256.realty.api;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.command.util.SafeLocationFinder;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.economy.EconomyProvider;
import io.github.md5sha256.realty.economy.PaymentResult;
import io.github.md5sha256.realty.settings.AccountManagers;
import io.github.md5sha256.realty.settings.Settings;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.AccountMember;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
    private Server server;
    @Mock
    private World world;
    @Mock
    private TreasuryApi treasury;

    private SignCache signCache;
    private RealtyPaperApiImpl api;
    private MockedStatic<WorldGuard> worldGuardMock;
    private MockedStatic<BukkitAdapter> bukkitAdapterMock;

    private ProtectedRegion protectedRegion;
    private WorldGuardRegion wgRegion;

    private static final String REGION_ID = "test_region";
    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID BUYER_ID = UUID.randomUUID();
    private static final ActorContext BUYER_CTX = ActorContext.player(BUYER_ID, false);
    private static final UUID AUTHORITY_ID = UUID.randomUUID();
    private static final UUID TITLE_HOLDER_ID = UUID.randomUUID();
    private static final UUID LANDLORD_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Party.Personal BUYER = Party.personal(BUYER_ID);
    private static final Party.Personal TITLE_HOLDER = Party.personal(TITLE_HOLDER_ID);
    private static final Party.Personal LANDLORD = Party.personal(LANDLORD_ID);
    private static final Party.Personal TENANT = Party.personal(TENANT_ID);
    private static final Party.Account GOVERNMENT = Party.account(42, AccountKind.GOVERNMENT);
    private static final RealtyBackend.RentResult.Success LET =
            new RealtyBackend.RentResult.Success(500.0, 3600, Party.personal(LANDLORD_ID), 7);
    /** A renewal at 200 that applied a landlord's change of terms on the way. */
    private static final RealtyBackend.RenewLeaseholdResult.Success RENEWED =
            new RealtyBackend.RenewLeaseholdResult.Success(200.0, Party.personal(LANDLORD_ID),
                    new RealtyBackend.RenewUndo(8,
                            new RealtyBackend.AppliedTerms(3, 9, 150.0, 3600, 5, 1)));
    private static final RealtyBackend.UnrentResult.Success ENDED =
            new RealtyBackend.UnrentResult.Success(100.0, TENANT_ID, Party.personal(LANDLORD_ID),
                    new RealtyBackend.Tenancy(
                            LocalDateTime.of(2026, 9, 1, 12, 0), LocalDateTime.of(2026, 10, 1, 12, 0), 3),
                    10);
    /** A reservation at 1000 that withdrew one offer, which a rollback has to put back. */
    private static final RealtyBackend.BuyResult.Success RESERVED = new RealtyBackend.BuyResult.Success(
            1000.0, Party.personal(AUTHORITY_ID), TITLE_HOLDER_ID,
            new RealtyBackend.BuyUndo(42,
                    List.of(new RealtyBackend.WithdrawnOffer(
                            UUID.randomUUID(), 500.0, LocalDateTime.of(2026, 8, 1, 12, 0))),
                    List.of(UUID.randomUUID())));

    @BeforeEach
    void setUp() {
        signCache = new SignCache();
        ExecutorState executorState = new ExecutorState(Runnable::run, sameThreadExecutorService(), sameThreadExecutorService());
        lenient().when(treasury.getMembers(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        lenient().when(treasury.getAuthorizers(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        ActorContexts actorContexts = new ActorContexts(treasury, null,
                new AtomicReference<>(new Settings(null, null, null, new SimpleDateFormat("yyyy"),
                        0, 0, 0, 0, List.of(), null, 0, 0, 0, 0, AccountManagers.MEMBERS)),
                realtyApi);
        api = new RealtyPaperApiImpl(server, realtyApi, economyProvider, executorState, database,
                regionProfileService, signTextApplicator, signCache, () -> 604800,
                new SafeLocationFinder(), stubPlayerNameService(), accountId -> CompletableFuture.completedFuture(Optional.empty()),
                actorContexts);

        lenient().when(world.getUID()).thenReturn(WORLD_ID);

        protectedRegion = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(0, 0, 0), BlockVector3.at(100, 100, 100));
        wgRegion = new WorldGuardRegion(protectedRegion, world);

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
        worldGuardMock.close();
        bukkitAdapterMock.close();
    }

    private static PlayerNameService stubPlayerNameService() {
        return new PlayerNameService() {
            @Override
            public CompletableFuture<Optional<String>> nameOf(UUID id) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletableFuture<Optional<UUID>> uuidOf(String name) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
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
    @DisplayName("actorContext")
    class ActorContextFactory {

        @Test
        @DisplayName("a player who authorizes the region's account authority manages it")
        void authorizerOfTheAuthority_managesIt() {
            when(realtyApi.getFreeholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(new FreeholdContractEntity(1, GOVERNMENT, TITLE_HOLDER_ID, 1000.0, true));
            when(treasury.getAuthorizers(42))
                    .thenReturn(List.of(new AccountMember(0, BUYER_ID, TITLE_HOLDER_ID, Instant.EPOCH)));
            OfflinePlayer buyer = org.mockito.Mockito.mock(OfflinePlayer.class);
            when(buyer.getUniqueId()).thenReturn(BUYER_ID);

            ActorContext ctx = api.actorContext(buyer, false, wgRegion).join();

            Assertions.assertEquals(BUYER_ID, ctx.player());
            Assertions.assertTrue(ctx.mayManage(GOVERNMENT));
            Assertions.assertTrue(ctx.mayReassign(GOVERNMENT));
            Assertions.assertFalse(ctx.bypass());
        }

        @Test
        @DisplayName("an extra party is tested too")
        void extraParty_isTested() {
            Party.Account business = Party.account(7, AccountKind.BUSINESS);
            when(treasury.getMembers(7))
                    .thenReturn(List.of(new AccountMember(0, BUYER_ID, TITLE_HOLDER_ID, Instant.EPOCH)));
            OfflinePlayer buyer = org.mockito.Mockito.mock(OfflinePlayer.class);
            when(buyer.getUniqueId()).thenReturn(BUYER_ID);

            ActorContext ctx = api.actorContext(buyer, true, wgRegion, business).join();

            Assertions.assertTrue(ctx.manages().contains(business));
            Assertions.assertFalse(ctx.reassigns().contains(business));
            Assertions.assertTrue(ctx.bypass());
        }
    }

    @Nested
    @DisplayName("buy")
    class Buy {

        @Test
        @DisplayName("a context with no player fails the future and does not throw at the call")
        void contextWithoutAPlayer_failsTheFuture() {
            CompletableFuture<RealtyPaperApi.BuyResult> future =
                    Assertions.assertDoesNotThrow(() -> api.buy(wgRegion, ActorContext.console(), false));

            Assertions.assertTrue(future.isCompletedExceptionally());
            verify(realtyApi, never()).executeBuy(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("returns NoFreeholdContract when no contract exists")
        void noFreeholdContract() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.BuyResult.NoFreeholdContract());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.NoFreeholdContract.class, result);
        }

        @Test
        @DisplayName("returns NotForSale when region is not for sale")
        void notForSale() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.BuyResult.NotForFreehold());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.NotForSale.class, result);
        }

        @Test
        @DisplayName("returns IsAuthority when buyer is the authority")
        void isAuthority() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.BuyResult.IsAuthority());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.IsAuthority.class, result);
        }

        @Test
        @DisplayName("returns IsTitleHolder when buyer already owns")
        void isTitleHolder() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.BuyResult.IsTitleHolder());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.IsTitleHolder.class, result);
        }

        @Test
        @DisplayName("returns InsufficientFunds and rolls back DB when balance is too low")
        void insufficientFunds() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(RESERVED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(BUYER)).thenReturn(500.0);

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.InsufficientFunds.class, result);
            RealtyPaperApi.BuyResult.InsufficientFunds insufficient =
                    (RealtyPaperApi.BuyResult.InsufficientFunds) result;
            Assertions.assertEquals(1000.0, insufficient.price());
            Assertions.assertEquals(500.0, insufficient.balance());
            // Handed the reservation itself, so it can put back everything that was taken.
            verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, BUYER_ID, RESERVED);
        }

        @Test
        @DisplayName("returns PaymentFailed and rolls back DB when economy withdraw fails")
        void paymentFailed() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(RESERVED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(BUYER)).thenReturn(2000.0);
            when(economyProvider.transfer(eq(BUYER), eq(TITLE_HOLDER), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Failure("Bank error"));

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.PaymentFailed.class, result);
            // Handed the reservation itself, so it can put back everything that was taken.
            verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, BUYER_ID, RESERVED);
        }

        @Test
        @DisplayName("success transfers ownership and applies flags")
        void success() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(RESERVED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of("price", "1000"));
            when(economyProvider.getBalance(BUYER)).thenReturn(2000.0);
            when(economyProvider.transfer(eq(BUYER), eq(TITLE_HOLDER), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.Success.class, result);
            RealtyPaperApi.BuyResult.Success success = (RealtyPaperApi.BuyResult.Success) result;
            Assertions.assertEquals(1000.0, success.price());
            Assertions.assertEquals(REGION_ID, success.regionId());
            Assertions.assertEquals(TITLE_HOLDER, success.previousTitleHolder());

            // Verify region ownership updated
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
            Assertions.assertEquals(1, protectedRegion.getOwners().size());
            Assertions.assertEquals(0, protectedRegion.getMembers().size());

            // Verify flags applied
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.SOLD), any());
            verify(signTextApplicator).updateLoadedSigns(eq(world), eq(REGION_ID),
                    eq(RegionState.SOLD), any());
            verify(realtyApi, never()).rollbackBuy(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a failed purchase leaves the region's owners and signs alone")
        void failedPurchaseTouchesNothingInTheWorld() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false)).thenReturn(RESERVED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            when(economyProvider.getBalance(BUYER)).thenReturn(500.0);

            api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
        }

        @Test
        @DisplayName("once the buyer has paid, nothing more is asked of the database before they are made the owner")
        void nothingBetweenPaymentAndOwnership() {
            // A database call there is a call that can fail, and the buyer has been
            // charged. They would hold the title and be unable to build.
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false)).thenReturn(RESERVED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            when(economyProvider.getBalance(BUYER)).thenReturn(2000.0);
            when(economyProvider.transfer(eq(BUYER), eq(TITLE_HOLDER), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            api.buy(wgRegion, BUYER_CTX, false).join();

            InOrder order = inOrder(realtyApi, economyProvider);
            order.verify(realtyApi).executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false);
            order.verify(realtyApi).getRegionPlaceholders(REGION_ID, WORLD_ID);
            order.verify(economyProvider).transfer(eq(BUYER), eq(TITLE_HOLDER), eq(1000.0), any(), eq(BUYER_ID));
            order.verifyNoMoreInteractions();
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
        }

        @Test
        @DisplayName("returns TransferFailed when atomic buy fails")
        void transferFailedOnAtomicBuyFailure() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.BuyResult.UpdateFailed());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.TransferFailed.class, result);
        }

        @Test
        @DisplayName("a plot with no titleholder pays its account authority")
        void buy_fromAccountAuthority_paysTheAccount() {
            RealtyBackend.BuyResult.Success fromAuthority = new RealtyBackend.BuyResult.Success(
                    1000.0, GOVERNMENT, null, RESERVED.undo());
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false)).thenReturn(fromAuthority);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            when(economyProvider.getBalance(BUYER)).thenReturn(2000.0);
            when(economyProvider.transfer(eq(BUYER), eq(GOVERNMENT), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.Success.class, result);
            verify(economyProvider).transfer(eq(BUYER), eq(GOVERNMENT), eq(1000.0), any(), eq(BUYER_ID));
            verify(realtyApi, never()).rollbackBuy(any(), any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════
    // a region is held until its payment settles
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("a region is held until its payment settles")
    class HeldUntilPaymentSettles {

        private static final String OTHER_REGION_ID = "other_region";
        private static final ActorContext SECOND_BUYER_CTX = ActorContext.player(UUID.randomUUID(), false);

        /** What waits for the main thread. It runs when the test says so. */
        private final java.util.ArrayDeque<Runnable> mainThread = new java.util.ArrayDeque<>();
        private RealtyPaperApiImpl held;

        @BeforeEach
        void holdTheMainThread() {
            ExecutorState controlled = new ExecutorState(mainThread::add,
                    sameThreadExecutorService(), sameThreadExecutorService());
            held = new RealtyPaperApiImpl(server, realtyApi, economyProvider,
                    controlled, database, regionProfileService, signTextApplicator, signCache,
                    () -> 604800, new SafeLocationFinder(), stubPlayerNameService(),
                    accountId -> CompletableFuture.completedFuture(Optional.empty()),
                    new ActorContexts(treasury, null,
                            new AtomicReference<>(new Settings(null, null, null,
                                    new SimpleDateFormat("yyyy"), 0, 0, 0, 0, List.of(), null,
                                    0, 0, 0, 0, AccountManagers.MEMBERS)),
                            realtyApi));
        }

        private void runMainThread() {
            Runnable next;
            while ((next = mainThread.poll()) != null) {
                next.run();
            }
        }

        /** A purchase that is reserved in the database and whose payment has not run yet. */
        private CompletableFuture<RealtyPaperApi.BuyResult> reservedPurchase() {
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false)).thenReturn(RESERVED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            CompletableFuture<RealtyPaperApi.BuyResult> purchase = held.buy(wgRegion, BUYER_CTX, false);
            Assertions.assertFalse(purchase.isDone());
            Assertions.assertEquals(1, mainThread.size());
            return purchase;
        }

        /** A tenancy that is reserved in the database and whose payment has not run yet. */
        private CompletableFuture<RealtyPaperApi.RentResult> reservedTenancy() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID)).thenReturn(LET);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            CompletableFuture<RealtyPaperApi.RentResult> tenancy = held.rent(wgRegion, TENANT_ID);
            Assertions.assertFalse(tenancy.isDone());
            Assertions.assertEquals(1, mainThread.size());
            return tenancy;
        }

        @Test
        @DisplayName("setPrice waits for a purchase of the region and runs once it is rolled back")
        void setPriceWaitsForThePurchase() {
            CompletableFuture<RealtyPaperApi.BuyResult> purchase = reservedPurchase();
            when(economyProvider.getBalance(BUYER)).thenReturn(500.0);
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());

            CompletableFuture<RealtyBackend.SetPriceResult> price =
                    held.setPrice(REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true);

            // The unpaid buyer is the stored titleholder here, so the write must not start.
            verify(realtyApi, never()).setPrice(REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true);
            Assertions.assertFalse(price.isDone());

            runMainThread();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.InsufficientFunds.class, purchase.join());
            InOrder order = inOrder(realtyApi);
            order.verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, BUYER_ID, RESERVED);
            order.verify(realtyApi).setPrice(REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true);
            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.Success.class, price.join());
        }

        @Test
        @DisplayName("a second purchase of the region waits for the first to settle")
        void secondPurchaseWaitsForTheFirst() {
            CompletableFuture<RealtyPaperApi.BuyResult> purchase = reservedPurchase();
            when(economyProvider.getBalance(BUYER)).thenReturn(500.0);
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, SECOND_BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.BuyResult.NotForFreehold());

            CompletableFuture<RealtyPaperApi.BuyResult> second = held.buy(wgRegion, SECOND_BUYER_CTX, false);

            verify(realtyApi, never()).executeBuy(REGION_ID, WORLD_ID, SECOND_BUYER_CTX, false);
            Assertions.assertFalse(second.isDone());

            runMainThread();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.InsufficientFunds.class, purchase.join());
            InOrder order = inOrder(realtyApi);
            order.verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, BUYER_ID, RESERVED);
            order.verify(realtyApi).executeBuy(REGION_ID, WORLD_ID, SECOND_BUYER_CTX, false);
            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.NotForSale.class, second.join());
        }

        @Test
        @DisplayName("setPrice on another region does not wait for the purchase")
        void setPriceElsewhereIsNotHeld() {
            CompletableFuture<RealtyPaperApi.BuyResult> purchase = reservedPurchase();
            when(realtyApi.setPrice(OTHER_REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.NotAuthorized());

            held.setPrice(OTHER_REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true);

            verify(realtyApi).setPrice(OTHER_REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true);
            Assertions.assertFalse(purchase.isDone());
        }

        @Test
        @DisplayName("a purchase whose payment step throws still lets go of the region")
        void paymentThatThrowsLetsGo() {
            CompletableFuture<RealtyPaperApi.BuyResult> purchase = reservedPurchase();
            when(economyProvider.getBalance(BUYER)).thenThrow(new IllegalStateException("economy down"));
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.NotAuthorized());

            CompletableFuture<RealtyBackend.SetPriceResult> price =
                    held.setPrice(REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true);

            verify(realtyApi, never()).setPrice(REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true);

            runMainThread();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.Error.class, purchase.join());
            verify(realtyApi).setPrice(REGION_ID, WORLD_ID, 0.01, BUYER_CTX, true);
            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.NotAuthorized.class, price.join());
        }

        @Test
        @DisplayName("setTitleHolder waits for a purchase of the region")
        void setTitleHolderWaitsForThePurchase() {
            CompletableFuture<RealtyPaperApi.BuyResult> purchase = reservedPurchase();
            when(economyProvider.getBalance(BUYER)).thenReturn(500.0);
            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, TENANT_ID, BUYER_CTX))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.NotAuthorized());

            CompletableFuture<RealtyPaperApi.SetTitleHolderResult> title =
                    held.setTitleHolder(wgRegion, TENANT, BUYER_CTX);

            verify(realtyApi, never()).setTitleHolder(REGION_ID, WORLD_ID, TENANT_ID, BUYER_CTX);

            runMainThread();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.InsufficientFunds.class, purchase.join());
            InOrder order = inOrder(realtyApi);
            order.verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, BUYER_ID, RESERVED);
            order.verify(realtyApi).setTitleHolder(REGION_ID, WORLD_ID, TENANT_ID, BUYER_CTX);
            Assertions.assertInstanceOf(RealtyPaperApi.SetTitleHolderResult.NotAuthorized.class, title.join());
        }

        @Test
        @DisplayName("setTenant waits for a tenancy of the region that is being paid for")
        void setTenantWaitsForTheTenancy() {
            CompletableFuture<RealtyPaperApi.RentResult> tenancy = reservedTenancy();
            when(economyProvider.getBalance(TENANT)).thenReturn(0.0);
            when(realtyApi.setTenant(REGION_ID, WORLD_ID, null, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.SetTenantResult.NotAuthorized());

            CompletableFuture<RealtyPaperApi.SetTenantResult> cleared =
                    held.setTenant(wgRegion, null, BUYER_CTX, false);

            verify(realtyApi, never()).setTenant(REGION_ID, WORLD_ID, null, BUYER_CTX, false);

            runMainThread();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.InsufficientFunds.class, tenancy.join());
            InOrder order = inOrder(realtyApi);
            order.verify(realtyApi).rollbackRent(REGION_ID, WORLD_ID, TENANT_ID, LET);
            order.verify(realtyApi).setTenant(REGION_ID, WORLD_ID, null, BUYER_CTX, false);
            Assertions.assertInstanceOf(RealtyPaperApi.SetTenantResult.NotAuthorized.class, cleared.join());
        }

        @Test
        @DisplayName("setLandlord waits for a tenancy of the region that is being paid for")
        void setLandlordWaitsForTheTenancy() {
            CompletableFuture<RealtyPaperApi.RentResult> tenancy = reservedTenancy();
            when(economyProvider.getBalance(TENANT)).thenReturn(0.0);
            when(realtyApi.setLandlord(REGION_ID, WORLD_ID, BUYER, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetLandlordResult.Occupied());

            CompletableFuture<RealtyPaperApi.SetLandlordResult> landlord =
                    held.setLandlord(wgRegion, BUYER, BUYER_CTX, true);

            verify(realtyApi, never()).setLandlord(REGION_ID, WORLD_ID, BUYER, BUYER_CTX, true);

            runMainThread();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.InsufficientFunds.class, tenancy.join());
            InOrder order = inOrder(realtyApi);
            order.verify(realtyApi).rollbackRent(REGION_ID, WORLD_ID, TENANT_ID, LET);
            order.verify(realtyApi).setLandlord(REGION_ID, WORLD_ID, BUYER, BUYER_CTX, true);
            Assertions.assertInstanceOf(RealtyPaperApi.SetLandlordResult.Occupied.class, landlord.join());
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
                    .thenReturn(LET);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT)).thenReturn(100.0);

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.InsufficientFunds.class, result);
            verify(realtyApi).rollbackRent(REGION_ID, WORLD_ID, TENANT_ID, LET);
            Assertions.assertFalse(protectedRegion.getOwners().contains(TENANT_ID));
        }

        @Test
        @DisplayName("returns PaymentFailed and rolls back DB when the transfer fails")
        void paymentFailed() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID)).thenReturn(LET);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT)).thenReturn(1000.0);
            when(economyProvider.transfer(eq(TENANT), eq(LANDLORD), eq(500.0), any(), eq(TENANT_ID)))
                    .thenReturn(new PaymentResult.Failure("Bank error"));

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.PaymentFailed.class, result);
            verify(realtyApi).rollbackRent(REGION_ID, WORLD_ID, TENANT_ID, LET);
            Assertions.assertFalse(protectedRegion.getOwners().contains(TENANT_ID));
        }

        @Test
        @DisplayName("success sets tenant as owner and applies LEASED flags")
        void success() {
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(LET);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT)).thenReturn(1000.0);
            when(economyProvider.transfer(eq(TENANT), eq(LANDLORD), eq(500.0), any(), eq(TENANT_ID)))
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
                    .thenReturn(new RealtyBackend.RentResult.Success(0.0, 3600, Party.personal(LANDLORD_ID), 7));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.Success.class, result);
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
        }

        @Test
        @DisplayName("rent is paid into the account a landlord party names")
        void rent_toAccountLandlord_paysTheAccount() {
            RealtyBackend.RentResult.Success let = new RealtyBackend.RentResult.Success(500.0, 3600, GOVERNMENT, 7);
            when(realtyApi.rentRegion(REGION_ID, WORLD_ID, TENANT_ID)).thenReturn(let);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT)).thenReturn(1000.0);
            when(economyProvider.transfer(eq(TENANT), eq(GOVERNMENT), eq(500.0), any(), eq(TENANT_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.RentResult result = api.rent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.RentResult.Success.class, result);
            verify(economyProvider).transfer(eq(TENANT), eq(GOVERNMENT), eq(500.0), any(), eq(TENANT_ID));
            verify(realtyApi, never()).rollbackRent(any(), any(), any(), any());
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
        @DisplayName("returns Terminating and leaves the region alone while an eviction is pending")
        void terminating() {
            when(realtyApi.unrentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(new RealtyBackend.UnrentResult.Terminating());

            protectedRegion.getOwners().addPlayer(TENANT_ID);

            RealtyPaperApi.UnrentResult result = api.unrent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.UnrentResult.Terminating.class, result);
            Assertions.assertEquals(1, protectedRegion.getOwners().size());
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
        }

        @Test
        @DisplayName("success clears owners and applies FOR_LEASE flags")
        void success() {
            when(realtyApi.unrentRegion(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(ENDED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.transfer(eq(LANDLORD), eq(TENANT), eq(100.0), any(), eq(TENANT_ID)))
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
                    .thenReturn(ENDED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.transfer(eq(LANDLORD), eq(TENANT), eq(100.0), any(), eq(TENANT_ID)))
                    .thenReturn(new PaymentResult.Failure("Insufficient funds"));
            protectedRegion.getOwners().addPlayer(TENANT_ID);

            RealtyPaperApi.UnrentResult result = api.unrent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.UnrentResult.RefundFailed.class, result);
            // Put back as it was, with the dates it had. Letting the region to the tenant
            // again started their tenancy from now and recorded a second letting.
            verify(realtyApi).rollbackUnrent(REGION_ID, WORLD_ID, TENANT_ID, ENDED);
            verify(realtyApi, never()).rentRegion(any(), any(), any());
            Assertions.assertTrue(protectedRegion.getOwners().contains(TENANT_ID),
                    "the tenancy goes on, so the tenant keeps the region");
        }

        @Test
        @DisplayName("a refund out of an account landlord names the tenant who ended the lease")
        void unrent_refundFromAccountLandlord_namesTheTenantAsInitiator() {
            RealtyBackend.UnrentResult.Success ended = new RealtyBackend.UnrentResult.Success(
                    100.0, TENANT_ID, GOVERNMENT, ENDED.previous(), ENDED.historyId());
            when(realtyApi.unrentRegion(REGION_ID, WORLD_ID, TENANT_ID)).thenReturn(ended);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            when(economyProvider.transfer(eq(GOVERNMENT), eq(TENANT), eq(100.0), any(), eq(TENANT_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.UnrentResult result = api.unrent(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.UnrentResult.Success.class, result);
            verify(economyProvider).transfer(eq(GOVERNMENT), eq(TENANT), eq(100.0), any(), eq(TENANT_ID));
            verify(realtyApi, never()).rollbackUnrent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a refund the landlord's account cannot make puts the tenancy back")
        void unrent_refundFails_rollsBack() {
            RealtyBackend.UnrentResult.Success ended = new RealtyBackend.UnrentResult.Success(
                    100.0, TENANT_ID, GOVERNMENT, ENDED.previous(), ENDED.historyId());
            when(realtyApi.unrentRegion(REGION_ID, WORLD_ID, TENANT_ID)).thenReturn(ended);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            when(economyProvider.transfer(eq(GOVERNMENT), eq(TENANT), eq(100.0), any(), eq(TENANT_ID)))
                    .thenReturn(new PaymentResult.Failure("Account #42 is archived"));
            protectedRegion.getOwners().addPlayer(TENANT_ID);

            RealtyPaperApi.UnrentResult result = api.unrent(wgRegion, TENANT_ID).join();

            RealtyPaperApi.UnrentResult.RefundFailed failed =
                    Assertions.assertInstanceOf(RealtyPaperApi.UnrentResult.RefundFailed.class, result);
            Assertions.assertEquals("Account #42 is archived", failed.error());
            verify(realtyApi).rollbackUnrent(REGION_ID, WORLD_ID, TENANT_ID, ended);
            Assertions.assertTrue(protectedRegion.getOwners().contains(TENANT_ID));
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
                    .thenReturn(RENEWED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT)).thenReturn(50.0);

            RealtyPaperApi.ExtendResult result = api.extend(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.ExtendResult.InsufficientFunds.class, result);
            verify(realtyApi).rollbackRenewLeasehold(REGION_ID, WORLD_ID, TENANT_ID, RENEWED);
        }

        @Test
        @DisplayName("returns PaymentFailed and rolls back DB when the transfer fails")
        void paymentFailed() {
            when(realtyApi.renewLeasehold(REGION_ID, WORLD_ID, TENANT_ID)).thenReturn(RENEWED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT)).thenReturn(500.0);
            when(economyProvider.transfer(eq(TENANT), eq(LANDLORD), eq(200.0), any(), eq(TENANT_ID)))
                    .thenReturn(new PaymentResult.Failure("Bank error"));

            RealtyPaperApi.ExtendResult result = api.extend(wgRegion, TENANT_ID).join();

            Assertions.assertInstanceOf(RealtyPaperApi.ExtendResult.PaymentFailed.class, result);
            verify(realtyApi).rollbackRenewLeasehold(REGION_ID, WORLD_ID, TENANT_ID, RENEWED);
        }

        @Test
        @DisplayName("success extends lease and updates signs")
        void success() {
            when(realtyApi.renewLeasehold(REGION_ID, WORLD_ID, TENANT_ID))
                    .thenReturn(RENEWED);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());
            when(economyProvider.getBalance(TENANT)).thenReturn(500.0);
            when(economyProvider.transfer(eq(TENANT), eq(LANDLORD), eq(200.0), any(), eq(TENANT_ID)))
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
    // terminate()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("terminate")
    class Terminate {

        private LeaseholdContractEntity lease(LocalDateTime endDate, LocalDateTime terminationDate) {
            return new LeaseholdContractEntity(1, Party.personal(LANDLORD_ID), TENANT_ID, 200.0, 604800L,
                    LocalDateTime.now().minusSeconds(1), endDate, null, null, terminationDate, null, true);
        }

        @Test
        @DisplayName("returns NotAuthorized when the actor is neither landlord nor tenant")
        void notAuthorized() {
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(lease(LocalDateTime.now().plusDays(30), null));

            RealtyPaperApi.TerminateResult result =
                    api.terminate(wgRegion, ActorContext.player(UUID.randomUUID(), false), false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.TerminateResult.NotAuthorized.class, result);
            verify(realtyApi, never()).terminateLease(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("returns AlreadyTerminating when a termination is already scheduled")
        void alreadyTerminating() {
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(lease(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(7)));

            RealtyPaperApi.TerminateResult result =
                    api.terminate(wgRegion, ActorContext.player(LANDLORD_ID, false), false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.TerminateResult.AlreadyTerminating.class, result);
        }

        @Test
        @DisplayName("landlord termination with the notice already paid charges nothing")
        void landlordNoCharge() {
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(lease(LocalDateTime.now().plusDays(30), null));
            when(realtyApi.terminateLease(eq(REGION_ID), eq(WORLD_ID), any(), any(), eq("landlord")))
                    .thenReturn(new RealtyBackend.TerminateLeaseholdResult.Success(TENANT_ID, Party.personal(LANDLORD_ID)));

            RealtyPaperApi.TerminateResult result =
                    api.terminate(wgRegion, ActorContext.player(LANDLORD_ID, false), false).join();

            RealtyPaperApi.TerminateResult.Success success =
                    Assertions.assertInstanceOf(RealtyPaperApi.TerminateResult.Success.class, result);
            Assertions.assertEquals(0.0, success.charged());
            Assertions.assertEquals("landlord", success.terminatedByRole());
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
        }

        @Test
        @DisplayName("a manager of an account landlord terminates as the landlord; a stranger may not")
        void managerOfAccountLandlordTerminatesAsLandlord() {
            Party gov = Party.account(42, AccountKind.GOVERNMENT);
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(new LeaseholdContractEntity(1, gov, TENANT_ID, 200.0, 604800L,
                            LocalDateTime.now().minusSeconds(1), LocalDateTime.now().plusDays(30),
                            null, null, null, null, true));
            when(realtyApi.terminateLease(eq(REGION_ID), eq(WORLD_ID), any(), any(), eq("landlord")))
                    .thenReturn(new RealtyBackend.TerminateLeaseholdResult.Success(TENANT_ID, gov));

            Assertions.assertInstanceOf(RealtyPaperApi.TerminateResult.NotAuthorized.class,
                    api.terminate(wgRegion, ActorContext.player(LANDLORD_ID, false), false).join());
            ActorContext manager = new ActorContext(LANDLORD_ID, Set.of(gov), Set.of(), false);
            RealtyPaperApi.TerminateResult.Success success = Assertions.assertInstanceOf(
                    RealtyPaperApi.TerminateResult.Success.class,
                    api.terminate(wgRegion, manager, false).join());
            Assertions.assertEquals("landlord", success.terminatedByRole());
        }

        @Test
        @DisplayName("a tenant who also manages the landlord terminates as the tenant, and pays the notice")
        void tenantWhoManagesTheLandlord_terminatesAsTheTenant() {
            Party gov = Party.account(42, AccountKind.GOVERNMENT);
            // endDate ~now, notice 7 days, duration 7 days: a tenant owes one extension.
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(new LeaseholdContractEntity(1, gov, TENANT_ID, 200.0, 604800L,
                            LocalDateTime.now().minusSeconds(1), LocalDateTime.now(),
                            null, null, null, null, true));
            when(economyProvider.getBalance(TENANT)).thenReturn(1000.0);
            when(economyProvider.transfer(eq(TENANT), eq(gov), eq(200.0), any(), eq(TENANT_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(realtyApi.terminateLease(eq(REGION_ID), eq(WORLD_ID), any(), any(), eq("tenant")))
                    .thenReturn(new RealtyBackend.TerminateLeaseholdResult.Success(TENANT_ID, gov));

            ActorContext tenantAndManager = new ActorContext(TENANT_ID, Set.of(gov), Set.of(), false);
            RealtyPaperApi.TerminateResult.Success success = Assertions.assertInstanceOf(
                    RealtyPaperApi.TerminateResult.Success.class,
                    api.terminate(wgRegion, tenantAndManager, false).join());

            Assertions.assertEquals("tenant", success.terminatedByRole());
            Assertions.assertEquals(200.0, success.charged());
            verify(realtyApi, never()).terminateLease(any(), any(), any(), any(), eq("landlord"));
        }

        @Test
        @DisplayName("tenant termination past the paid term charges one extension and refunds at the end")
        void tenantChargesExtension() {
            // endDate ~now, notice 7 days, duration 7 days → exactly one extension owed.
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(lease(LocalDateTime.now(), null));
            when(economyProvider.getBalance(TENANT)).thenReturn(1000.0);
            when(economyProvider.transfer(eq(TENANT), eq(LANDLORD), eq(200.0), any(), eq(TENANT_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(realtyApi.terminateLease(eq(REGION_ID), eq(WORLD_ID), any(), any(), eq("tenant")))
                    .thenReturn(new RealtyBackend.TerminateLeaseholdResult.Success(TENANT_ID, Party.personal(LANDLORD_ID)));

            RealtyPaperApi.TerminateResult result =
                    api.terminate(wgRegion, ActorContext.player(TENANT_ID, false), false).join();

            RealtyPaperApi.TerminateResult.Success success =
                    Assertions.assertInstanceOf(RealtyPaperApi.TerminateResult.Success.class, result);
            Assertions.assertEquals(200.0, success.charged());
            verify(economyProvider).transfer(eq(TENANT), eq(LANDLORD), eq(200.0), any(), eq(TENANT_ID));
        }

        @Test
        @DisplayName("immediate (--now) termination skips notice and charges nothing")
        void immediateSkipsNotice() {
            // endDate ~now: a normal tenant termination would owe an extension; --now must not charge.
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(lease(LocalDateTime.now(), null));
            when(realtyApi.terminateLease(eq(REGION_ID), eq(WORLD_ID), any(), any(), eq("tenant")))
                    .thenReturn(new RealtyBackend.TerminateLeaseholdResult.Success(TENANT_ID, Party.personal(LANDLORD_ID)));

            RealtyPaperApi.TerminateResult result =
                    api.terminate(wgRegion, ActorContext.player(TENANT_ID, false), true).join();

            RealtyPaperApi.TerminateResult.Success success =
                    Assertions.assertInstanceOf(RealtyPaperApi.TerminateResult.Success.class, result);
            Assertions.assertEquals(0.0, success.charged());
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
        }

        @Test
        @DisplayName("tenant termination is refused when funds cannot cover the notice")
        void tenantInsufficientFunds() {
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(lease(LocalDateTime.now(), null));
            when(economyProvider.getBalance(TENANT)).thenReturn(50.0);

            RealtyPaperApi.TerminateResult result =
                    api.terminate(wgRegion, ActorContext.player(TENANT_ID, false), false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.TerminateResult.InsufficientFunds.class, result);
            verify(realtyApi, never()).terminateLease(any(), any(), any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════
    // setTitleHolder()
    // ═══════════════════════════════════════════════════

    private static void assertOnlyPlayersRefusal(CompletableFuture<?> future) {
        java.util.concurrent.CompletionException thrown =
                Assertions.assertThrows(java.util.concurrent.CompletionException.class, future::join);
        IllegalArgumentException cause = Assertions.assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        Assertions.assertEquals("only a player can be tenant or titleholder in this version", cause.getMessage());
    }

    @Nested
    @DisplayName("setTitleHolder")
    class SetTitleHolder {

        @Test
        @DisplayName("a title holder that is not a player fails the future and changes nothing")
        void accountTitleHolder_isRefused() {
            assertOnlyPlayersRefusal(api.setTitleHolder(wgRegion, GOVERNMENT));
            assertOnlyPlayersRefusal(api.transferTitleHolder(wgRegion, GOVERNMENT));
            assertOnlyPlayersRefusal(api.createFreehold(wgRegion, 1000.0, GOVERNMENT, GOVERNMENT));
            assertOnlyPlayersRefusal(api.registerFreehold(wgRegion, 1000.0, GOVERNMENT, GOVERNMENT));
            verify(realtyApi, never()).setTitleHolder(any(), any(), any(), any());
            verify(realtyApi, never()).transferTitleHolder(any(), any(), any());
            verify(realtyApi, never()).createFreehold(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("the previous title holder is given as a player party")
        void previousTitleHolder_isAPlayerParty() {
            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, BUYER_ID, ActorContext.console()))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.Success(TITLE_HOLDER_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());

            RealtyPaperApi.SetTitleHolderResult.Success success = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.Success.class,
                    api.setTitleHolder(wgRegion, BUYER).join());

            Assertions.assertEquals(TITLE_HOLDER, success.previousTitleHolder());
        }

        @Test
        @DisplayName("returns NoFreeholdContract when no contract exists")
        void noFreeholdContract() {
            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, BUYER_ID, ActorContext.console()))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.NoFreeholdContract());

            RealtyPaperApi.SetTitleHolderResult result =
                    api.setTitleHolder(wgRegion, BUYER).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.NoFreeholdContract.class, result);
        }

        @Test
        @DisplayName("success with holder sets owner and applies SOLD")
        void successWithHolder() {
            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, BUYER_ID, ActorContext.console()))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.Success(TITLE_HOLDER_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.SetTitleHolderResult result =
                    api.setTitleHolder(wgRegion, BUYER).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.Success.class, result);
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.SOLD), any());
        }

        @Test
        @DisplayName("success with null clears owner and applies FOR_SALE")
        void successWithNull() {
            protectedRegion.getOwners().addPlayer(TITLE_HOLDER_ID);

            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, null, ActorContext.console()))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.Success(TITLE_HOLDER_ID));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.SetTitleHolderResult result =
                    api.setTitleHolder(wgRegion, (Party) null).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.Success.class, result);
            Assertions.assertEquals(0, protectedRegion.getOwners().size());
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_SALE), any());
        }

        @Test
        @DisplayName("NotAuthorized leaves owners, flags and signs untouched")
        void notAuthorized() {
            protectedRegion.getOwners().addPlayer(TITLE_HOLDER_ID);
            ActorContext stranger = ActorContext.player(LANDLORD_ID, false);

            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, BUYER_ID, stranger))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.NotAuthorized());

            RealtyPaperApi.SetTitleHolderResult result =
                    api.setTitleHolder(wgRegion, BUYER, stranger).join();

            RealtyPaperApi.SetTitleHolderResult.NotAuthorized refused = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.NotAuthorized.class, result);
            Assertions.assertEquals(REGION_ID, refused.regionId());
            Assertions.assertTrue(protectedRegion.getOwners().contains(TITLE_HOLDER_ID));
            Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════
    // setPrice() / unsetPrice() / setDuration() / setMaxRenewals()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("term setters")
    class TermSetters {

        private static final Map<String, String> NEW_TERMS = Map.of("price", "5000");

        private void regionIs(RegionState state) {
            when(server.getWorld(WORLD_ID)).thenReturn(world);
            when(realtyApi.getRegionWithState(REGION_ID, WORLD_ID))
                    .thenReturn(new RealtyBackend.RegionWithState(
                            new RealtyRegionEntity(1, REGION_ID, WORLD_ID), state, NEW_TERMS));
        }

        private void assertNothingTouched() {
            verify(realtyApi, never()).getRegionWithState(any(), any());
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
        }

        @Test
        @DisplayName("setPrice success updates loaded signs and leaves the flags alone")
        void setPriceSuccess() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());
            regionIs(RegionState.FOR_SALE);

            RealtyBackend.SetPriceResult result =
                    api.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true).join();

            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.Success.class, result);
            verify(signTextApplicator).updateLoadedSigns(world, REGION_ID, RegionState.FOR_SALE, NEW_TERMS);
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
        }

        @Test
        @DisplayName("the old setPrice signature updates loaded signs too")
        void setPriceOldSignature() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, ActorContext.console(), false))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());
            regionIs(RegionState.FOR_SALE);

            api.setPrice(REGION_ID, WORLD_ID, 5000.0).join();

            verify(signTextApplicator).updateLoadedSigns(world, REGION_ID, RegionState.FOR_SALE, NEW_TERMS);
        }

        @Test
        @DisplayName("setPrice on a rented lease redraws the sign as leased, not for lease")
        void setPriceRentedLease() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());
            regionIs(RegionState.LEASED);

            api.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, false).join();

            verify(signTextApplicator).updateLoadedSigns(world, REGION_ID, RegionState.LEASED, NEW_TERMS);
            verify(signTextApplicator, never())
                    .updateLoadedSigns(any(), any(), eq(RegionState.FOR_LEASE), any());
        }

        @Test
        @DisplayName("setPrice NotAuthorized touches nothing")
        void setPriceNotAuthorized() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.NotAuthorized());

            RealtyBackend.SetPriceResult result =
                    api.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true).join();

            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.NotAuthorized.class, result);
            assertNothingTouched();
        }

        @Test
        @DisplayName("setPrice Occupied touches nothing")
        void setPriceOccupied() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.Occupied());

            RealtyBackend.SetPriceResult result =
                    api.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true).join();

            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.Occupied.class, result);
            assertNothingTouched();
        }

        @Test
        @DisplayName("an unreadable state skips the redraw and still returns the result")
        void unreadableState() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());
            when(realtyApi.getRegionWithState(REGION_ID, WORLD_ID)).thenReturn(null);

            RealtyBackend.SetPriceResult result =
                    api.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true).join();

            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.Success.class, result);
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a world that is not loaded skips the redraw and still returns the result")
        void unloadedWorld() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());
            when(server.getWorld(WORLD_ID)).thenReturn(null);
            when(realtyApi.getRegionWithState(REGION_ID, WORLD_ID))
                    .thenReturn(new RealtyBackend.RegionWithState(
                            new RealtyRegionEntity(1, REGION_ID, WORLD_ID), RegionState.SOLD, NEW_TERMS));

            RealtyBackend.SetPriceResult result =
                    api.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true).join();

            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.Success.class, result);
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a failed state read after the write still returns the result")
        void stateReadFails() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());
            when(realtyApi.getRegionWithState(REGION_ID, WORLD_ID))
                    .thenThrow(new IllegalStateException("database gone"));

            RealtyBackend.SetPriceResult result =
                    api.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true).join();

            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.Success.class, result);
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a failed sign update after the write still returns the result")
        void signUpdateFails() {
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());
            regionIs(RegionState.FOR_SALE);
            org.mockito.Mockito.doThrow(new IllegalStateException("sign gone"))
                    .when(signTextApplicator).updateLoadedSigns(any(), any(), any(), any());

            RealtyBackend.SetPriceResult result =
                    api.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true).join();

            Assertions.assertInstanceOf(RealtyBackend.SetPriceResult.Success.class, result);
        }

        @Test
        @DisplayName("the sign is redrawn and the future completes only when the main thread runs")
        void completesOnTheMainThread() {
            java.util.ArrayDeque<Runnable> mainThread = new java.util.ArrayDeque<>();
            ExecutorState controlled = new ExecutorState(mainThread::add,
                    sameThreadExecutorService(), sameThreadExecutorService());
            RealtyPaperApiImpl onControlled = new RealtyPaperApiImpl(server, realtyApi, economyProvider,
                    controlled, database, regionProfileService, signTextApplicator, signCache,
                    () -> 604800, new SafeLocationFinder(), stubPlayerNameService(),
                    accountId -> CompletableFuture.completedFuture(Optional.empty()),
                    new ActorContexts(treasury, null,
                            new AtomicReference<>(new Settings(null, null, null,
                                    new SimpleDateFormat("yyyy"), 0, 0, 0, 0, List.of(), null,
                                    0, 0, 0, 0, AccountManagers.MEMBERS)),
                            realtyApi));
            when(realtyApi.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetPriceResult.Success());
            regionIs(RegionState.FOR_SALE);

            CompletableFuture<RealtyBackend.SetPriceResult> future =
                    onControlled.setPrice(REGION_ID, WORLD_ID, 5000.0, BUYER_CTX, true);

            Assertions.assertFalse(future.isDone());
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
            Assertions.assertEquals(1, mainThread.size());

            mainThread.poll().run();

            Assertions.assertTrue(future.isDone());
            verify(signTextApplicator).updateLoadedSigns(world, REGION_ID, RegionState.FOR_SALE, NEW_TERMS);
        }

        @Test
        @DisplayName("unsetPrice success updates loaded signs")
        void unsetPriceSuccess() {
            when(realtyApi.unsetPrice(REGION_ID, WORLD_ID, BUYER_CTX))
                    .thenReturn(new RealtyBackend.UnsetPriceResult.Success());
            regionIs(RegionState.SOLD);

            RealtyBackend.UnsetPriceResult result =
                    api.unsetPrice(REGION_ID, WORLD_ID, BUYER_CTX).join();

            Assertions.assertInstanceOf(RealtyBackend.UnsetPriceResult.Success.class, result);
            verify(signTextApplicator).updateLoadedSigns(world, REGION_ID, RegionState.SOLD, NEW_TERMS);
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
        }

        @Test
        @DisplayName("unsetPrice NotAuthorized touches nothing")
        void unsetPriceNotAuthorized() {
            when(realtyApi.unsetPrice(REGION_ID, WORLD_ID, BUYER_CTX))
                    .thenReturn(new RealtyBackend.UnsetPriceResult.NotAuthorized());

            api.unsetPrice(REGION_ID, WORLD_ID, BUYER_CTX).join();

            assertNothingTouched();
        }

        @Test
        @DisplayName("setDuration success updates loaded signs")
        void setDurationSuccess() {
            when(realtyApi.setDuration(REGION_ID, WORLD_ID, 7200, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetDurationResult.Success());
            regionIs(RegionState.FOR_LEASE);

            RealtyBackend.SetDurationResult result =
                    api.setDuration(REGION_ID, WORLD_ID, 7200, BUYER_CTX, true).join();

            Assertions.assertInstanceOf(RealtyBackend.SetDurationResult.Success.class, result);
            verify(signTextApplicator).updateLoadedSigns(world, REGION_ID, RegionState.FOR_LEASE, NEW_TERMS);
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
        }

        @Test
        @DisplayName("setDuration Occupied touches nothing")
        void setDurationOccupied() {
            when(realtyApi.setDuration(REGION_ID, WORLD_ID, 7200, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.SetDurationResult.Occupied());

            api.setDuration(REGION_ID, WORLD_ID, 7200, BUYER_CTX, true).join();

            assertNothingTouched();
        }

        @Test
        @DisplayName("setMaxRenewals success updates loaded signs")
        void setMaxRenewalsSuccess() {
            when(realtyApi.setMaxRenewals(REGION_ID, WORLD_ID, 4, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.SetMaxRenewalsResult.Success());
            regionIs(RegionState.LEASED);

            RealtyBackend.SetMaxRenewalsResult result =
                    api.setMaxRenewals(REGION_ID, WORLD_ID, 4, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyBackend.SetMaxRenewalsResult.Success.class, result);
            verify(signTextApplicator).updateLoadedSigns(world, REGION_ID, RegionState.LEASED, NEW_TERMS);
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
        }

        @Test
        @DisplayName("setMaxRenewals BelowCurrentExtensions touches nothing")
        void setMaxRenewalsBelowCurrent() {
            when(realtyApi.setMaxRenewals(REGION_ID, WORLD_ID, 1, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.SetMaxRenewalsResult.BelowCurrentExtensions(3));

            api.setMaxRenewals(REGION_ID, WORLD_ID, 1, BUYER_CTX, false).join();

            assertNothingTouched();
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
            when(realtyApi.setTenant(REGION_ID, WORLD_ID, TENANT_ID, ActorContext.console(), false))
                    .thenReturn(new RealtyBackend.SetTenantResult.Success(null, Party.personal(LANDLORD_ID)));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.SetTenantResult result =
                    api.setTenant(wgRegion, TENANT).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.Success.class, result);
            Assertions.assertTrue(protectedRegion.getOwners().contains(TENANT_ID));
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.LEASED), any());
        }

        @Test
        @DisplayName("success with null clears owner and applies FOR_LEASE")
        void successWithNull() {
            protectedRegion.getOwners().addPlayer(TENANT_ID);

            when(realtyApi.setTenant(REGION_ID, WORLD_ID, null, ActorContext.console(), false))
                    .thenReturn(new RealtyBackend.SetTenantResult.Success(TENANT_ID, Party.personal(LANDLORD_ID)));
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.SetTenantResult result =
                    api.setTenant(wgRegion, (Party) null).join();

            RealtyPaperApi.SetTenantResult.Success success = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.Success.class, result);
            Assertions.assertEquals(TENANT, success.previousTenant());
            Assertions.assertEquals(0, protectedRegion.getOwners().size());
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_LEASE), any());
        }

        @Test
        @DisplayName("a tenant that is not a player fails the future and changes nothing")
        void accountTenant_isRefused() {
            CompletableFuture<RealtyPaperApi.SetTenantResult> future = api.setTenant(wgRegion, GOVERNMENT);

            assertOnlyPlayersRefusal(future);
            verify(realtyApi, never()).setTenant(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("returns NoLeaseholdContract when no contract exists")
        void noLeaseholdContract() {
            when(realtyApi.setTenant(REGION_ID, WORLD_ID, TENANT_ID, ActorContext.console(), false))
                    .thenReturn(new RealtyBackend.SetTenantResult.NoLeaseholdContract());

            RealtyPaperApi.SetTenantResult result =
                    api.setTenant(wgRegion, TENANT).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.NoLeaseholdContract.class, result);
        }

        @Test
        @DisplayName("NotAuthorized leaves owners, flags and signs untouched")
        void notAuthorized() {
            protectedRegion.getOwners().addPlayer(TENANT_ID);
            ActorContext stranger = ActorContext.player(BUYER_ID, false);

            when(realtyApi.setTenant(REGION_ID, WORLD_ID, null, stranger, false))
                    .thenReturn(new RealtyBackend.SetTenantResult.NotAuthorized());

            RealtyPaperApi.SetTenantResult result =
                    api.setTenant(wgRegion, (Party) null, stranger, false).join();

            RealtyPaperApi.SetTenantResult.NotAuthorized refused = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.NotAuthorized.class, result);
            Assertions.assertEquals(REGION_ID, refused.regionId());
            Assertions.assertTrue(protectedRegion.getOwners().contains(TENANT_ID));
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Occupied leaves owners, flags and signs untouched")
        void occupied() {
            protectedRegion.getOwners().addPlayer(TENANT_ID);
            ActorContext landlord = ActorContext.player(LANDLORD_ID, false);

            when(realtyApi.setTenant(REGION_ID, WORLD_ID, BUYER_ID, landlord, true))
                    .thenReturn(new RealtyBackend.SetTenantResult.Occupied());

            RealtyPaperApi.SetTenantResult result =
                    api.setTenant(wgRegion, BUYER, landlord, true).join();

            RealtyPaperApi.SetTenantResult.Occupied occupied = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.Occupied.class, result);
            Assertions.assertEquals(REGION_ID, occupied.regionId());
            Assertions.assertTrue(protectedRegion.getOwners().contains(TENANT_ID));
            Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
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

            when(realtyApi.setLandlord(REGION_ID, WORLD_ID, Party.personal(LANDLORD_ID), ActorContext.console(), false))
                    .thenReturn(new RealtyBackend.SetLandlordResult.Success(Party.personal(UUID.randomUUID())));

            RealtyPaperApi.SetLandlordResult result =
                    api.setLandlord(wgRegion, Party.personal(LANDLORD_ID)).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetLandlordResult.Success.class, result);
            Assertions.assertEquals(0, protectedRegion.getMembers().size());
        }

        @Test
        @DisplayName("returns NoLeaseholdContract when no contract exists")
        void noLeaseholdContract() {
            when(realtyApi.setLandlord(REGION_ID, WORLD_ID, Party.personal(LANDLORD_ID), ActorContext.console(), false))
                    .thenReturn(new RealtyBackend.SetLandlordResult.NoLeaseholdContract());

            RealtyPaperApi.SetLandlordResult result =
                    api.setLandlord(wgRegion, Party.personal(LANDLORD_ID)).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.SetLandlordResult.NoLeaseholdContract.class, result);
        }

        @Test
        @DisplayName("Occupied keeps the members")
        void occupied() {
            UUID member = UUID.randomUUID();
            protectedRegion.getMembers().addPlayer(member);
            ActorContext landlord = ActorContext.player(LANDLORD_ID, false);

            when(realtyApi.setLandlord(REGION_ID, WORLD_ID, Party.personal(BUYER_ID), landlord, true))
                    .thenReturn(new RealtyBackend.SetLandlordResult.Occupied());

            RealtyPaperApi.SetLandlordResult result =
                    api.setLandlord(wgRegion, Party.personal(BUYER_ID), landlord, true).join();

            RealtyPaperApi.SetLandlordResult.Occupied occupied = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetLandlordResult.Occupied.class, result);
            Assertions.assertEquals(REGION_ID, occupied.regionId());
            Assertions.assertTrue(protectedRegion.getMembers().contains(member));
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
            when(realtyApi.createFreehold(REGION_ID, WORLD_ID, 1000.0, Party.personal(AUTHORITY_ID), null))
                    .thenReturn(true);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.CreateFreeholdResult result =
                    api.createFreehold(wgRegion, 1000.0, Party.personal(AUTHORITY_ID), (Party) null).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateFreeholdResult.Success.class, result);
            Assertions.assertTrue(protectedRegion.getMembers().contains(AUTHORITY_ID));
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_SALE), any());
        }

        @Test
        @DisplayName("success with title holder applies SOLD state")
        void successWithTitleHolder() {
            when(realtyApi.createFreehold(REGION_ID, WORLD_ID, 1000.0, Party.personal(AUTHORITY_ID), TITLE_HOLDER_ID))
                    .thenReturn(true);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.CreateFreeholdResult result =
                    api.createFreehold(wgRegion, 1000.0, Party.personal(AUTHORITY_ID), TITLE_HOLDER).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateFreeholdResult.Success.class, result);
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.SOLD), any());
        }

        @Test
        @DisplayName("returns AlreadyRegistered when region exists")
        void alreadyRegistered() {
            when(realtyApi.createFreehold(REGION_ID, WORLD_ID, 1000.0, Party.personal(AUTHORITY_ID), null))
                    .thenReturn(false);

            RealtyPaperApi.CreateFreeholdResult result =
                    api.createFreehold(wgRegion, 1000.0, Party.personal(AUTHORITY_ID), (Party) null).join();

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
            when(realtyApi.createLeasehold(REGION_ID, WORLD_ID, 500.0, 3600, 3, Party.personal(LANDLORD_ID)))
                    .thenReturn(true);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID))
                    .thenReturn(Map.of());

            RealtyPaperApi.CreateLeaseholdResult result =
                    api.createLeasehold(wgRegion, 500.0, 3600, 3, Party.personal(LANDLORD_ID)).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateLeaseholdResult.Success.class, result);
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.FOR_LEASE), any());
        }

        @Test
        @DisplayName("returns AlreadyRegistered when region exists")
        void alreadyRegistered() {
            when(realtyApi.createLeasehold(REGION_ID, WORLD_ID, 500.0, 3600, 3, Party.personal(LANDLORD_ID)))
                    .thenReturn(false);

            RealtyPaperApi.CreateLeaseholdResult result =
                    api.createLeasehold(wgRegion, 500.0, 3600, 3, Party.personal(LANDLORD_ID)).join();

            Assertions.assertInstanceOf(
                    RealtyPaperApi.CreateLeaseholdResult.AlreadyRegistered.class, result);
        }
    }
}
