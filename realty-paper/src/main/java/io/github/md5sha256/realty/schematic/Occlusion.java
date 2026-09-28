package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import org.jetbrains.annotations.NotNull;

/**
 * Whether a block hides what is behind it, asked of the server's own block data.
 */
public final class Occlusion {

    private Occlusion() {
    }

    /**
     * A full, opaque cube. Anything less -- glass, a slab, a fence -- leaves something
     * behind it visible, and the cull has to keep that something.
     *
     * <p>WorldEdit describes a block type, not a block state, and describes it by its
     * default state. A slab's default is the bottom half, so a double slab, which is a
     * full cube, would be read as half of one. It is recognised by its own state.</p>
     *
     * <p>A block with no material at all is treated as hiding nothing. The cost of that
     * guess is a block kept that need not have been; the other guess would punch a hole
     * in the preview. That is not the same as a server WorldEdit has no adapter for:
     * there every block is given a material that says full and opaque, so everything
     * hides, nothing private is kept, and the preview has holes in it.</p>
     */
    public static boolean hides(@NotNull BlockState state) {
        BlockMaterial material = state.getBlockType().getMaterial();
        if (material == null || !material.isOpaque()) {
            return false;
        }
        return material.isFullCube() || isDoubleSlab(state);
    }

    private static boolean isDoubleSlab(@NotNull BlockState state) {
        return state.getBlockType().id().endsWith("_slab") && state.getAsString().contains("type=double");
    }

    /**
     * Has WorldEdit look up every block type's material now. Main thread only.
     *
     * <p>The first lookup of a type's material writes to a map inside WorldEdit that is
     * not safe to write from two threads. {@link #hides} runs on a database thread, so
     * without this its first sight of a block type would make that write there, while the
     * server thread may be making its own. Each type keeps its answer once it has one, so
     * after this the database thread only reads. Looking up a type that is already known
     * costs nothing, so this is called before every capture and keeps no flag.</p>
     */
    public static void learnEveryMaterial() {
        for (BlockType type : BlockType.REGISTRY) {
            type.getMaterial();
        }
    }
}
