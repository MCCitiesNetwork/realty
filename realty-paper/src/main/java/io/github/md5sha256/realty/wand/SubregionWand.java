package io.github.md5sha256.realty.wand;

import io.github.md5sha256.realty.settings.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the subregion selection wand and identifies it later. The wand is a plain item tagged via
 * the {@link PersistentDataContainer} so it survives renaming/moving and can't be spoofed by name.
 */
public final class SubregionWand {

    private final NamespacedKey wandKey;
    private final AtomicReference<Settings> settings;

    public SubregionWand(@NotNull Plugin plugin, @NotNull AtomicReference<Settings> settings) {
        this.wandKey = new NamespacedKey(plugin, "subregion_wand");
        this.settings = settings;
    }

    /**
     * Creates a fresh wand item using the configured material.
     */
    public @NotNull ItemStack createWand() {
        Material material = resolveMaterial(settings.get().subregionWandMaterial());
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Subregion Wand", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Right-click: place a corner", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Left-click: undo last corner", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Clear all: /realty subregion clear", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Finish: /realty subregion confirm", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /**
     * Returns {@code true} if the given item is a subregion wand (by PDC tag, not by name).
     */
    public boolean isWand(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(wandKey, PersistentDataType.BYTE);
        return tag != null;
    }

    private static @NotNull Material resolveMaterial(@NotNull String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            return Material.GOLDEN_AXE;
        }
        return material;
    }
}
