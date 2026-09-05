package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.platform.PlatformsRegisteredEvent;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.extension.platform.Preference;
import java.nio.file.Path;
import com.sk89q.worldedit.world.DataFixer;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.registry.BundledRegistries;
import com.sk89q.worldedit.world.registry.Registries;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.Map;

/**
 * Boots just enough of WorldEdit for its block registry to answer.
 *
 * <p>{@code BlockTypes.STONE} and friends read through a registry that refuses to
 * answer until a platform is registered ("WorldEdit is not initialized yet"), so any
 * test touching a real block needs one. WorldEdit ships bundled block data for
 * exactly this case, so the stub platform hands back {@link BundledRegistries} and
 * no server is needed.</p>
 */
final class WorldEditTestPlatform {

    private static boolean registered;

    private WorldEditTestPlatform() {
    }

    /** Idempotent: the platform is global to the JVM, so it is registered once. */
    static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        Platform platform = Mockito.mock(Platform.class, Mockito.RETURNS_DEEP_STUBS);

        Map<Capability, Preference> capabilities = new EnumMap<>(Capability.class);
        for (Capability capability : Capability.values()) {
            capabilities.put(capability, Preference.PREFERRED);
        }

        Mockito.when(platform.getCapabilities()).thenReturn(capabilities);
        Mockito.when(platform.getRegistries()).thenReturn((Registries) BundledRegistries.getInstance());
        // The bundled block data is read through ResourceLoader's default methods,
        // which go via the classloader and already work here. Only the local-file
        // lookup is abstract, and nothing in these tests reads a local resource.
        Mockito.when(platform.getResourceLoader()).thenReturn(Path::of);
        Mockito.when(platform.getDataVersion()).thenReturn(-1);
        Mockito.when(platform.getDataFixer()).thenReturn((DataFixer) null);
        Mockito.when(platform.getConfiguration()).thenReturn(new LocalConfiguration() {
            @Override
            public void load() {
            }
        });
        Mockito.when(platform.getVersion()).thenReturn("test");
        Mockito.when(platform.getPlatformName()).thenReturn("test");
        Mockito.when(platform.getPlatformVersion()).thenReturn("test");
        Mockito.when(platform.getId()).thenReturn("realty:test");

        WorldEdit worldEdit = WorldEdit.getInstance();
        worldEdit.getPlatformManager().register(platform);
        worldEdit.getEventBus().post(new PlatformsRegisteredEvent());

        // A real platform's adapter registers every Minecraft block on load. Nothing
        // does that here, so the registry would stay empty and BlockTypes.STONE null.
        // Only the blocks these tests place need to exist; their property sets come
        // from the bundled data the stub platform exposes above.
        for (String id : new String[]{"minecraft:air", "minecraft:stone", "minecraft:dirt",
                "minecraft:chest"}) {
            if (BlockType.REGISTRY.get(id) == null) {
                BlockType.REGISTRY.register(id, new BlockType(id));
            }
        }
        registered = true;
    }
}
