package io.github.md5sha256.realty.api;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.command.util.SafeLocationFinder;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.api.event.AuctionBidEvent;
import io.github.md5sha256.realty.api.event.AuctionBidPlacedEvent;
import io.github.md5sha256.realty.api.event.AuctionCancelledEvent;
import io.github.md5sha256.realty.api.event.AuctionCreateEvent;
import io.github.md5sha256.realty.api.event.AuctionCreatedEvent;
import io.github.md5sha256.realty.api.event.AuctionWonPurchaseEvent;
import io.github.md5sha256.realty.api.event.FeeApprovedEvent;
import io.github.md5sha256.realty.api.event.FeeChangeEvent;
import io.github.md5sha256.realty.api.event.FeeChangedEvent;
import io.github.md5sha256.realty.api.event.FeeRejectedEvent;
import io.github.md5sha256.realty.api.event.FeeRemovedEvent;
import io.github.md5sha256.realty.api.event.LevyChangeEvent;
import io.github.md5sha256.realty.api.event.LevyChangedEvent;
import io.github.md5sha256.realty.api.event.OfferPurchaseCompletedEvent;
import io.github.md5sha256.realty.api.event.OfferAcceptEvent;
import io.github.md5sha256.realty.api.event.OfferPlaceEvent;
import io.github.md5sha256.realty.api.event.OfferPlacedEvent;
import io.github.md5sha256.realty.api.event.OfferRejectedEvent;
import io.github.md5sha256.realty.api.event.OffersToggledEvent;
import io.github.md5sha256.realty.api.event.RealtyRegionEvent;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.economy.EconomyProvider;
import io.github.md5sha256.realty.economy.HoldingAccount;
import io.github.md5sha256.realty.economy.SaleSettlement;
import io.github.md5sha256.realty.settings.DefaultParties;
import io.github.md5sha256.realty.economy.PaymentResult;
import io.github.md5sha256.realty.settings.AccountManagers;
import io.github.md5sha256.realty.settings.Settings;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.AccountMember;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;
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
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
    @Mock
    private RealtyEventDispatch events;

    private SignCache signCache;
    private HoldingAccount holding;
    private SaleSettlement settlement;
    private RealtyPaperApiImpl api;
    private MockedStatic<WorldGuard> worldGuardMock;
    private MockedStatic<BukkitAdapter> bukkitAdapterMock;
    private RegionContainer regionContainer;

    private ProtectedRegion protectedRegion;
    private WorldGuardRegion wgRegion;

    private static final Logger LOG = Logger.getLogger("RealtyPaperApiImplTest");
    private static final UUID ESCROW_ID = UUID.randomUUID();
    private static final Party.Personal HOLDING = Party.personal(ESCROW_ID);
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
                    List.of(UUID.randomUUID())),
            FeeQuote.none(1000.0));

    @Test
    void partialAuctionPaymentUsesAuctionSourceWithoutFinalizingOwnership() {
        when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 25.0)).thenReturn(
                new RealtyBackend.PayBidResult.Success(25, 75, GOVERNMENT, TITLE_HOLDER_ID, true));
        when(economyProvider.getBalance(BUYER)).thenReturn(100.0);
        when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(TITLE_HOLDER), eq(25.0), any(), eq(BUYER_ID)))
                .thenReturn(new PaymentResult.Success());
        Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.Success.class,
                api.payBid(wgRegion, BUYER_ID, 25.0).join());
        verify(economyProvider).transferAuctionProceeds(BUYER, TITLE_HOLDER, 25.0,
                "Auction Bid Payment (partial): " + REGION_ID, BUYER_ID);
        verify(realtyApi, never()).finalizeBidPurchase(any(), any(), any());
        verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
    }

    @Test
    void finalAuctionPaymentUsesAuctionSourceBeforeTransferringOwnership() {
        when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 75.0)).thenReturn(
                new RealtyBackend.PayBidResult.FullyPaid(GOVERNMENT, TITLE_HOLDER_ID, FeeQuote.none(75), true, 9));
        when(economyProvider.getBalance(BUYER)).thenReturn(100.0);
        when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(TITLE_HOLDER), eq(75.0), any(), eq(BUYER_ID)))
                .thenReturn(new PaymentResult.Success());
        when(realtyApi.finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(true);
        when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
        Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.FullyPaid.class,
                api.payBid(wgRegion, BUYER_ID, 75.0).join());
        var order = inOrder(economyProvider, realtyApi);
        order.verify(economyProvider).transferAuctionProceeds(BUYER, TITLE_HOLDER, 75.0,
                "Auction Bid Payment (final): " + REGION_ID, BUYER_ID);
        order.verify(realtyApi).finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID);
        Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
    }

    @Test
    void failedAuctionSettlementRollsBackPaymentAndDoesNotTransferOwnership() {
        when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 75.0)).thenReturn(
                new RealtyBackend.PayBidResult.FullyPaid(GOVERNMENT, TITLE_HOLDER_ID, FeeQuote.none(75), true, 9));
        when(economyProvider.getBalance(BUYER)).thenReturn(100.0);
        when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(TITLE_HOLDER), eq(75.0), any(), eq(BUYER_ID)))
                .thenReturn(new PaymentResult.Failure("Rejected"));
        Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.PaymentFailed.class,
                api.payBid(wgRegion, BUYER_ID, 75.0).join());
        verify(realtyApi).rollbackPayBid(REGION_ID, WORLD_ID, BUYER_ID, 75.0);
        verify(realtyApi, never()).finalizeBidPurchase(any(), any(), any());
        Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
    }

    @BeforeEach
    void setUp() {
        signCache = new SignCache();
        holding = HoldingAccount.vault(() -> new DefaultParties(null, null, null, false, ESCROW_ID, List.of()));
        settlement = new SaleSettlement(economyProvider, realtyApi, holding, events, LOG);
        ExecutorState executorState = new ExecutorState(Runnable::run, sameThreadExecutorService(), sameThreadExecutorService());
        lenient().when(treasury.getMembers(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        lenient().when(treasury.getAuthorizers(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        ActorContexts actorContexts = new ActorContexts(treasury, null,
                new AtomicReference<>(new Settings(null, null, null, null, new SimpleDateFormat("yyyy"),
                        0, 0, 0, 0, List.of(), null, 0, 0, 0, 0, AccountManagers.MEMBERS)),
                realtyApi);
        api = new RealtyPaperApiImpl(server, realtyApi, economyProvider, executorState, database,
                regionProfileService, signTextApplicator, signCache, () -> 604800,
                new SafeLocationFinder(), stubPlayerNameService(), accountId -> CompletableFuture.completedFuture(Optional.empty()),
                actorContexts, events, holding, settlement, LOG);

        lenient().when(world.getUID()).thenReturn(WORLD_ID);
        // An instalment whose money moved is confirmed, as the backend does while its payment exists.
        lenient().when(realtyApi.confirmPayBid(any(), any(), any(), anyDouble(), anyBoolean())).thenReturn(true);
        lenient().when(realtyApi.confirmPayOffer(any(), any(), any(), anyDouble(), anyBoolean())).thenReturn(true);

        protectedRegion = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(0, 0, 0), BlockVector3.at(100, 100, 100));
        wgRegion = new WorldGuardRegion(protectedRegion, world);

        // Mock WorldGuard static chain: getInstance() -> platform -> regionContainer -> get() -> null
        // Returning null for RegionManager makes updateChildLandlords return early
        WorldGuard worldGuardInstance = org.mockito.Mockito.mock(WorldGuard.class);
        WorldGuardPlatform platform = org.mockito.Mockito.mock(WorldGuardPlatform.class);
        regionContainer = org.mockito.Mockito.mock(RegionContainer.class);
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

            // The fees are recorded only once the buyer owns the region.
            doAnswer(invocation -> {
                Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
                return null;
            }).when(realtyApi).recordBuyFees(REGION_ID, WORLD_ID, BUYER_ID, TITLE_HOLDER, RESERVED.fees());

            api.buy(wgRegion, BUYER_CTX, false).join();

            InOrder order = inOrder(realtyApi, economyProvider);
            order.verify(realtyApi).executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false);
            order.verify(realtyApi).getRegionPlaceholders(REGION_ID, WORLD_ID);
            order.verify(economyProvider).transfer(eq(BUYER), eq(TITLE_HOLDER), eq(1000.0), any(), eq(BUYER_ID));
            order.verify(realtyApi).recordBuyFees(REGION_ID, WORLD_ID, BUYER_ID, TITLE_HOLDER, RESERVED.fees());
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
                    1000.0, GOVERNMENT, null, RESERVED.undo(), FeeQuote.none(1000.0));
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
                            new AtomicReference<>(new Settings(null, null, null, null,
                                    new SimpleDateFormat("yyyy"), 0, 0, 0, 0, List.of(), null,
                                    0, 0, 0, 0, AccountManagers.MEMBERS)),
                            realtyApi), events, holding, settlement, LOG);
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

        @Test
        @DisplayName("a transfer to the current holder is refused and leaves owners, members, flags and signs untouched")
        void transferToCurrentHolder() {
            UUID member = UUID.randomUUID();
            protectedRegion.getOwners().addPlayer(TITLE_HOLDER_ID);
            protectedRegion.getMembers().addPlayer(member);

            when(realtyApi.transferTitleHolder(REGION_ID, WORLD_ID, TITLE_HOLDER_ID))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.NoChange());

            RealtyPaperApi.SetTitleHolderResult result =
                    api.transferTitleHolder(wgRegion, TITLE_HOLDER).join();

            RealtyPaperApi.SetTitleHolderResult.NoChange refused = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTitleHolderResult.NoChange.class, result);
            Assertions.assertEquals(REGION_ID, refused.regionId());
            Assertions.assertTrue(protectedRegion.getOwners().contains(TITLE_HOLDER_ID));
            Assertions.assertTrue(protectedRegion.getMembers().contains(member));
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
        }

        @Test
        @DisplayName("setting the current holder is refused and leaves owners, members, flags and signs untouched")
        void setToCurrentHolder() {
            UUID member = UUID.randomUUID();
            protectedRegion.getOwners().addPlayer(TITLE_HOLDER_ID);
            protectedRegion.getMembers().addPlayer(member);

            when(realtyApi.setTitleHolder(REGION_ID, WORLD_ID, TITLE_HOLDER_ID, ActorContext.console()))
                    .thenReturn(new RealtyBackend.SetTitleHolderResult.NoChange());

            RealtyPaperApi.SetTitleHolderResult result = api.setTitleHolder(wgRegion, TITLE_HOLDER).join();

            Assertions.assertInstanceOf(RealtyPaperApi.SetTitleHolderResult.NoChange.class, result);
            Assertions.assertTrue(protectedRegion.getOwners().contains(TITLE_HOLDER_ID));
            Assertions.assertTrue(protectedRegion.getMembers().contains(member));
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
                            new AtomicReference<>(new Settings(null, null, null, null,
                                    new SimpleDateFormat("yyyy"), 0, 0, 0, 0, List.of(), null,
                                    0, 0, 0, 0, AccountManagers.MEMBERS)),
                            realtyApi), events, holding, settlement, LOG);
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
        @DisplayName("NoChange leaves owners, members, flags and signs untouched")
        void alreadyTenant() {
            UUID member = UUID.randomUUID();
            protectedRegion.getOwners().addPlayer(TENANT_ID);
            protectedRegion.getMembers().addPlayer(member);

            when(realtyApi.setTenant(REGION_ID, WORLD_ID, TENANT_ID, ActorContext.console(), false))
                    .thenReturn(new RealtyBackend.SetTenantResult.NoChange());

            RealtyPaperApi.SetTenantResult result = api.setTenant(wgRegion, TENANT).join();

            RealtyPaperApi.SetTenantResult.NoChange refused = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetTenantResult.NoChange.class, result);
            Assertions.assertEquals(REGION_ID, refused.regionId());
            Assertions.assertTrue(protectedRegion.getOwners().contains(TENANT_ID));
            Assertions.assertTrue(protectedRegion.getMembers().contains(member));
            verify(regionProfileService, never()).applyFlags(any(), any(), any());
            verify(signTextApplicator, never()).updateLoadedSigns(any(), any(), any(), any());
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

        @Test
        @DisplayName("NoChange keeps the members")
        void alreadyLandlord() {
            UUID member = UUID.randomUUID();
            protectedRegion.getMembers().addPlayer(member);

            when(realtyApi.setLandlord(REGION_ID, WORLD_ID, LANDLORD, ActorContext.console(), false))
                    .thenReturn(new RealtyBackend.SetLandlordResult.NoChange());

            RealtyPaperApi.SetLandlordResult result = api.setLandlord(wgRegion, LANDLORD).join();

            RealtyPaperApi.SetLandlordResult.NoChange refused = Assertions.assertInstanceOf(
                    RealtyPaperApi.SetLandlordResult.NoChange.class, result);
            Assertions.assertEquals(REGION_ID, refused.regionId());
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

    /** The auction actions fire their events from the API, so a plugin calling them gets the same as a command. */
    @Nested
    class AuctionEvents {

        private static final RealtyBackend.RegionInfo BID_STANDING = new RealtyBackend.RegionInfo(null, null,
                new FreeholdContractAuctionEntity(1, 1, AUTHORITY_ID, LocalDateTime.of(2026, 10, 1, 12, 0),
                        3600, 600, LocalDateTime.of(2026, 10, 2, 12, 0), 100, 10, false),
                null, new FreeholdContractBid(1, BUYER_ID, 150, LocalDateTime.of(2026, 10, 1, 13, 0)));

        @BeforeEach
        void regionIsKnownToWorldGuard() {
            RegionManager regionManager = org.mockito.Mockito.mock(RegionManager.class);
            lenient().when(regionManager.getRegion(REGION_ID)).thenReturn(protectedRegion);
            lenient().when(regionContainer.get(any())).thenReturn(regionManager);
            lenient().when(server.getWorld(WORLD_ID)).thenReturn(world);
            lenient().when(events.fireSync(any(RealtyRegionEvent.class))).thenReturn(true);
        }

        private <E extends RealtyRegionEvent> E fired(Class<E> type) {
            ArgumentCaptor<RealtyRegionEvent> captor = ArgumentCaptor.forClass(RealtyRegionEvent.class);
            verify(events, org.mockito.Mockito.atLeastOnce()).fireSync(captor.capture());
            return captor.getAllValues().stream().filter(type::isInstance).map(type::cast)
                    .findFirst().orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " fired"));
        }

        @Test
        void createFiresBothEvents() {
            when(realtyApi.createAuction(REGION_ID, WORLD_ID, BUYER_CTX, 3600, 600, 100, 10))
                    .thenReturn(new RealtyBackend.CreateAuctionResult.Success());

            Assertions.assertInstanceOf(RealtyBackend.CreateAuctionResult.Success.class,
                    api.createAuction(REGION_ID, WORLD_ID, BUYER_CTX, 3600, 600, 100, 10).join());

            Assertions.assertEquals(BUYER_ID, fired(AuctionCreateEvent.class).getAuctioneerId());
            AuctionCreatedEvent created = fired(AuctionCreatedEvent.class);
            Assertions.assertEquals(3600, created.getBiddingDurationSeconds());
            Assertions.assertEquals(600, created.getPaymentDurationSeconds());
        }

        @Test
        void createVetoedByAListenerIsCancelled() {
            when(events.fireSync(any(AuctionCreateEvent.class))).thenReturn(false);

            Assertions.assertInstanceOf(RealtyBackend.CreateAuctionResult.Cancelled.class,
                    api.createAuction(REGION_ID, WORLD_ID, BUYER_CTX, 3600, 600, 100, 10).join());

            verify(realtyApi, never()).createAuction(any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
        }

        @Test
        void bidNamesTheOutbidPlayerAndTheNewEnd() {
            UUID outbid = UUID.randomUUID();
            when(realtyApi.performBid(REGION_ID, WORLD_ID, BUYER_CTX, 150, false))
                    .thenReturn(new RealtyBackend.BidResult.Success(outbid));
            when(realtyApi.getRegionInfo(REGION_ID, WORLD_ID)).thenReturn(BID_STANDING);

            Assertions.assertInstanceOf(RealtyBackend.BidResult.Success.class,
                    api.performBid(REGION_ID, WORLD_ID, BUYER_CTX, 150, false).join());

            AuctionBidPlacedEvent placed = fired(AuctionBidPlacedEvent.class);
            Assertions.assertEquals(Optional.of(outbid), placed.getPreviousBidderId());
            Assertions.assertEquals(LocalDateTime.of(2026, 10, 1, 14, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant(), placed.getBiddingEndsAt());
        }

        @Test
        void bidVetoedByAListenerIsCancelled() {
            when(events.fireSync(any(AuctionBidEvent.class))).thenReturn(false);

            Assertions.assertInstanceOf(RealtyBackend.BidResult.Cancelled.class,
                    api.performBid(REGION_ID, WORLD_ID, BUYER_CTX, 150, false).join());

            verify(realtyApi, never()).performBid(any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        void cancelByTheConsoleStillFiresTheEvent() {
            UUID bidder = UUID.randomUUID();
            when(realtyApi.cancelAuction(REGION_ID, WORLD_ID))
                    .thenReturn(new RealtyBackend.CancelAuctionResult(1, List.of(bidder)));

            api.cancelAuction(REGION_ID, WORLD_ID, null).join();

            AuctionCancelledEvent cancelled = fired(AuctionCancelledEvent.class);
            Assertions.assertTrue(cancelled.getActorId().isEmpty());
            Assertions.assertEquals(List.of(bidder), cancelled.getBidderIds());
        }

        @Test
        void cancelWithNoAuctionFiresNothing() {
            when(realtyApi.cancelAuction(REGION_ID, WORLD_ID))
                    .thenReturn(new RealtyBackend.CancelAuctionResult(0, List.of()));

            api.cancelAuction(REGION_ID, WORLD_ID, BUYER_ID).join();

            verify(events, never()).fireSync(any(RealtyRegionEvent.class));
        }
    }

    /** The offer actions fire their events from the API, as the auction actions do. */
    @Nested
    class OfferEvents {

        @BeforeEach
        void regionIsKnownToWorldGuard() {
            RegionManager regionManager = org.mockito.Mockito.mock(RegionManager.class);
            lenient().when(regionManager.getRegion(REGION_ID)).thenReturn(protectedRegion);
            lenient().when(regionContainer.get(any())).thenReturn(regionManager);
            lenient().when(server.getWorld(WORLD_ID)).thenReturn(world);
            lenient().when(events.fireSync(any(RealtyRegionEvent.class))).thenReturn(true);
        }

        private List<RealtyRegionEvent> firedEvents() {
            ArgumentCaptor<RealtyRegionEvent> captor = ArgumentCaptor.forClass(RealtyRegionEvent.class);
            verify(events, org.mockito.Mockito.atLeastOnce()).fireSync(captor.capture());
            return captor.getAllValues();
        }

        @Test
        void placeFiresBothEvents() {
            when(realtyApi.placeOffer(REGION_ID, WORLD_ID, BUYER_CTX, 500, false))
                    .thenReturn(new RealtyBackend.OfferResult.Success(TITLE_HOLDER_ID));

            api.placeOffer(REGION_ID, WORLD_ID, BUYER_CTX, 500, false).join();

            List<RealtyRegionEvent> fired = firedEvents();
            Assertions.assertInstanceOf(OfferPlaceEvent.class, fired.get(0));
            OfferPlacedEvent placed = Assertions.assertInstanceOf(OfferPlacedEvent.class, fired.get(1));
            Assertions.assertEquals(TITLE_HOLDER, placed.getTitleHolder());
        }

        @Test
        void placeVetoedByAListenerIsCancelled() {
            when(events.fireSync(any(OfferPlaceEvent.class))).thenReturn(false);

            Assertions.assertInstanceOf(RealtyBackend.OfferResult.Cancelled.class,
                    api.placeOffer(REGION_ID, WORLD_ID, BUYER_CTX, 500, false).join());

            verify(realtyApi, never()).placeOffer(any(), any(), any(),
                    org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        void acceptVetoedByAListenerIsCancelled() {
            when(events.fireSync(any(OfferAcceptEvent.class))).thenReturn(false);

            Assertions.assertInstanceOf(RealtyBackend.AcceptOfferResult.Cancelled.class,
                    api.acceptOffer(REGION_ID, WORLD_ID, BUYER_CTX, TENANT_ID).join());

            verify(realtyApi, never()).acceptOffer(any(), any(), any(), any());
        }

        @Test
        void rejectAllFiresOneEventPerOffer() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            when(realtyApi.rejectAllOffers(REGION_ID, WORLD_ID, ActorContext.console()))
                    .thenReturn(new RealtyBackend.RejectAllOffersResult.Success(List.of(first, second)));

            api.rejectAllOffers(REGION_ID, WORLD_ID, ActorContext.console()).join();

            List<OfferRejectedEvent> rejected = firedEvents().stream()
                    .map(OfferRejectedEvent.class::cast).toList();
            Assertions.assertEquals(List.of(first, second),
                    rejected.stream().map(OfferRejectedEvent::getOffererId).toList());
            Assertions.assertTrue(rejected.getFirst().getRejecterId().isEmpty());
        }

        @Test
        void toggleFiresOnlyOnAChange() {
            when(realtyApi.toggleOffers(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.ToggleOffersResult.Success(false));
            when(realtyApi.toggleOffers(REGION_ID, WORLD_ID, BUYER_CTX, true))
                    .thenReturn(new RealtyBackend.ToggleOffersResult.NoChange());

            api.toggleOffers(REGION_ID, WORLD_ID, BUYER_CTX, false).join();
            api.toggleOffers(REGION_ID, WORLD_ID, BUYER_CTX, true).join();

            List<RealtyRegionEvent> fired = firedEvents();
            Assertions.assertEquals(1, fired.size());
            OffersToggledEvent toggled = Assertions.assertInstanceOf(OffersToggledEvent.class, fired.getFirst());
            Assertions.assertFalse(toggled.isAcceptingOffers());
            Assertions.assertEquals(Optional.of(BUYER_ID), toggled.getActorId());
        }
    }

    // ═══════════════════════════════════════════════════
    // the holding account and sale fees
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("sale money")
    class SaleMoney {

        private static final Party.Personal AGENT = Party.personal(UUID.randomUUID());
        /** 1000 with a 3% seller-paid agent fee: the seller receives 970. */
        private static final FeeQuote AGENT_QUOTE = new FeeQuote(1000,
                List.of(new FeeLine("agent", AGENT, FeeSide.SELLER, 3, 30)), 1000, 970);
        /** 1000 with a 5% buyer-paid levy and a 3% seller-paid agent fee. */
        private static final FeeQuote BUY_QUOTE = new FeeQuote(1000,
                List.of(new FeeLine("stamp-duty", GOVERNMENT, FeeSide.BUYER, 5, 50),
                        new FeeLine("agent", AGENT, FeeSide.SELLER, 3, 30)), 1050, 970);
        private static final RealtyBackend.DuePayout PROCEEDS_LINE = new RealtyBackend.DuePayout(
                1, "proceeds", "proceeds", TITLE_HOLDER, 970, REGION_ID, WORLD_ID, BUYER_ID, 0, 1000, false);
        private static final RealtyBackend.DuePayout AGENT_LINE = new RealtyBackend.DuePayout(
                2, "fee", "agent", AGENT, 30, REGION_ID, WORLD_ID, BUYER_ID, 0, 1000, false);
        /** The same lines from a sale at auction: the seller's proceeds carry the auction source. */
        private static final RealtyBackend.DuePayout AUCTION_PROCEEDS_LINE = new RealtyBackend.DuePayout(
                1, "proceeds", "proceeds", TITLE_HOLDER, 970, REGION_ID, WORLD_ID, BUYER_ID, 0, 1000, true);
        private static final RealtyBackend.DuePayout AUCTION_AGENT_LINE = new RealtyBackend.DuePayout(
                2, "fee", "agent", AGENT, 30, REGION_ID, WORLD_ID, BUYER_ID, 0, 1000, true);

        private RealtyPaperApiImpl withoutHolding() {
            return withHolding(HoldingAccount.vault(DefaultParties::unresolved));
        }

        private RealtyPaperApiImpl withHolding(HoldingAccount none) {
            return new RealtyPaperApiImpl(server, realtyApi, economyProvider,
                    new ExecutorState(Runnable::run, sameThreadExecutorService(), sameThreadExecutorService()),
                    database, regionProfileService, signTextApplicator, signCache, () -> 604800,
                    new SafeLocationFinder(), stubPlayerNameService(),
                    accountId -> CompletableFuture.completedFuture(Optional.empty()),
                    new ActorContexts(treasury, null,
                            new AtomicReference<>(new Settings(null, null, null, null,
                                    new SimpleDateFormat("yyyy"), 0, 0, 0, 0, List.of(), null,
                                    0, 0, 0, 0, AccountManagers.MEMBERS)),
                            realtyApi),
                    events, none, new SaleSettlement(economyProvider, realtyApi, none, events, LOG), LOG);
        }

        private void paidInFull(double amount, FeeQuote quote, boolean oldRules) {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, amount)).thenReturn(
                    new RealtyBackend.PayBidResult.FullyPaid(GOVERNMENT, TITLE_HOLDER_ID, quote, oldRules, 9));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);
            lenient().when(realtyApi.finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(true);
        }

        private void payoutsDue(RealtyBackend.DuePayout... lines) {
            when(realtyApi.duePayouts()).thenReturn(List.of(lines));
            lenient().when(realtyApi.claimPayout(org.mockito.ArgumentMatchers.anyInt())).thenReturn(OptionalInt.of(0));
        }

        @Test
        void payBid_partial_goesToHolding() {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0)).thenReturn(
                    new RealtyBackend.PayBidResult.Success(300, 700, GOVERNMENT, TITLE_HOLDER_ID, false));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(300.0),
                    eq("Auction Bid Payment (partial): " + REGION_ID), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 300).join();

            Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.Success.class, result);
            verify(economyProvider, never()).transfer(any(), eq(TITLE_HOLDER), anyDouble(), any(), any());
            verify(realtyApi, never()).rollbackPayBid(any(), any(), any(), anyDouble());
        }

        @Test
        void payBid_partial_withoutHolding_failsAndRollsBack() {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0)).thenReturn(
                    new RealtyBackend.PayBidResult.Success(300, 700, GOVERNMENT, TITLE_HOLDER_ID, false));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);

            RealtyPaperApi.PayBidResult result = withoutHolding().payBid(wgRegion, BUYER_ID, 300).join();

            Assertions.assertEquals(new RealtyPaperApi.PayBidResult.HoldingPaused(REGION_ID), result);
            verify(realtyApi).rollbackPayBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0);
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
            verify(economyProvider, never()).transferAuctionProceeds(any(), any(), anyDouble(), any(), any());
        }

        /** Realty's Treasury SYSTEM account #17, archived since startup. */
        private HoldingAccount archivedHolding() {
            io.paradaux.treasury.model.economy.Account account = new io.paradaux.treasury.model.economy.Account(
                    17, io.paradaux.treasury.model.economy.AccountType.SYSTEM, UUID.randomUUID(), "Realty Holding",
                    false, false, false, null);
            when(treasury.getAccountsByTypeAndOwner(eq(io.paradaux.treasury.model.economy.AccountType.SYSTEM),
                    any())).thenReturn(List.of(account));
            HoldingAccount holding = io.github.md5sha256.realty.economy.TreasuryHoldingAccount.create(treasury, LOG);
            io.paradaux.treasury.model.economy.Account archived = new io.paradaux.treasury.model.economy.Account(
                    17, io.paradaux.treasury.model.economy.AccountType.SYSTEM, UUID.randomUUID(), "Realty Holding",
                    false, false, false, null).withArchived(true);
            when(treasury.getAccountById(17)).thenReturn(archived);
            return holding;
        }

        @Test
        void payBid_whileUnusable_saysPaused() throws java.io.IOException {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0)).thenReturn(
                    new RealtyBackend.PayBidResult.Success(300, 700, GOVERNMENT, TITLE_HOLDER_ID, false));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);

            RealtyPaperApi.PayBidResult result = withHolding(archivedHolding()).payBid(wgRegion, BUYER_ID, 300).join();

            Assertions.assertEquals(new RealtyPaperApi.PayBidResult.HoldingPaused(REGION_ID), result);
            verify(realtyApi).rollbackPayBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0);
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
            io.github.md5sha256.realty.localisation.MessageContainer messages =
                    new io.github.md5sha256.realty.localisation.MessageContainer();
            try (java.io.InputStream in = RealtyPaperApiImplTest.class.getResourceAsStream("/messages.yml")) {
                Assertions.assertNotNull(in);
                messages.load(org.spongepowered.configurate.yaml.YamlConfigurationLoader.builder()
                        .source(() -> new java.io.BufferedReader(new java.io.InputStreamReader(in,
                                java.nio.charset.StandardCharsets.UTF_8)))
                        .build()
                        .load());
            }
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(messages.messageFor(io.github.md5sha256.realty.localisation.MessageKeys.PAY_HOLDING_PAUSED,
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("region", REGION_ID)));
            Assertions.assertTrue(text.endsWith("Payments for " + REGION_ID + " are paused until an admin fixes"
                    + " Realty's holding account. Your deadline is paused too."), text);
        }

        @Test
        void payOffer_whileUnusable_saysPaused() {
            when(realtyApi.payOffer(REGION_ID, WORLD_ID, BUYER_ID, 300.0)).thenReturn(
                    new RealtyBackend.PayOfferResult.Success(300, 700, GOVERNMENT, TITLE_HOLDER_ID, false));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);

            RealtyPaperApi.PayOfferResult result =
                    withHolding(archivedHolding()).payOffer(wgRegion, BUYER_ID, 300).join();

            Assertions.assertEquals(new RealtyPaperApi.PayOfferResult.HoldingPaused(REGION_ID), result);
            verify(realtyApi).rollbackPayOffer(REGION_ID, WORLD_ID, BUYER_ID, 300.0);
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
        }

        @Test
        void payBid_final_paysSellerAndRecipients_fromHolding() {
            paidInFull(1000.0, AGENT_QUOTE, false);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(1000.0),
                    eq("Auction Bid Payment (final): " + REGION_ID), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            payoutsDue(AUCTION_PROCEEDS_LINE, AUCTION_AGENT_LINE);
            when(economyProvider.transfer(eq(HOLDING), any(), anyDouble(), any(), isNull()))
                    .thenReturn(new PaymentResult.Success());
            when(economyProvider.transferAuctionProceeds(eq(HOLDING), eq(TITLE_HOLDER), anyDouble(), any(), isNull()))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 1000).join();

            Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.FullyPaid.class, result);
            InOrder order = inOrder(realtyApi, economyProvider);
            order.verify(economyProvider).transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID));
            order.verify(realtyApi).finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID);
            order.verify(economyProvider).transferAuctionProceeds(eq(HOLDING), eq(TITLE_HOLDER), eq(970.0),
                    eq("Sale payout (proceeds): " + REGION_ID), isNull());
            order.verify(economyProvider).transfer(eq(HOLDING), eq(AGENT), eq(30.0),
                    eq("Sale payout (agent): " + REGION_ID), isNull());
            verify(realtyApi).markPayoutPaid(1);
            verify(realtyApi).markPayoutPaid(2);
            verify(economyProvider, never()).transferAuctionProceeds(eq(BUYER), eq(TITLE_HOLDER), anyDouble(), any(), any());
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
        }

        @Test
        void payBid_oldRules_paysSellerDirectly() {
            paidInFull(1000.0, FeeQuote.none(1000), true);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(TITLE_HOLDER), eq(1000.0),
                    eq("Auction Bid Payment (final): " + REGION_ID), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 1000).join();

            Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.FullyPaid.class, result);
            verify(realtyApi).finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID);
            verify(economyProvider, never()).transferAuctionProceeds(eq(BUYER), eq(HOLDING), anyDouble(), any(), any());
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
        }

        @Test
        void payBid_partial_oldRules_paysSellerDirectly() {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0)).thenReturn(
                    new RealtyBackend.PayBidResult.Success(300, 700, GOVERNMENT, TITLE_HOLDER_ID, true));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(TITLE_HOLDER), eq(300.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.Success.class,
                    api.payBid(wgRegion, BUYER_ID, 300).join());
        }

        @Test
        void payout_oneRecipientFails_buyerStillOwns_lineFailed_retriedLater() {
            paidInFull(1000.0, AGENT_QUOTE, false);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            payoutsDue(AUCTION_PROCEEDS_LINE, AUCTION_AGENT_LINE);
            when(economyProvider.transferAuctionProceeds(eq(HOLDING), eq(TITLE_HOLDER), eq(970.0), any(), isNull()))
                    .thenReturn(new PaymentResult.Success());
            when(economyProvider.transfer(eq(HOLDING), eq(AGENT), eq(30.0), any(), isNull()))
                    .thenReturn(new PaymentResult.Failure("Account is archived"),
                            new PaymentResult.Success());

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 1000).join();

            Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.FullyPaid.class, result);
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
            verify(realtyApi).finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID);
            verify(realtyApi).markPayoutPaid(1);
            verify(realtyApi).markPayoutFailed(2, "Account is archived");
            verify(realtyApi, never()).rollbackPayBid(any(), any(), any(), anyDouble());

            when(realtyApi.duePayouts()).thenReturn(List.of(new RealtyBackend.DuePayout(
                    2, "fee", "agent", AGENT, 30, REGION_ID, WORLD_ID, BUYER_ID, 1, 1000, true)));
            settlement.payOutDue();

            verify(realtyApi).markPayoutPaid(2);
        }

        @Test
        void payOffer_partialAndFinal_goToHolding() {
            when(realtyApi.payOffer(REGION_ID, WORLD_ID, BUYER_ID, 400.0)).thenReturn(
                    new RealtyBackend.PayOfferResult.Success(400, 600, GOVERNMENT, TITLE_HOLDER_ID, false));
            when(realtyApi.payOffer(REGION_ID, WORLD_ID, BUYER_ID, 600.0)).thenReturn(
                    new RealtyBackend.PayOfferResult.FullyPaid(GOVERNMENT, TITLE_HOLDER_ID, AGENT_QUOTE, false, 4));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);
            when(economyProvider.transfer(eq(BUYER), eq(HOLDING), anyDouble(), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(realtyApi.finalizeOfferPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(true);
            payoutsDue(PROCEEDS_LINE);
            when(economyProvider.transfer(eq(HOLDING), eq(TITLE_HOLDER), eq(970.0), any(), isNull()))
                    .thenReturn(new PaymentResult.Success());

            Assertions.assertInstanceOf(RealtyPaperApi.PayOfferResult.Success.class,
                    api.payOffer(wgRegion, BUYER_ID, 400).join());
            Assertions.assertInstanceOf(RealtyPaperApi.PayOfferResult.FullyPaid.class,
                    api.payOffer(wgRegion, BUYER_ID, 600).join());

            verify(economyProvider).transfer(eq(BUYER), eq(HOLDING), eq(400.0),
                    eq("Purchase Offer Payment (partial): " + REGION_ID), eq(BUYER_ID));
            verify(economyProvider).transfer(eq(BUYER), eq(HOLDING), eq(600.0),
                    eq("Purchase Offer Payment (final): " + REGION_ID), eq(BUYER_ID));
            verify(realtyApi).confirmPayOffer(REGION_ID, WORLD_ID, BUYER_ID, 400.0, false);
            verify(realtyApi).confirmPayOffer(REGION_ID, WORLD_ID, BUYER_ID, 600.0, false);
            verify(realtyApi).finalizeOfferPurchase(REGION_ID, WORLD_ID, BUYER_ID);
            verify(realtyApi).markPayoutPaid(1);
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
            ArgumentCaptor<io.github.md5sha256.realty.api.event.OfferPurchaseCompletedEvent> fired =
                    ArgumentCaptor.forClass(io.github.md5sha256.realty.api.event.OfferPurchaseCompletedEvent.class);
            verify(events).fireSync(fired.capture());
            Assertions.assertEquals(1000, fired.getValue().getAmount(), 0.001, "the sale price, not the instalment");
        }

        @Test
        void payBid_final_confirmsBeforeFinalizing() {
            paidInFull(1000.0, AGENT_QUOTE, false);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            payoutsDue();

            api.payBid(wgRegion, BUYER_ID, 1000).join();

            InOrder order = inOrder(realtyApi, economyProvider);
            order.verify(economyProvider).transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID));
            order.verify(realtyApi).confirmPayBid(REGION_ID, WORLD_ID, BUYER_ID, 1000.0, false);
            order.verify(realtyApi).finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID);
        }

        @Test
        void payBid_saleEndedWhileTheMoneyMoved_failsAndSaysItIsRefunded() {
            paidInFull(1000.0, AGENT_QUOTE, false);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(realtyApi.confirmPayBid(REGION_ID, WORLD_ID, BUYER_ID, 1000.0, false)).thenReturn(false);

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 1000).join();

            RealtyPaperApi.PayBidResult.PaymentFailed failed =
                    Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.PaymentFailed.class, result);
            Assertions.assertTrue(failed.error().contains("refunded"), failed.error());
            verify(realtyApi, never()).finalizeBidPurchase(any(), any(), any());
            verify(realtyApi, never()).rollbackPayBid(any(), any(), any(), anyDouble());
        }

        @Test
        void payBid_confirmThrows_leavesItPending_andSaysAnAdminWasAlerted() {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0)).thenReturn(
                    new RealtyBackend.PayBidResult.Success(300, 700, GOVERNMENT, TITLE_HOLDER_ID, false));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(300.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(realtyApi.confirmPayBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0, false))
                    .thenThrow(new IllegalStateException("connection lost"));

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 300).join();

            RealtyPaperApi.PayBidResult.PaymentFailed failed =
                    Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.PaymentFailed.class, result);
            Assertions.assertTrue(failed.error().contains("admin"), failed.error());
            verify(realtyApi, never()).rollbackPayBid(any(), any(), any(), anyDouble());
        }

        @Test
        void payBid_whileAnInstalmentIsPending_isRefused() {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0))
                    .thenReturn(new RealtyBackend.PayBidResult.PaymentInProgress());

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 300).join();

            Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.PaymentFailed.class, result);
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
            verify(economyProvider, never()).transferAuctionProceeds(any(), any(), anyDouble(), any(), any());
        }

        @Test
        void payBid_oldRulesFinalizeFails_isPending_paidToSeller() {
            paidInFull(1000.0, FeeQuote.none(1000), true);
            when(realtyApi.finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(false);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(TITLE_HOLDER), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 1000).join();

            Assertions.assertEquals(new RealtyPaperApi.PayBidResult.FinalizePending(1000, REGION_ID, true), result);
            Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
        }

        @Test
        void payBid_zeroOrNegative_refusedBeforeTheBackend() {
            Assertions.assertEquals(new RealtyPaperApi.PayBidResult.InvalidAmount(0),
                    api.payBid(wgRegion, BUYER_ID, 0).join());
            Assertions.assertEquals(new RealtyPaperApi.PayBidResult.InvalidAmount(-3),
                    api.payBid(wgRegion, BUYER_ID, -3).join());
            Assertions.assertEquals(new RealtyPaperApi.PayBidResult.InvalidAmount(0.004),
                    api.payBid(wgRegion, BUYER_ID, 0.004).join());
            Assertions.assertEquals(new RealtyPaperApi.PayOfferResult.InvalidAmount(0),
                    api.payOffer(wgRegion, BUYER_ID, 0).join());
            verify(realtyApi, never()).payBid(any(), any(), any(), anyDouble());
            verify(realtyApi, never()).payOffer(any(), any(), any(), anyDouble());
        }

        @Test
        void payBid_subCentAmount_isCountedAndMovedInCents() {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0)).thenReturn(
                    new RealtyBackend.PayBidResult.Success(300, 700, GOVERNMENT, TITLE_HOLDER_ID, false));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(300.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(realtyApi.confirmPayBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0, false)).thenReturn(true);

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 299.996).join();

            RealtyPaperApi.PayBidResult.Success success =
                    Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.Success.class, result);
            Assertions.assertEquals(300.0, success.amount(), 0.0);
            verify(economyProvider).transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(300.0), any(), eq(BUYER_ID));
        }

        @Test
        void finishPendingSales_oldRules_transfersTheTitle_paysNothingOut() {
            RegionManager regionManager = org.mockito.Mockito.mock(RegionManager.class);
            when(regionContainer.get(any())).thenReturn(regionManager);
            when(regionManager.getRegion(REGION_ID)).thenReturn(protectedRegion);
            when(server.getWorld(WORLD_ID)).thenReturn(world);
            when(realtyApi.listFullyPaidUnfinalized()).thenReturn(List.of(new RealtyBackend.PendingFinalize(
                    RealtyBackend.PendingFinalize.Kind.BID, REGION_ID, WORLD_ID, BUYER_ID, TITLE_HOLDER_ID,
                    1000, FeeQuote.none(1000), true)));
            when(realtyApi.finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(true);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());

            api.finishPendingSales().join();

            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
            verify(realtyApi, never()).duePayouts();
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
        }

        private RealtyBackend.BuyResult.Success reservedWith(FeeQuote quote) {
            RealtyBackend.BuyResult.Success reserved = new RealtyBackend.BuyResult.Success(
                    1000.0, GOVERNMENT, TITLE_HOLDER_ID, RESERVED.undo(), quote);
            when(realtyApi.executeBuy(REGION_ID, WORLD_ID, BUYER_CTX, false)).thenReturn(reserved);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            return reserved;
        }

        @Test
        void buy_withFees_paysEachRecipient() {
            reservedWith(BUY_QUOTE);
            when(economyProvider.getBalance(BUYER)).thenReturn(1050.0);
            when(economyProvider.transfer(eq(BUYER), any(), anyDouble(), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.Success.class, result);
            InOrder order = inOrder(economyProvider, realtyApi);
            order.verify(economyProvider).transfer(eq(BUYER), eq(TITLE_HOLDER), eq(970.0),
                    eq("Plot Purchase: " + REGION_ID), eq(BUYER_ID));
            order.verify(economyProvider).transfer(eq(BUYER), eq(GOVERNMENT), eq(50.0),
                    eq("Plot Purchase fee (stamp-duty): " + REGION_ID), eq(BUYER_ID));
            order.verify(economyProvider).transfer(eq(BUYER), eq(AGENT), eq(30.0),
                    eq("Plot Purchase fee (agent): " + REGION_ID), eq(BUYER_ID));
            order.verify(realtyApi).recordBuyFees(REGION_ID, WORLD_ID, BUYER_ID, TITLE_HOLDER, BUY_QUOTE);
            verify(realtyApi, never()).rollbackBuy(any(), any(), any(), any());
            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
        }

        @Test
        void buy_withFees_checksTheBuyerTotal() {
            RealtyBackend.BuyResult.Success reserved = reservedWith(BUY_QUOTE);
            when(economyProvider.getBalance(BUYER)).thenReturn(1040.0);

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            RealtyPaperApi.BuyResult.InsufficientFunds insufficient =
                    Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.InsufficientFunds.class, result);
            Assertions.assertEquals(1050.0, insufficient.price(), 0.001);
            verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, BUYER_ID, reserved);
            verify(economyProvider, never()).transfer(any(), any(), anyDouble(), any(), any());
        }

        @Test
        void buy_feeTransferFails_reversesSellerPayment_andRollsBack() {
            RealtyBackend.BuyResult.Success reserved = reservedWith(BUY_QUOTE);
            when(economyProvider.getBalance(BUYER)).thenReturn(2000.0);
            when(economyProvider.transfer(eq(BUYER), eq(TITLE_HOLDER), eq(970.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(economyProvider.transfer(eq(BUYER), eq(GOVERNMENT), eq(50.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Failure("Account #42 is archived"));
            when(economyProvider.transfer(eq(TITLE_HOLDER), eq(BUYER), eq(970.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.PaymentFailed.class, result);
            verify(economyProvider).transfer(eq(TITLE_HOLDER), eq(BUYER), eq(970.0),
                    eq("Plot Purchase reversal: " + REGION_ID), eq(BUYER_ID));
            verify(economyProvider, never()).transfer(eq(BUYER), eq(AGENT), anyDouble(), any(), any());
            verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, BUYER_ID, reserved);
            verify(realtyApi, never()).recordBuyFees(any(), any(), any(), any(), any());
            Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
        }
    
        @Test
        void payBid_final_finalizeAppliesNothing_finalizePending_worldUntouched() {
            paidInFull(1000.0, AGENT_QUOTE, false);
            when(realtyApi.finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(false);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 1000).join();

            Assertions.assertEquals(new RealtyPaperApi.PayBidResult.FinalizePending(1000, REGION_ID, false), result);
            Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
            verify(realtyApi, never()).duePayouts();
            verify(realtyApi, never()).rollbackPayBid(any(), any(), any(), anyDouble());
            verify(events, never()).fireSync(any(io.github.md5sha256.realty.api.event.AuctionWonPurchaseEvent.class));
        }

        @Test
        void payOffer_final_finalizeThrows_finalizePending() {
            when(realtyApi.payOffer(REGION_ID, WORLD_ID, BUYER_ID, 1000.0)).thenReturn(
                    new RealtyBackend.PayOfferResult.FullyPaid(GOVERNMENT, TITLE_HOLDER_ID, AGENT_QUOTE, false, 4));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);
            when(economyProvider.transfer(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(realtyApi.finalizeOfferPurchase(REGION_ID, WORLD_ID, BUYER_ID))
                    .thenThrow(new IllegalStateException("deadlock"));

            RealtyPaperApi.PayOfferResult result = api.payOffer(wgRegion, BUYER_ID, 1000).join();

            Assertions.assertEquals(new RealtyPaperApi.PayOfferResult.FinalizePending(1000, REGION_ID, false), result);
            Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
            verify(realtyApi, never()).rollbackPayOffer(any(), any(), any(), anyDouble());
        }

        @Test
        void finishPendingSales_finalizesThenCompletesTheSale() {
            RegionManager regionManager = org.mockito.Mockito.mock(RegionManager.class);
            when(regionContainer.get(any())).thenReturn(regionManager);
            when(regionManager.getRegion(REGION_ID)).thenReturn(protectedRegion);
            when(server.getWorld(WORLD_ID)).thenReturn(world);
            when(realtyApi.listFullyPaidUnfinalized()).thenReturn(List.of(new RealtyBackend.PendingFinalize(
                    RealtyBackend.PendingFinalize.Kind.BID, REGION_ID, WORLD_ID, BUYER_ID, TITLE_HOLDER_ID,
                    1000, AGENT_QUOTE, false)));
            when(realtyApi.finalizeBidPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(true);
            when(realtyApi.getRegionPlaceholders(REGION_ID, WORLD_ID)).thenReturn(Map.of());
            payoutsDue(AUCTION_PROCEEDS_LINE);
            when(economyProvider.transferAuctionProceeds(eq(HOLDING), eq(TITLE_HOLDER), eq(970.0), any(), isNull()))
                    .thenReturn(new PaymentResult.Success());

            api.finishPendingSales().join();

            Assertions.assertTrue(protectedRegion.getOwners().contains(BUYER_ID));
            verify(regionProfileService).applyFlags(eq(wgRegion), eq(RegionState.SOLD), any());
            verify(realtyApi).markPayoutPaid(1);
            ArgumentCaptor<io.github.md5sha256.realty.api.event.AuctionWonPurchaseEvent> fired =
                    ArgumentCaptor.forClass(io.github.md5sha256.realty.api.event.AuctionWonPurchaseEvent.class);
            verify(events).fireSync(fired.capture());
            Assertions.assertEquals(1000, fired.getValue().getAmount(), 0.001);
            Assertions.assertEquals(Party.personal(TITLE_HOLDER_ID), fired.getValue().getPreviousTitleHolder());
        }

        @Test
        void finishPendingSales_finalizeAppliesNothing_leavesTheWorld() {
            when(realtyApi.listFullyPaidUnfinalized()).thenReturn(List.of(new RealtyBackend.PendingFinalize(
                    RealtyBackend.PendingFinalize.Kind.OFFER, REGION_ID, WORLD_ID, BUYER_ID, null, 1000,
                    AGENT_QUOTE, false)));
            when(realtyApi.finalizeOfferPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(false);

            api.finishPendingSales().join();

            Assertions.assertFalse(protectedRegion.getOwners().contains(BUYER_ID));
            verify(realtyApi, never()).duePayouts();
        }

        @Test
        void buy_transferThrows_reversesAndRollsBack() {
            RealtyBackend.BuyResult.Success reserved = reservedWith(BUY_QUOTE);
            when(economyProvider.getBalance(BUYER)).thenReturn(2000.0);
            when(economyProvider.transfer(eq(BUYER), eq(TITLE_HOLDER), eq(970.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(economyProvider.transfer(eq(BUYER), eq(GOVERNMENT), eq(50.0), any(), eq(BUYER_ID)))
                    .thenThrow(new IllegalStateException("Treasury is down"));
            when(economyProvider.transfer(eq(TITLE_HOLDER), eq(BUYER), eq(970.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.BuyResult result = api.buy(wgRegion, BUYER_CTX, false).join();

            RealtyPaperApi.BuyResult.PaymentFailed failed =
                    Assertions.assertInstanceOf(RealtyPaperApi.BuyResult.PaymentFailed.class, result);
            Assertions.assertEquals("Treasury is down", failed.error());
            verify(economyProvider).transfer(eq(TITLE_HOLDER), eq(BUYER), eq(970.0),
                    eq("Plot Purchase reversal: " + REGION_ID), eq(BUYER_ID));
            verify(realtyApi).rollbackBuy(REGION_ID, WORLD_ID, BUYER_ID, reserved);
            verify(realtyApi, never()).recordBuyFees(any(), any(), any(), any(), any());
        }
    
        @Test
        void payBid_oldRulesSaleEndedWhileTheMoneyMoved_saysAnAdminWillSettleIt() {
            when(realtyApi.payBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0)).thenReturn(
                    new RealtyBackend.PayBidResult.Success(300, 700, GOVERNMENT, TITLE_HOLDER_ID, true));
            when(economyProvider.getBalance(BUYER)).thenReturn(5000.0);
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(TITLE_HOLDER), eq(300.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(realtyApi.confirmPayBid(REGION_ID, WORLD_ID, BUYER_ID, 300.0, true)).thenReturn(false);

            RealtyPaperApi.PayBidResult result = api.payBid(wgRegion, BUYER_ID, 300).join();

            RealtyPaperApi.PayBidResult.PaymentFailed failed =
                    Assertions.assertInstanceOf(RealtyPaperApi.PayBidResult.PaymentFailed.class, result);
            Assertions.assertTrue(failed.error().contains("seller") && !failed.error().contains("refunded"),
                    failed.error());
        }

        @Test
        void buySuccess_resultCarriesFees() {
            reservedWith(BUY_QUOTE);
            when(economyProvider.getBalance(BUYER)).thenReturn(1050.0);
            when(economyProvider.transfer(eq(BUYER), any(), anyDouble(), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());

            RealtyPaperApi.BuyResult.Success success = Assertions.assertInstanceOf(
                    RealtyPaperApi.BuyResult.Success.class, api.buy(wgRegion, BUYER_CTX, false).join());

            Assertions.assertEquals(BUY_QUOTE, success.fees());
        }

        @Test
        void payBidAndOfferFullyPaid_carryFeesToResultAndEvent() {
            lenient().when(events.fireSync(any(RealtyRegionEvent.class))).thenReturn(true);
            paidInFull(1000.0, AGENT_QUOTE, false);
            when(economyProvider.transfer(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            when(economyProvider.transferAuctionProceeds(eq(BUYER), eq(HOLDING), eq(1000.0), any(), eq(BUYER_ID)))
                    .thenReturn(new PaymentResult.Success());
            payoutsDue();
            when(realtyApi.payOffer(REGION_ID, WORLD_ID, BUYER_ID, 1000.0)).thenReturn(
                    new RealtyBackend.PayOfferResult.FullyPaid(GOVERNMENT, TITLE_HOLDER_ID, AGENT_QUOTE, false, 4));
            when(realtyApi.finalizeOfferPurchase(REGION_ID, WORLD_ID, BUYER_ID)).thenReturn(true);

            RealtyPaperApi.PayBidResult.FullyPaid bid = Assertions.assertInstanceOf(
                    RealtyPaperApi.PayBidResult.FullyPaid.class, api.payBid(wgRegion, BUYER_ID, 1000).join());
            RealtyPaperApi.PayOfferResult.FullyPaid offer = Assertions.assertInstanceOf(
                    RealtyPaperApi.PayOfferResult.FullyPaid.class, api.payOffer(wgRegion, BUYER_ID, 1000).join());

            Assertions.assertEquals(AGENT_QUOTE, bid.fees());
            Assertions.assertEquals(AGENT_QUOTE, offer.fees());
            ArgumentCaptor<RealtyRegionEvent> captor = ArgumentCaptor.forClass(RealtyRegionEvent.class);
            verify(events, org.mockito.Mockito.atLeastOnce()).fireSync(captor.capture());
            Assertions.assertEquals(AGENT_QUOTE, captor.getAllValues().stream()
                    .filter(AuctionWonPurchaseEvent.class::isInstance)
                    .map(AuctionWonPurchaseEvent.class::cast).findFirst().orElseThrow().getFees());
            Assertions.assertEquals(AGENT_QUOTE, captor.getAllValues().stream()
                    .filter(OfferPurchaseCompletedEvent.class::isInstance)
                    .map(OfferPurchaseCompletedEvent.class::cast).findFirst().orElseThrow().getFees());
        }
    }

    // ═══════════════════════════════════════════════════
    // fee and levy methods and events
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("fees and levies")
    class FeesAndLevies {

        private final Party.Personal recipient = Party.personal(BUYER_ID);

        @BeforeEach
        void regionIsKnownToWorldGuard() {
            RegionManager regionManager = org.mockito.Mockito.mock(RegionManager.class);
            lenient().when(regionManager.getRegion(REGION_ID)).thenReturn(protectedRegion);
            lenient().when(regionContainer.get(any())).thenReturn(regionManager);
            lenient().when(server.getWorld(WORLD_ID)).thenReturn(world);
            lenient().when(events.fireSync(any(RealtyRegionEvent.class))).thenReturn(true);
            lenient().when(events.fireSync(any(LevyChangeEvent.class))).thenReturn(true);
        }

        private List<RealtyRegionEvent> firedRegionEvents() {
            ArgumentCaptor<RealtyRegionEvent> captor = ArgumentCaptor.forClass(RealtyRegionEvent.class);
            verify(events, org.mockito.Mockito.atLeastOnce()).fireSync(captor.capture());
            return captor.getAllValues();
        }

        @Test
        void setFee_cancelledByListener_writesNothing() {
            when(events.fireSync(any(FeeChangeEvent.class))).thenReturn(false);

            Assertions.assertInstanceOf(RealtyBackend.SetFeeResult.Cancelled.class,
                    api.setFee(wgRegion, BUYER_CTX, 3, FeeSide.SELLER, false).join());

            verify(realtyApi, never()).setFee(any(), any(), any(), anyDouble(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        void setFee_firesChangedWithPrevious() {
            when(realtyApi.setFee(REGION_ID, WORLD_ID, BUYER_CTX, 3, FeeSide.SELLER, false))
                    .thenReturn(new RealtyBackend.SetFeeResult.Success(recipient, false, 2.0));

            api.setFee(wgRegion, BUYER_CTX, 3, FeeSide.SELLER, false).join();

            List<RealtyRegionEvent> fired = firedRegionEvents();
            FeeChangeEvent change = Assertions.assertInstanceOf(FeeChangeEvent.class, fired.get(0));
            Assertions.assertEquals(Optional.of(recipient), change.getRecipient());
            Assertions.assertEquals(Optional.of(BUYER_ID), change.getActorId());
            FeeChangedEvent changed = Assertions.assertInstanceOf(FeeChangedEvent.class, fired.get(1));
            Assertions.assertEquals(recipient, changed.getRecipient());
            Assertions.assertEquals(FeeSide.SELLER, changed.getSide());
            Assertions.assertEquals(3, changed.getPercent(), 0.0001);
            Assertions.assertEquals(2.0, changed.getPreviousPercent().orElseThrow(), 0.0001);
        }

        @Test
        void setFee_noChange_firesNoChangedEvent() {
            when(realtyApi.setFee(REGION_ID, WORLD_ID, BUYER_CTX, 3, FeeSide.SELLER, false))
                    .thenReturn(new RealtyBackend.SetFeeResult.NoChange());

            api.setFee(wgRegion, BUYER_CTX, 3, FeeSide.SELLER, false).join();

            Assertions.assertEquals(1, firedRegionEvents().size());
        }

        @Test
        void removeFee_firesRemoved() {
            when(realtyApi.removeFee(REGION_ID, WORLD_ID, BUYER_CTX, false))
                    .thenReturn(new RealtyBackend.RemoveFeeResult.Success());

            api.removeFee(wgRegion, BUYER_CTX, false).join();

            FeeRemovedEvent removed = Assertions.assertInstanceOf(FeeRemovedEvent.class,
                    firedRegionEvents().getFirst());
            Assertions.assertEquals(Optional.of(recipient), removed.getRecipient());
        }

        @Test
        void approveFee_firesApproved() {
            when(realtyApi.approveFee(REGION_ID, WORLD_ID, BUYER_CTX, recipient))
                    .thenReturn(new RealtyBackend.ResolveFeeResult.Success(FeeSide.SELLER, 3));

            api.approveFee(wgRegion, BUYER_CTX, recipient).join();

            FeeApprovedEvent approved = Assertions.assertInstanceOf(FeeApprovedEvent.class,
                    firedRegionEvents().getFirst());
            Assertions.assertEquals(recipient, approved.getRecipient());
            Assertions.assertEquals(FeeSide.SELLER, approved.getSide());
            Assertions.assertEquals(3, approved.getPercent(), 0.0001);
            Assertions.assertEquals(Optional.of(BUYER_ID), approved.getApproverId());
        }

        @Test
        void rejectFee_firesRejected() {
            when(realtyApi.rejectFee(REGION_ID, WORLD_ID, BUYER_CTX, recipient))
                    .thenReturn(new RealtyBackend.ResolveFeeResult.Success(FeeSide.SELLER, 3));

            api.rejectFee(wgRegion, BUYER_CTX, recipient).join();

            FeeRejectedEvent rejected = Assertions.assertInstanceOf(FeeRejectedEvent.class,
                    firedRegionEvents().getFirst());
            Assertions.assertEquals(Optional.of(BUYER_ID), rejected.getApproverId());
        }

        @Test
        void approveFee_failure_firesNothing() {
            when(realtyApi.approveFee(REGION_ID, WORLD_ID, BUYER_CTX, recipient))
                    .thenReturn(new RealtyBackend.ResolveFeeResult.NoPendingFee());

            api.approveFee(wgRegion, BUYER_CTX, recipient).join();

            verify(events, never()).fireSync(any(RealtyRegionEvent.class));
        }

        @Test
        void consoleApproveAndRemove_stillFireWithNoActor() {
            ActorContext console = ActorContext.console();
            when(realtyApi.approveFee(REGION_ID, WORLD_ID, console, recipient))
                    .thenReturn(new RealtyBackend.ResolveFeeResult.Success(FeeSide.SELLER, 3));
            when(realtyApi.removeFee(REGION_ID, WORLD_ID, console, false))
                    .thenReturn(new RealtyBackend.RemoveFeeResult.Success());

            api.approveFee(wgRegion, console, recipient).join();
            api.removeFee(wgRegion, console, false).join();

            List<RealtyRegionEvent> fired = firedRegionEvents();
            Assertions.assertTrue(Assertions.assertInstanceOf(FeeApprovedEvent.class, fired.get(0))
                    .getApproverId().isEmpty());
            Assertions.assertTrue(Assertions.assertInstanceOf(FeeRemovedEvent.class, fired.get(1))
                    .getRecipient().isEmpty());
        }

        @Test
        void setFee_consolePersonal_stillFiresChangeEventAndVetoHolds() {
            ActorContext console = ActorContext.console();
            when(events.fireSync(any(FeeChangeEvent.class))).thenReturn(false);

            Assertions.assertInstanceOf(RealtyBackend.SetFeeResult.Cancelled.class,
                    api.setFee(wgRegion, console, 3, FeeSide.SELLER, false).join());

            ArgumentCaptor<FeeChangeEvent> pre = ArgumentCaptor.forClass(FeeChangeEvent.class);
            verify(events).fireSync(pre.capture());
            Assertions.assertTrue(pre.getValue().getRecipient().isEmpty());
            Assertions.assertTrue(pre.getValue().getActorId().isEmpty());
            verify(realtyApi, never()).setFee(any(), any(), any(), anyDouble(), any(),
                    org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        void setRegionLevy_cancelled() {
            when(events.fireSync(any(LevyChangeEvent.class))).thenReturn(false);

            Assertions.assertInstanceOf(RealtyBackend.SetLevyResult.Cancelled.class,
                    api.setRegionLevy(wgRegion, BUYER_CTX, "stamp-duty").join());

            verify(realtyApi, never()).setRegionLevy(any(), any(), any(), any());
        }

        @Test
        void setRegionLevy_firesChangedWithPrevious() {
            when(realtyApi.setRegionLevy(REGION_ID, WORLD_ID, BUYER_CTX, "stamp-duty"))
                    .thenReturn(new RealtyBackend.SetLevyResult.Success("old"));

            api.setRegionLevy(wgRegion, BUYER_CTX, "stamp-duty").join();

            ArgumentCaptor<LevyChangeEvent> pre = ArgumentCaptor.forClass(LevyChangeEvent.class);
            verify(events).fireSync(pre.capture());
            Assertions.assertEquals(REGION_ID, pre.getValue().getRegion().orElseThrow().region().getId());
            Assertions.assertTrue(pre.getValue().getAuthority().isEmpty());
            Assertions.assertEquals("stamp-duty", pre.getValue().getNewLevy().orElseThrow());
            ArgumentCaptor<LevyChangedEvent> post = ArgumentCaptor.forClass(LevyChangedEvent.class);
            verify(events).fireSync(post.capture());
            Assertions.assertEquals("old", post.getValue().getPreviousLevy().orElseThrow());
            Assertions.assertEquals("stamp-duty", post.getValue().getNewLevy().orElseThrow());
        }

        @Test
        void clearAuthorityLevy_firesChangedWithNoNewLevy() {
            when(realtyApi.clearAuthorityLevy(GOVERNMENT, BUYER_CTX))
                    .thenReturn(new RealtyBackend.SetLevyResult.Success("old"));

            api.clearAuthorityLevy(GOVERNMENT, BUYER_CTX).join();

            ArgumentCaptor<LevyChangedEvent> post = ArgumentCaptor.forClass(LevyChangedEvent.class);
            verify(events).fireSync(post.capture());
            Assertions.assertEquals(GOVERNMENT, post.getValue().getAuthority().orElseThrow());
            Assertions.assertTrue(post.getValue().getRegion().isEmpty());
            Assertions.assertTrue(post.getValue().getNewLevy().isEmpty());
            Assertions.assertEquals("old", post.getValue().getPreviousLevy().orElseThrow());
        }

        @Test
        void setAuthorityLevy_failure_firesNoChangedEvent() {
            when(realtyApi.setAuthorityLevy(GOVERNMENT, BUYER_CTX, "nope"))
                    .thenReturn(new RealtyBackend.SetLevyResult.UnknownLevy());

            api.setAuthorityLevy(GOVERNMENT, BUYER_CTX, "nope").join();

            verify(events, never()).fireSync(any(LevyChangedEvent.class));
        }

        @Test
        void saleOpen_passedThrough_firesNoChangedEvent() {
            when(realtyApi.setFee(REGION_ID, WORLD_ID, BUYER_CTX, 3, FeeSide.BUYER, false))
                    .thenReturn(new RealtyBackend.SetFeeResult.SaleOpen());
            when(realtyApi.setAuthorityLevy(GOVERNMENT, BUYER_CTX, "stamp-duty"))
                    .thenReturn(new RealtyBackend.SetLevyResult.SaleOpen(2));

            Assertions.assertInstanceOf(RealtyBackend.SetFeeResult.SaleOpen.class,
                    api.setFee(wgRegion, BUYER_CTX, 3, FeeSide.BUYER, false).join());
            Assertions.assertEquals(new RealtyBackend.SetLevyResult.SaleOpen(2),
                    api.setAuthorityLevy(GOVERNMENT, BUYER_CTX, "stamp-duty").join());

            verify(events, never()).fireSync(any(FeeChangedEvent.class));
            verify(events, never()).fireSync(any(LevyChangedEvent.class));
        }

        @Test
        void setAuthorityLevy_pinned_passedThroughWithChangedEvent() {
            when(realtyApi.setAuthorityLevy(GOVERNMENT, BUYER_CTX, "stamp-duty"))
                    .thenReturn(new RealtyBackend.SetLevyResult.Success("old", 3));

            Assertions.assertEquals(new RealtyBackend.SetLevyResult.Success("old", 3),
                    api.setAuthorityLevy(GOVERNMENT, BUYER_CTX, "stamp-duty").join());

            verify(events).fireSync(any(LevyChangedEvent.class));
        }

        @Test
        void previewAndListFees_delegate() {
            FeeQuote quote = FeeQuote.none(500);
            when(realtyApi.previewFees(REGION_ID, WORLD_ID, BUYER_ID, 500)).thenReturn(quote);
            List<RealtyBackend.FeeListing> listings = List.of(
                    new RealtyBackend.FeeListing("agent fee", recipient, FeeSide.SELLER, "3%", true, "agent"));
            when(realtyApi.listFees(REGION_ID, WORLD_ID)).thenReturn(listings);

            Assertions.assertEquals(quote, api.previewFees(wgRegion, BUYER_ID, 500).join());
            Assertions.assertEquals(listings, api.listFees(wgRegion).join());
        }
    }

    // ═══════════════════════════════════════════════════
    // deleteSubregion() / cancelSubregionDelete()
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteSubregion")
    class DeleteSubregion {

        private static final String PARENT_ID = "parent_plot";
        private static final ActorContext AS_LANDLORD = ActorContext.player(LANDLORD_ID, false);
        private final LocalDateTime effective = LocalDateTime.of(2026, 12, 1, 12, 0);
        private RegionManager regionManager;

        @BeforeEach
        void subregionOfAFreehold() throws ProtectedRegion.CircularInheritanceException {
            protectedRegion.setParent(new ProtectedCuboidRegion(PARENT_ID,
                    BlockVector3.at(0, 0, 0), BlockVector3.at(200, 200, 200)));
            lenient().when(realtyApi.getFreeholdContract(PARENT_ID, WORLD_ID))
                    .thenReturn(new FreeholdContractEntity(1, LANDLORD, LANDLORD_ID, null, false));
            regionManager = org.mockito.Mockito.mock(RegionManager.class);
            lenient().when(regionManager.getRegion(REGION_ID)).thenReturn(protectedRegion);
            lenient().when(regionContainer.get(any())).thenReturn(regionManager);
        }

        private LeaseholdContractEntity rentedLease(LocalDateTime terminationDate) {
            return new LeaseholdContractEntity(1, LANDLORD, TENANT_ID, 200.0, 604800L,
                    LocalDateTime.now().minusSeconds(1), LocalDateTime.now().plusDays(30),
                    null, null, terminationDate, null, true);
        }

        @Test
        @DisplayName("a region with no parent is not a subregion")
        void noParent() throws ProtectedRegion.CircularInheritanceException {
            protectedRegion.setParent(null);
            Assertions.assertInstanceOf(RealtyPaperApi.DeleteSubregionResult.NotSubregion.class,
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
            verify(realtyApi, never()).deleteVacantLeasehold(any(), any(), any());
        }

        @Test
        @DisplayName("a parent that is not a registered freehold is not a subregion")
        void parentNotFreehold() {
            when(realtyApi.getFreeholdContract(PARENT_ID, WORLD_ID)).thenReturn(null);
            Assertions.assertInstanceOf(RealtyPaperApi.DeleteSubregionResult.NotSubregion.class,
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
            verify(realtyApi, never()).deleteVacantLeasehold(any(), any(), any());
        }

        @Test
        @DisplayName("a vacant subregion is deleted with its WorldGuard region")
        void vacantDeleted() throws Exception {
            when(realtyApi.deleteVacantLeasehold(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.DeleteLeaseholdResult.Success());
            Assertions.assertInstanceOf(RealtyPaperApi.DeleteSubregionResult.Deleted.class,
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
            verify(regionManager).removeRegion(REGION_ID);
            verify(regionManager).save();
            verify(regionProfileService).clearAllFlags(wgRegion);
        }

        @Test
        @DisplayName("a backend refusal for a non-manager is NotAuthorized")
        void notAuthorized() {
            when(realtyApi.deleteVacantLeasehold(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.DeleteLeaseholdResult.NotAuthorized());
            Assertions.assertInstanceOf(RealtyPaperApi.DeleteSubregionResult.NotAuthorized.class,
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
            verify(regionManager, never()).removeRegion(any());
        }

        @Test
        @DisplayName("a rented subregion starts a landlord termination, then marks it")
        void rentedStartsTermination() {
            when(realtyApi.deleteVacantLeasehold(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.DeleteLeaseholdResult.Occupied(null));
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID)).thenReturn(rentedLease(null));
            when(realtyApi.terminateLease(eq(REGION_ID), eq(WORLD_ID), any(), any(), eq("landlord")))
                    .thenReturn(new RealtyBackend.TerminateLeaseholdResult.Success(TENANT_ID, LANDLORD));
            when(realtyApi.markForDeletion(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenAnswer(inv -> new RealtyBackend.MarkDeletionResult.Success(effective));

            RealtyPaperApi.DeleteSubregionResult.Scheduled scheduled = Assertions.assertInstanceOf(
                    RealtyPaperApi.DeleteSubregionResult.Scheduled.class,
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
            Assertions.assertNotNull(scheduled.termination(), "this call started the termination");
            Assertions.assertEquals(effective, scheduled.effectiveDate(), "the stored termination date");
            var order = inOrder(realtyApi);
            order.verify(realtyApi).terminateLease(eq(REGION_ID), eq(WORLD_ID), any(), any(), eq("landlord"));
            order.verify(realtyApi).markForDeletion(REGION_ID, WORLD_ID, AS_LANDLORD);
        }

        @Test
        @DisplayName("a subregion already terminating is only marked")
        void terminatingOnlyMarks() {
            when(realtyApi.deleteVacantLeasehold(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.DeleteLeaseholdResult.Occupied(effective));
            when(realtyApi.markForDeletion(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.MarkDeletionResult.Success(effective));
            Assertions.assertEquals(new RealtyPaperApi.DeleteSubregionResult.Scheduled(effective, null),
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
            verify(realtyApi, never()).terminateLease(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("a refused termination leaves no mark")
        void failedTerminationLeavesNoMark() {
            when(realtyApi.deleteVacantLeasehold(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.DeleteLeaseholdResult.Occupied(null));
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID))
                    .thenReturn(new LeaseholdContractEntity(1, Party.personal(UUID.randomUUID()), TENANT_ID,
                            200.0, 604800L, LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                            null, null, null, null, true));
            RealtyPaperApi.DeleteSubregionResult.TerminationFailed failed = Assertions.assertInstanceOf(
                    RealtyPaperApi.DeleteSubregionResult.TerminationFailed.class,
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
            Assertions.assertInstanceOf(RealtyPaperApi.TerminateResult.NotAuthorized.class, failed.reason());
            verify(realtyApi, never()).markForDeletion(any(), any(), any());
        }

        @Test
        @DisplayName("a mark refused after the termination is reported")
        void refusedMarkAfterTermination() {
            when(realtyApi.deleteVacantLeasehold(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.DeleteLeaseholdResult.Occupied(null));
            when(realtyApi.getLeaseholdContract(REGION_ID, WORLD_ID)).thenReturn(rentedLease(null));
            when(realtyApi.terminateLease(eq(REGION_ID), eq(WORLD_ID), any(), any(), eq("landlord")))
                    .thenReturn(new RealtyBackend.TerminateLeaseholdResult.Success(TENANT_ID, LANDLORD));
            when(realtyApi.markForDeletion(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.MarkDeletionResult.NotAuthorized());
            Assertions.assertInstanceOf(RealtyPaperApi.DeleteSubregionResult.TerminatedNotMarked.class,
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
        }

        @Test
        @DisplayName("a subregion already marked is reported with its date")
        void alreadyMarked() {
            when(realtyApi.deleteVacantLeasehold(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.DeleteLeaseholdResult.Occupied(effective));
            when(realtyApi.markForDeletion(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.MarkDeletionResult.AlreadyMarked(effective));
            Assertions.assertEquals(new RealtyPaperApi.DeleteSubregionResult.AlreadyScheduled(effective),
                    api.deleteSubregion(wgRegion, AS_LANDLORD).join());
        }

        @Test
        @DisplayName("cancelling maps the backend results")
        void cancelMapsResults() {
            when(realtyApi.unmarkForDeletion(REGION_ID, WORLD_ID, AS_LANDLORD))
                    .thenReturn(new RealtyBackend.UnmarkDeletionResult.Success(),
                            new RealtyBackend.UnmarkDeletionResult.NotMarked(),
                            new RealtyBackend.UnmarkDeletionResult.NotAuthorized(),
                            new RealtyBackend.UnmarkDeletionResult.NoLeaseholdContract());
            Assertions.assertInstanceOf(RealtyPaperApi.CancelSubregionDeleteResult.Success.class,
                    api.cancelSubregionDelete(wgRegion, AS_LANDLORD).join());
            Assertions.assertInstanceOf(RealtyPaperApi.CancelSubregionDeleteResult.NotScheduled.class,
                    api.cancelSubregionDelete(wgRegion, AS_LANDLORD).join());
            Assertions.assertInstanceOf(RealtyPaperApi.CancelSubregionDeleteResult.NotAuthorized.class,
                    api.cancelSubregionDelete(wgRegion, AS_LANDLORD).join());
            Assertions.assertInstanceOf(RealtyPaperApi.CancelSubregionDeleteResult.NotSubregion.class,
                    api.cancelSubregionDelete(wgRegion, AS_LANDLORD).join());
        }

        @Test
        @DisplayName("cancelling refuses a region that is not a subregion")
        void cancelNotSubregion() throws ProtectedRegion.CircularInheritanceException {
            protectedRegion.setParent(null);
            Assertions.assertInstanceOf(RealtyPaperApi.CancelSubregionDeleteResult.NotSubregion.class,
                    api.cancelSubregionDelete(wgRegion, AS_LANDLORD).join());
            verify(realtyApi, never()).unmarkForDeletion(any(), any(), any());
        }
    }
}
