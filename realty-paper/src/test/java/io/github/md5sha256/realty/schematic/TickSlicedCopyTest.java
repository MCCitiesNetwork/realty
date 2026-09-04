package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinTagType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class TickSlicedCopyTest {

    @BeforeAll
    static void bootWorldEdit() {
        WorldEditTestPlatform.ensureRegistered();
    }

    /** Runs queued tasks only when the test says so, so nothing sleeps. */
    private static final class FakeScheduler implements TickScheduler {
        private final List<Runnable> tasks = new ArrayList<>();
        private boolean cancelled;

        @Override
        public TickScheduler.Cancellable repeating(Runnable task) {
            this.tasks.add(task);
            return () -> {
                this.cancelled = true;
                this.tasks.clear();
            };
        }

        void runTicks(int count) {
            for (int i = 0; i < count && !this.tasks.isEmpty(); i++) {
                new ArrayList<>(this.tasks).forEach(Runnable::run);
            }
        }

        boolean cancelled() {
            return this.cancelled;
        }

        boolean idle() {
            return this.tasks.isEmpty();
        }
    }

    /** A 3x3x3 source (27 blocks), stone throughout. */
    private static BlockArrayClipboard source() throws Exception {
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(2, 2, 2));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        for (BlockVector3 pos : region) {
            clipboard.setBlock(pos, BlockTypes.STONE.getDefaultState());
        }
        return clipboard;
    }

    @Test
    void aCopyLargerThanTheBudgetSpansMultipleTicks() throws Exception {
        BlockArrayClipboard src = source();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicReference<Clipboard> done = new AtomicReference<>();

        TickSlicedCopy.start(src, src.getRegion(), 10, scheduler, done::set, reason -> { });

        scheduler.runTicks(1);
        Assertions.assertNull(done.get(), "27 blocks at 10/tick must not finish in one tick");

        scheduler.runTicks(2);
        Assertions.assertNotNull(done.get(), "three ticks at 10/tick covers 27 blocks");
    }

    @Test
    void theCopiedClipboardMatchesTheSource() throws Exception {
        BlockArrayClipboard src = source();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicReference<Clipboard> done = new AtomicReference<>();

        TickSlicedCopy.start(src, src.getRegion(), 10, scheduler, done::set, reason -> { });
        scheduler.runTicks(5);

        Clipboard result = done.get();
        Assertions.assertNotNull(result);
        for (BlockVector3 pos : src.getRegion()) {
            Assertions.assertEquals(BlockTypes.STONE, result.getBlock(pos).getBlockType(),
                    "block mismatch at " + pos);
        }
    }

    @Test
    void aBudgetCoveringEverythingFinishesInOneTick() throws Exception {
        BlockArrayClipboard src = source();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicReference<Clipboard> done = new AtomicReference<>();

        TickSlicedCopy.start(src, src.getRegion(), 1000, scheduler, done::set, reason -> { });
        scheduler.runTicks(1);

        Assertions.assertNotNull(done.get());
    }

    @Test
    void theTaskStopsReschedulingOnceComplete() throws Exception {
        BlockArrayClipboard src = source();
        FakeScheduler scheduler = new FakeScheduler();

        TickSlicedCopy.start(src, src.getRegion(), 1000, scheduler, clipboard -> { }, reason -> { });
        scheduler.runTicks(1);

        Assertions.assertTrue(scheduler.idle(), "a finished copy must cancel its own task");
    }

    @Test
    void cancellingPartWayCompletesNothing() throws Exception {
        BlockArrayClipboard src = source();
        FakeScheduler scheduler = new FakeScheduler();
        AtomicReference<Clipboard> done = new AtomicReference<>();
        AtomicReference<String> aborted = new AtomicReference<>();

        TickSlicedCopy copy =
                TickSlicedCopy.start(src, src.getRegion(), 5, scheduler, done::set, aborted::set);
        scheduler.runTicks(1);
        copy.cancel();
        scheduler.runTicks(10);

        Assertions.assertNull(done.get(), "a cancelled copy must not deliver a clipboard");
        Assertions.assertTrue(scheduler.cancelled());
    }

    @Test
    void keepsABlockEntityButDropsItsContents() throws Exception {
        // A sign or chest needs its block entity to render at all -- the vanilla block
        // model for both is empty. Its contents, though, are neither useful to a
        // preview nor safe on a public endpoint.
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.ZERO);
        BlockArrayClipboard src = new BlockArrayClipboard(region);
        src.setBlock(BlockVector3.ZERO, BlockTypes.CHEST.getDefaultState().toBaseBlock(
                LazyReference.computed(LinCompoundTag.builder()
                        .putString("id", "minecraft:chest")
                        .putString("CustomName", "Treasure")
                        .build())));

        FakeScheduler scheduler = new FakeScheduler();
        AtomicReference<Clipboard> done = new AtomicReference<>();
        TickSlicedCopy.start(src, region, 10, scheduler, done::set, reason -> { });
        scheduler.runTicks(2);

        Clipboard result = done.get();
        Assertions.assertNotNull(result);
        BaseBlock copied = result.getFullBlock(BlockVector3.ZERO);
        LinCompoundTag nbt = copied.getNbt();
        Assertions.assertNotNull(nbt, "the block entity itself must survive");
        Assertions.assertEquals("minecraft:chest", nbt.getTag("id", LinTagType.stringTag()).value());
        Assertions.assertNull(nbt.findTag("CustomName", LinTagType.stringTag()),
                "block entity contents must not be copied");
    }
}
