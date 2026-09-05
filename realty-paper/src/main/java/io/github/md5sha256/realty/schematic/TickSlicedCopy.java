package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Copies a region into a clipboard a fixed number of blocks per tick.
 *
 * <p>The blocks are read on whatever thread the scheduler runs on -- the main thread,
 * in production -- because Paper forbids chunk access anywhere else. Slicing does not
 * move the work off the main thread; it stops the work occupying one whole tick.</p>
 *
 * <p>The source is an {@link Extent} rather than a world so that a clipboard can stand
 * in for one in tests.</p>
 */
public final class TickSlicedCopy {

    private final Extent source;
    private final BlockArrayClipboard clipboard;
    private final Iterator<BlockVector3> positions;
    private final int blocksPerTick;
    private final Consumer<Clipboard> onComplete;
    private final Consumer<String> onAborted;

    private TickScheduler.Cancellable handle;
    private boolean finished;

    private TickSlicedCopy(@NotNull Extent source,
                           @NotNull Region region,
                           int blocksPerTick,
                           @NotNull Consumer<Clipboard> onComplete,
                           @NotNull Consumer<String> onAborted) {
        this.source = source;
        this.clipboard = new BlockArrayClipboard(region);
        this.positions = region.iterator();
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.onComplete = onComplete;
        this.onAborted = onAborted;
    }

    /**
     * Begins copying. {@code onComplete} receives the filled clipboard; {@code onAborted}
     * receives a reason if the copy fails. A cancelled copy calls neither.
     */
    public static @NotNull TickSlicedCopy start(@NotNull Extent source,
                                                @NotNull Region region,
                                                int blocksPerTick,
                                                @NotNull TickScheduler scheduler,
                                                @NotNull Consumer<Clipboard> onComplete,
                                                @NotNull Consumer<String> onAborted) {
        TickSlicedCopy copy = new TickSlicedCopy(source, region, blocksPerTick, onComplete, onAborted);
        copy.handle = scheduler.repeating(copy::tick);
        return copy;
    }

    private void tick() {
        if (this.finished) {
            return;
        }
        try {
            for (int i = 0; i < this.blocksPerTick && this.positions.hasNext(); i++) {
                BlockVector3 pos = this.positions.next();
                this.clipboard.setBlock(pos, withoutPayload(this.source.getFullBlock(pos)));
            }
        } catch (RuntimeException e) {
            // BlockArrayClipboard#setBlock declares no checked exception, but a live
            // world read can still fail unchecked -- an unloaded world mid-copy, a
            // chunk that will not load. Abort and report rather than retrying every
            // tick against a source that is no longer there.
            stop();
            this.onAborted.accept(String.valueOf(e.getMessage()));
            return;
        }
        if (!this.positions.hasNext()) {
            stop();
            this.onComplete.accept(this.clipboard);
        }
    }

    /**
     * Keeps a block entity's identity and discards everything inside it.
     *
     * <p>The identity has to survive: a renderer draws a sign or a chest from its block
     * entity, and a block state alone gives it no geometry to build -- the vanilla model
     * for both is empty, existing only to name a particle texture. Capture the state
     * only and those blocks vanish from the preview entirely.</p>
     *
     * <p>The contents must not: a chest's {@code Items} and a sign's text are of no use
     * to a preview, and this schematic is served over a public, unauthenticated
     * endpoint. An allowlist rather than a denylist, so a block entity type nobody
     * anticipated cannot leak a field nobody thought to strip.</p>
     */
    private static @NotNull BaseBlock withoutPayload(@NotNull BaseBlock block) {
        if (block.getNbtReference() == null) {
            return block;
        }
        String id = block.getNbtId();
        if (id.isEmpty()) {
            return block.toBaseBlock((LazyReference<LinCompoundTag>) null);
        }
        return block.toBaseBlock(LazyReference.computed(
                LinCompoundTag.builder().putString("id", id).build()));
    }

    /** Stops the copy and delivers nothing -- a partial clipboard is never handed on. */
    public void cancel() {
        stop();
    }

    private void stop() {
        this.finished = true;
        if (this.handle != null) {
            this.handle.cancel();
        }
    }
}
