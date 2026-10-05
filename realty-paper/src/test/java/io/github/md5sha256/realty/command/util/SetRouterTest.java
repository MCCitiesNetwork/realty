package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.command.util.SetRouter.HolderTest;
import io.github.md5sha256.realty.command.util.SetRouting.Kind;
import io.github.md5sha256.realty.command.util.SetRouting.Outcome;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SetRouterTest {

    private static final String REGION_ID = "test_region";
    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final Party ACCOUNT = new Party.Account(7, io.github.md5sha256.realty.api.AccountKind.BUSINESS);
    private static final String OTHERS = "realty.command.set.price.others";
    private static final String NOW_NODE = "realty.command.set.now";

    @Mock
    private RealtyPaperApi api;
    @Mock
    private ActorContexts actors;
    @Mock
    private MessageContainer messages;
    @Mock
    private Player player;
    @Mock
    private CommandSender console;
    @Mock
    private ProtectedRegion protectedRegion;
    @Mock
    private World world;

    private SetRouter router;
    private WorldGuardRegion region;
    private final List<SetRouter.Routed> routed = new ArrayList<>();
    private final List<LogRecord> logged = new ArrayList<>();

    @BeforeEach
    void setUp() {
        router = routerOn(Runnable::run);
        lenient().when(protectedRegion.getId()).thenReturn(REGION_ID);
        lenient().when(world.getUID()).thenReturn(WORLD_ID);
        region = new WorldGuardRegion(protectedRegion, world);
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_ID);
        lenient().when(actors.forRegion(any(), anyBoolean(), any(), any(Party[].class)))
                .thenReturn(ActorContext.player(PLAYER_ID, false));
        givenFreehold(null);
        givenLease(null);
    }

    private SetRouter routerOn(Executor mainThread) {
        ExecutorService db = mock(ExecutorService.class);
        lenient().doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(db).execute(any(Runnable.class));
        Logger logger = Logger.getLogger("SetRouterTest-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return new SetRouter(api, actors, messages,
                new ExecutorState(mainThread, db, mock(ExecutorService.class)), logger);
    }

    private static LeaseholdContractEntity lease(Party landlord, UUID tenant) {
        return new LeaseholdContractEntity(1, landlord, tenant, 100.0, 3600L, null, null,
                null, null, null, null, true);
    }

    private static FreeholdContractEntity freehold(UUID titleHolder) {
        return new FreeholdContractEntity(1, Party.personal(OTHER_ID), titleHolder, null, false);
    }

    private void givenLease(LeaseholdContractEntity lease) {
        lenient().when(api.getLeaseholdContract(REGION_ID, WORLD_ID))
                .thenReturn(CompletableFuture.completedFuture(lease));
    }

    private void givenFreehold(FreeholdContractEntity freehold) {
        lenient().when(api.getFreeholdContract(REGION_ID, WORLD_ID))
                .thenReturn(CompletableFuture.completedFuture(freehold));
    }

    private void route(CommandSender sender, boolean now) {
        route(sender, Kind.TERM, HolderTest.MANAGES, MessageKeys.SET_NO_PERMISSION, now, false);
    }

    private void route(CommandSender sender, Kind kind, HolderTest test, String noPermissionKey,
                       boolean now, boolean unlimited) {
        router.route(sender, region, kind, test, OTHERS, noPermissionKey, now, unlimited, routed::add);
    }

    private void verifySent(String key) {
        verify(messages).messageFor(eq(key), any(TagResolver.class));
    }

    @Test
    void landlordOnVacantLeaseIsRoutedToApplyNow() {
        givenLease(lease(Party.personal(PLAYER_ID), null));
        route(player, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(true), routed.get(0).outcome());
        assertEquals(ActorContext.player(PLAYER_ID, false), routed.get(0).actor());
        assertTrue(routed.get(0).vacantOnly());
    }

    @Test
    void memberOfAnAccountLandlordIsRouted() {
        givenLease(lease(ACCOUNT, null));
        ActorContext member = new ActorContext(PLAYER_ID, Set.of(ACCOUNT), Set.of(), false);
        lenient().when(actors.forRegion(any(), anyBoolean(), any(), any(Party[].class))).thenReturn(member);
        route(player, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(true), routed.get(0).outcome());
        assertSame(member, routed.get(0).actor());
    }

    @Test
    void adminOnRentedLeaseIsRoutedToSchedule() {
        givenLease(lease(Party.personal(OTHER_ID), UUID.randomUUID()));
        lenient().when(player.hasPermission(OTHERS)).thenReturn(true);
        ActorContext admin = ActorContext.player(PLAYER_ID, true);
        lenient().when(actors.forRegion(any(), eq(true), any(), any(Party[].class))).thenReturn(admin);
        route(player, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.Schedule(), routed.get(0).outcome());
        assertSame(admin, routed.get(0).actor());
    }

    @Test
    void tenantOnRentedLeaseIsRoutedToRequest() {
        LeaseholdContractEntity rented = lease(Party.personal(OTHER_ID), PLAYER_ID);
        givenLease(rented);
        route(player, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.Request(), routed.get(0).outcome());
        assertEquals(rented, routed.get(0).lease());
    }

    @Test
    void consoleOnRentedLeaseWithNowAppliesUnguarded() {
        givenLease(lease(Party.personal(OTHER_ID), UUID.randomUUID()));
        route(console, true);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(false), routed.get(0).outcome());
        assertFalse(routed.get(0).vacantOnly());
        assertSame(ActorContext.console(), routed.get(0).actor());
    }

    @Test
    void consoleOnAnAccountsRentedLeaseIsToldToUseNow() {
        givenLease(lease(ACCOUNT, UUID.randomUUID()));
        route(console, false);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.SET_CONSOLE_NEEDS_NOW);
    }

    @Test
    void consoleOnAPlayersRentedLeaseSchedules() {
        givenLease(lease(Party.personal(OTHER_ID), UUID.randomUUID()));
        route(console, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.Schedule(), routed.get(0).outcome());
    }

    @Test
    void strangerOnLeaseGetsNotLandlordAndNoCallback() {
        givenLease(lease(Party.personal(OTHER_ID), null));
        route(player, false);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.SET_NOT_LANDLORD);
    }

    @Test
    void strangerOnFreeholdGetsTheSuppliedNoPermissionKey() {
        givenFreehold(freehold(OTHER_ID));
        route(player, Kind.TERM, HolderTest.MANAGES, MessageKeys.UNSET_NO_PERMISSION, false, false);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.UNSET_NO_PERMISSION);
        verify(messages, never()).messageFor(eq(MessageKeys.SET_NOT_LANDLORD), any(TagResolver.class));
    }

    @Test
    void titleHolderOnFreeholdIsRoutedToApplyNow() {
        givenFreehold(freehold(PLAYER_ID));
        route(player, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(true), routed.get(0).outcome());
    }

    @Test
    void reassignTestUsesMayReassign() {
        // Manages the account but may not hand its role on.
        givenLease(lease(ACCOUNT, null));
        lenient().when(actors.forRegion(any(), anyBoolean(), any(), any(Party[].class)))
                .thenReturn(new ActorContext(PLAYER_ID, Set.of(ACCOUNT), Set.of(), false));
        route(player, Kind.HOLDER, HolderTest.REASSIGNS, MessageKeys.SET_NO_PERMISSION, false, false);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.SET_NOT_LANDLORD);
    }

    @Test
    void reassignTestAdmitsAnActorWhoMayReassign() {
        givenLease(lease(ACCOUNT, null));
        lenient().when(actors.forRegion(any(), anyBoolean(), any(), any(Party[].class)))
                .thenReturn(new ActorContext(PLAYER_ID, Set.of(ACCOUNT), Set.of(ACCOUNT), false));
        route(player, Kind.HOLDER, HolderTest.REASSIGNS, MessageKeys.SET_NO_PERMISSION, false, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(true), routed.get(0).outcome());
    }

    @Test
    void regionWithNoContractIsRoutedToApplyNow() {
        route(player, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(true), routed.get(0).outcome());
    }

    @Test
    void extraPartiesReachTheContext() {
        givenLease(lease(Party.personal(PLAYER_ID), null));
        router.route(player, region, Kind.HOLDER, HolderTest.REASSIGNS, OTHERS, MessageKeys.SET_NO_PERMISSION,
                false, false, routed::add, ACCOUNT);
        verify(actors).forRegion(player, false, region, ACCOUNT);
    }

    @Test
    void vacantOnlyIsRefusedForAnOutcomeThatIsNotApplyNow() {
        SetRouter.Routed schedule = new SetRouter.Routed(new Outcome.Schedule(), ActorContext.console(), null);
        assertThrows(IllegalStateException.class, schedule::vacantOnly);
    }

    @Test
    void theCallbackWaitsForTheMainThread() throws Exception {
        ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
        router = routerOn(mainThreadTasks::add);
        CompletableFuture<LeaseholdContractEntity> read = new CompletableFuture<>();
        lenient().when(api.getLeaseholdContract(REGION_ID, WORLD_ID)).thenReturn(read);
        route(player, false);
        Thread db = new Thread(() -> read.complete(lease(Party.personal(PLAYER_ID), null)));
        db.start();
        db.join();
        assertEquals(1, mainThreadTasks.size());
        assertTrue(routed.isEmpty(), "the callback must not run on the completing thread");
        mainThreadTasks.poll().run();
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(true), routed.get(0).outcome());
    }

    @Test
    void aRefusalWaitsForTheMainThread() throws Exception {
        ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
        router = routerOn(mainThreadTasks::add);
        CompletableFuture<LeaseholdContractEntity> read = new CompletableFuture<>();
        lenient().when(api.getLeaseholdContract(REGION_ID, WORLD_ID)).thenReturn(read);
        route(player, false);
        Thread db = new Thread(() -> read.complete(lease(Party.personal(OTHER_ID), null)));
        db.start();
        db.join();
        verify(messages, never()).messageFor(eq(MessageKeys.SET_NOT_LANDLORD), any(TagResolver.class));
        mainThreadTasks.poll().run();
        verifySent(MessageKeys.SET_NOT_LANDLORD);
        assertTrue(mainThreadTasks.isEmpty());
    }

    @Test
    void aCallbackThatThrowsIsReportedToTheSender() {
        givenLease(lease(Party.personal(PLAYER_ID), null));
        router.route(player, region, Kind.TERM, HolderTest.MANAGES, OTHERS, MessageKeys.SET_NO_PERMISSION,
                false, false, r -> {
                    throw new IllegalStateException("boom");
                });
        verifySent(MessageKeys.SET_CHECK_PERMISSIONS_ERROR);
        assertEquals(1, logged.size());
        assertEquals(Level.SEVERE, logged.get(0).getLevel());
    }

    @Test
    void aFailedReadIsReportedToTheSender() {
        lenient().when(api.getLeaseholdContract(REGION_ID, WORLD_ID))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));
        route(player, false);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.SET_CHECK_PERMISSIONS_ERROR);
        assertEquals(1, logged.size());
        assertEquals(Level.SEVERE, logged.get(0).getLevel());
    }

    @Test
    void aFailedWriteIsReportedAndLogged() {
        CompletableFuture<String> write = CompletableFuture.failedFuture(new IllegalStateException("down"));
        router.reportWriteFailure(write, player, MessageKeys.SET_PRICE_ERROR);
        verifySent(MessageKeys.SET_PRICE_ERROR);
        assertEquals(Level.SEVERE, logged.get(0).getLevel());
    }

    private static LeaseholdContractEntity endingLease(Party landlord, UUID tenant) {
        return new LeaseholdContractEntity(1, landlord, tenant, 100.0, 3600L, null, null,
                null, null, java.time.LocalDateTime.of(2030, 1, 1, 0, 0), "LANDLORD", true);
    }

    private void holderRoute(CommandSender sender, boolean now) {
        route(sender, Kind.HOLDER, HolderTest.MANAGES, MessageKeys.UNSET_NO_PERMISSION, now, false);
    }

    /** The text a message would show for {@code tag}, taken from the resolver the router sent. */
    private String sentPlaceholder(String key, String tag) {
        ArgumentCaptor<TagResolver> captor = ArgumentCaptor.forClass(TagResolver.class);
        verify(messages).messageFor(eq(key), captor.capture());
        return PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize("<" + tag + ">", captor.getValue()));
    }

    @Test
    void tenantRunningUnsetTenantDoesNotHoldTheLease() {
        givenLease(lease(Party.personal(OTHER_ID), PLAYER_ID));
        lenient().when(player.hasPermission(NOW_NODE)).thenReturn(true);
        holderRoute(player, true);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.SET_NOT_LANDLORD);
    }

    @Test
    void landlordRunningUnsetTenantWithNowIsRouted() {
        givenLease(lease(Party.personal(PLAYER_ID), UUID.randomUUID()));
        lenient().when(player.hasPermission(NOW_NODE)).thenReturn(true);
        holderRoute(player, true);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(false), routed.get(0).outcome());
    }

    @Test
    void landlordRunningUnsetTenantWithoutNowIsToldToAddIt() {
        givenLease(lease(Party.personal(PLAYER_ID), UUID.randomUUID()));
        lenient().when(player.hasPermission(NOW_NODE)).thenReturn(true);
        holderRoute(player, false);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.SET_RENTED_NEEDS_NOW);
    }

    @Test
    void titleHolderIsRoutedOnTheirFreehold() {
        givenFreehold(freehold(PLAYER_ID));
        holderRoute(player, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(true), routed.get(0).outcome());
    }

    @Test
    void managerOfTheAuthorityIsRoutedOnAnUnsoldFreehold() {
        givenFreehold(new FreeholdContractEntity(1, ACCOUNT, null, null, false));
        lenient().when(actors.forRegion(any(), anyBoolean(), any(), any(Party[].class)))
                .thenReturn(new ActorContext(PLAYER_ID, Set.of(ACCOUNT), Set.of(), false));
        holderRoute(player, false);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(true), routed.get(0).outcome());
    }

    @Test
    void holderOnARentedLeaseWithNowAndTheNodeAppliesNow() {
        givenLease(lease(Party.personal(PLAYER_ID), UUID.randomUUID()));
        lenient().when(player.hasPermission(NOW_NODE)).thenReturn(true);
        route(player, true);
        assertEquals(1, routed.size());
        assertEquals(new Outcome.ApplyNow(false), routed.get(0).outcome());
    }

    @Test
    void holderOnARentedLeaseWithNowButNotTheNodeIsRefused() {
        givenLease(lease(Party.personal(PLAYER_ID), UUID.randomUUID()));
        route(player, true);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.SET_RENTED_NO_NOW_PERMISSION);
    }

    @Test
    void aTermChangeOnAnEndingLeaseIsRefused() {
        givenLease(endingLease(Party.personal(PLAYER_ID), UUID.randomUUID()));
        route(player, false);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.MODIFY_TERMINATING);
    }

    @Test
    void unlimitedExtensionsWithoutNowAreRefused() {
        givenLease(lease(Party.personal(PLAYER_ID), UUID.randomUUID()));
        route(player, Kind.TERM, HolderTest.MANAGES, MessageKeys.SET_NO_PERMISSION, false, true);
        assertTrue(routed.isEmpty());
        verifySent(MessageKeys.SET_UNLIMITED_NEEDS_NOW);
    }

    @Test
    void aRefusalSendsTheRegionPlaceholder() {
        givenLease(lease(Party.personal(OTHER_ID), null));
        route(player, false);
        assertEquals(REGION_ID, sentPlaceholder(MessageKeys.SET_NOT_LANDLORD, "region"));
    }

    @Test
    void aWriteFailureSendsTheErrorPlaceholder() {
        router.reportWriteFailure(CompletableFuture.failedFuture(new IllegalStateException("down")), player,
                MessageKeys.SET_TENANT_ERROR);
        assertEquals("down", sentPlaceholder(MessageKeys.SET_TENANT_ERROR, "error"));
    }
}
