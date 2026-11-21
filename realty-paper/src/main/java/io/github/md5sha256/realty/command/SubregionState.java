package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.regions.Region;
import io.github.md5sha256.realty.command.util.ExtensionCap;
import net.kyori.adventure.text.Component;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Per-player wizard state tracked by {@link SubregionDialog} between its dialog pages. */
final class SubregionState {

    /** The count the max-renewals field starts at; a capped player's starts no higher than their cap. */
    static final int DEFAULT_MAX_RENEWALS = 3;

    Region selection;
    World world;
    UUID worldId;
    final List<String> parentCandidates = new ArrayList<>();
    String parentId;
    String name = "";
    String price = "100";
    String durationAmount = "30";
    String durationUnit = DurationUnit.DAYS.name();
    // The player's extension cap, read when the dialog opens; it decides whether the unlimited
    // toggle is shown.
    ExtensionCap extensionCap;
    boolean unlimitedRenewals = true;
    String maxRenewals = String.valueOf(DEFAULT_MAX_RENEWALS);
    final List<String> permittedTagIds = new ArrayList<>();
    final Set<String> selectedTags = new LinkedHashSet<>();
    // Shown at the top of the details dialog when validation fails, so the message isn't hidden
    // behind the reopened dialog. Cleared once rendered.
    Component error;
}
