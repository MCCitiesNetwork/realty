package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.SubregionSelectionValidator;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.mapper.RegionTagMapper;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.ConfigRegionTag;
import io.github.md5sha256.realty.settings.RealtyTags;
import io.github.md5sha256.realty.settings.Settings;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import io.github.md5sha256.realty.wand.SubregionWandManager;

/**
 * Guided two-page dialog for creating a subregion from the player's wand selection.
 *
 * <p>Page 1 collects the parent freehold (auto-detected from the selection), name, price and
 * duration; page 2 is a plain-English confirmation. Submit calls
 * {@link RealtyPaperApi#quickCreateSubregion}. Modelled on {@code SearchDialog}.</p>
 */
public final class SubregionDialog {

    static final String BYPASS_PERMISSION = "realty.command.subregion.confirm.bypass";
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");

    private static final String INPUT_PARENT = "parent";
    private static final String INPUT_NAME = "name";
    private static final String INPUT_PRICE = "price";
    private static final String INPUT_DURATION_AMOUNT = "duration_amount";
    private static final String INPUT_DURATION_UNIT = "duration_unit";
    private static final String INPUT_UNLIMITED_RENEWALS = "unlimited_renewals";
    private static final String INPUT_MAX_RENEWALS = "max_renewals";
    private static final String INPUT_HEIGHT = "height";
    private static final int DEFAULT_HEIGHT = 16;
    private static final int FORM_INPUT_WIDTH = 200;
    /** Stored as the lease's max renewals to mean "no cap"; the backend maps any negative to NULL. */
    private static final int UNLIMITED_RENEWALS = -1;
    private static final ClickCallback.Options CLICK_OPTIONS =
            ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();

    /** Lease-duration units offered in the create dialog. */
    enum DurationUnit {
        MINUTES(60L, "Minutes"),
        HOURS(3600L, "Hours"),
        DAYS(86400L, "Days"),
        WEEKS(604800L, "Weeks");

        final long seconds;
        final String label;

        DurationUnit(long seconds, String label) {
            this.seconds = seconds;
            this.label = label;
        }
    }

    private static final String TAG_INPUT_PREFIX = "tag_";

    private final RealtyPaperApi api;
    private final ExecutorState executorState;
    private final Database database;
    private final SubregionWandManager wandManager;
    private final AtomicReference<Settings> settings;
    private final AtomicReference<RealtyTags> realtyTags;
    private final MessageContainer messages;
    private final ConcurrentHashMap<UUID, SubregionState> playerStates = new ConcurrentHashMap<>();

    static final class SubregionState {
        Region selection;
        World world;
        UUID worldId;
        final List<String> parentCandidates = new ArrayList<>();
        String parentId;
        String name = "";
        String price = "100";
        String durationAmount = "30";
        String durationUnit = DurationUnit.DAYS.name();
        boolean unlimitedRenewals = true;
        String maxRenewals = "3";
        final List<String> permittedTagIds = new ArrayList<>();
        final Set<String> selectedTags = new LinkedHashSet<>();
    }

    public SubregionDialog(@NotNull RealtyPaperApi api,
                           @NotNull ExecutorState executorState,
                           @NotNull Database database,
                           @NotNull SubregionWandManager wandManager,
                           @NotNull AtomicReference<Settings> settings,
                           @NotNull AtomicReference<RealtyTags> realtyTags,
                           @NotNull MessageContainer messages) {
        this.api = api;
        this.executorState = executorState;
        this.database = database;
        this.wandManager = wandManager;
        this.settings = settings;
        this.realtyTags = realtyTags;
        this.messages = messages;
    }

    /**
     * Opens the creation dialog for the player, auto-detecting candidate parent freeholds from the
     * current wand selection.
     */
    public void open(@NotNull Player player) {
        SubregionWandManager.WandSelection wandSelection = wandManager.get(player.getUniqueId());
        if (wandSelection == null || !wandSelection.isComplete()) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_SELECTION_INCOMPLETE));
            return;
        }
        if (!wandSelection.heightSet()) {
            // No vertical span chosen yet — collect it first, then this dialog reopens.
            showHeightDialog(player, wandSelection);
            return;
        }
        Region selection = wandSelection.toRegion();
        World world = wandSelection.world();

        int minVolume = settings.get().subregionMinVolume();
        if (selection.getVolume() < minVolume) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_TOO_SMALL,
                    Placeholder.unparsed("volume", String.valueOf(selection.getVolume())),
                    Placeholder.unparsed("min-volume", String.valueOf(minVolume))));
            return;
        }

        RegionManager regionManager = regionManager(world);
        if (regionManager == null) {
            player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                    Placeholder.unparsed("error", "Region manager unavailable")));
            return;
        }

        boolean canBypass = player.hasPermission(BYPASS_PERMISSION);
        List<ProtectedRegion> geometricCandidates = SubregionSelectionValidator.candidateParents(
                player.getUniqueId(), canBypass, selection, regionManager);
        if (geometricCandidates.isEmpty()) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_NO_PARENT_CANDIDATES));
            return;
        }

        UUID worldId = world.getUID();
        Collection<String> blacklist = settings.get().subregionTagBlacklist();

        // Filter to actual freeholds that aren't tag-blacklisted, without blocking a worker thread.
        List<CompletableFuture<String>> checks = new ArrayList<>();
        for (ProtectedRegion candidate : geometricCandidates) {
            String id = candidate.getId();
            CompletableFuture<FreeholdContractEntity> freehold = api.getFreeholdContract(id, worldId);
            CompletableFuture<List<String>> tags = blacklist.isEmpty()
                    ? CompletableFuture.completedFuture(List.of())
                    : api.getTagIdsByRegion(id);
            checks.add(freehold.thenCombine(tags, (contract, tagList) -> {
                if (contract == null) {
                    return null;
                }
                for (String tag : tagList) {
                    if (blacklist.contains(tag)) {
                        return null;
                    }
                }
                return id;
            }));
        }

        CompletableFuture.allOf(checks.toArray(new CompletableFuture[0]))
                .thenAcceptAsync(ignored -> {
                    SubregionState state = new SubregionState();
                    state.selection = selection;
                    state.world = world;
                    state.worldId = worldId;
                    for (CompletableFuture<String> check : checks) {
                        String id = check.join();
                        if (id != null) {
                            state.parentCandidates.add(id);
                        }
                    }
                    if (state.parentCandidates.isEmpty()) {
                        player.sendMessage(messages.messageFor(
                                MessageKeys.SUBREGION_NO_PARENT_CANDIDATES));
                        return;
                    }
                    state.parentId = state.parentCandidates.get(0);
                    for (ConfigRegionTag tag : realtyTags.get().values()) {
                        if (tag.permission() == null
                                || player.hasPermission(tag.permission().node())) {
                            state.permittedTagIds.add(tag.tagId());
                        }
                    }
                    playerStates.put(player.getUniqueId(), state);
                    showCreateDialog(player, state);
                }, executorState.mainThreadExec())
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CREATE_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    /**
     * Opens the height dialog for the player's current footprint selection.
     */
    public void openHeight(@NotNull Player player) {
        SubregionWandManager.WandSelection selection = wandManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_SELECTION_INCOMPLETE));
            return;
        }
        showHeightDialog(player, selection);
    }

    private void showHeightDialog(@NotNull Player player,
                                  @NotNull SubregionWandManager.WandSelection selection) {
        World world = selection.world();
        // Floor is the lowest corner the player marked; the slider only sets how tall it is.
        int baseFloor = selection.minPointY();
        int worldMax = world.getMaxHeight() - 1;
        int maxHeight = Math.max(1, worldMax - baseFloor + 1);

        int currentHeight = selection.heightSet()
                ? selection.ceilingY() - selection.floorY() + 1
                : DEFAULT_HEIGHT;
        currentHeight = Math.max(1, Math.min(maxHeight, currentHeight));

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.numberRange(INPUT_HEIGHT, Component.text("Height"),
                        1f, (float) maxHeight)
                .width(250)
                .step(1f)
                .initial((float) currentHeight)
                .labelFormat("%s: %s blocks")
                .build());

        ClickCallback.Options clickOptions = CLICK_OPTIONS;

        DialogActionCallback previewCallback = (response, audience) -> {
            saveHeight(selection, response, baseFloor, worldMax);
            // On success the dialog closes and the wand's particle outline previews the full shape.
        };
        DialogActionCallback continueCallback = (response, audience) -> {
            saveHeight(selection, response, baseFloor, worldMax);
            open(player);
        };
        DialogActionCallback clearCallback = (response, audience) -> {
            wandManager.clear(player.getUniqueId());
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_SELECTION_CLEARED));
        };

        List<ActionButton> actions = new ArrayList<>();
        actions.add(ActionButton.builder(Component.text("Preview"))
                .width(150)
                .action(DialogAction.customClick(previewCallback, clickOptions))
                .build());
        actions.add(ActionButton.builder(Component.text("Continue", NamedTextColor.GREEN))
                .width(150)
                .action(DialogAction.customClick(continueCallback, clickOptions))
                .build());
        actions.add(ActionButton.builder(Component.text("Clear", NamedTextColor.RED))
                .width(150)
                .action(DialogAction.customClick(clearCallback, clickOptions))
                .build());

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Step 1 of 3: Height"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Starts at the corners you placed. Drag to set the height."))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(actions,
                        ActionButton.builder(Component.text("Cancel")).width(150).build(),
                        1)));
        player.showDialog(dialog);
    }

    private void saveHeight(@NotNull SubregionWandManager.WandSelection selection,
                            @NotNull DialogResponseView response,
                            int baseFloor, int worldMax) {
        Float raw = response.getFloat(INPUT_HEIGHT);
        int height = raw == null ? DEFAULT_HEIGHT : Math.max(1, Math.round(raw));
        int ceiling = Math.min(worldMax, baseFloor + height - 1);
        selection.setHeight(baseFloor, ceiling);
    }

    private void showCreateDialog(@NotNull Player player, @NotNull SubregionState state) {
        List<DialogInput> inputs = new ArrayList<>();

        List<SingleOptionDialogInput.OptionEntry> parentEntries = new ArrayList<>();
        for (String id : state.parentCandidates) {
            parentEntries.add(SingleOptionDialogInput.OptionEntry.create(
                    id, Component.text(id), id.equals(state.parentId)));
        }
        inputs.add(DialogInput.singleOption(INPUT_PARENT, Component.text("Region"),
                        parentEntries)
                .width(FORM_INPUT_WIDTH).labelVisible(true).build());

        inputs.add(DialogInput.text(INPUT_NAME, Component.text("Name"))
                .width(FORM_INPUT_WIDTH).initial(state.name).maxLength(40).build());

        inputs.add(DialogInput.text(INPUT_PRICE, Component.text("Price"))
                .width(FORM_INPUT_WIDTH).initial(state.price).maxLength(15).build());

        inputs.add(DialogInput.text(INPUT_DURATION_AMOUNT, Component.text("Lease length"))
                .width(FORM_INPUT_WIDTH).initial(state.durationAmount).maxLength(9).build());

        List<SingleOptionDialogInput.OptionEntry> unitEntries = new ArrayList<>();
        for (DurationUnit unit : DurationUnit.values()) {
            unitEntries.add(SingleOptionDialogInput.OptionEntry.create(
                    unit.name(), Component.text(unit.label),
                    unit.name().equals(state.durationUnit)));
        }
        inputs.add(DialogInput.singleOption(INPUT_DURATION_UNIT, Component.text("Unit"),
                        unitEntries)
                .width(FORM_INPUT_WIDTH).labelVisible(true).build());

        inputs.add(DialogInput.bool(INPUT_UNLIMITED_RENEWALS, Component.text("Unlimited renewals"))
                .initial(state.unlimitedRenewals).onTrue("true").onFalse("false").build());
        inputs.add(DialogInput.text(INPUT_MAX_RENEWALS, Component.text("Max renewals (if limited)"))
                .width(FORM_INPUT_WIDTH).initial(state.maxRenewals).maxLength(6).build());

        ClickCallback.Options clickOptions = CLICK_OPTIONS;

        DialogActionCallback nextCallback = (response, audience) -> {
            saveCreate(state, response);
            RegionManager regionManager = regionManager(state.world);
            if (regionManager == null || !validate(player, state, regionManager)) {
                showCreateDialog(player, state);
                return;
            }
            showConfirmDialog(player, state);
        };
        DialogActionCallback tagsCallback = (response, audience) -> {
            saveCreate(state, response);
            showTagDialog(player, state);
        };

        List<ActionButton> actions = new ArrayList<>();
        actions.add(ActionButton.builder(Component.text("Next", NamedTextColor.GREEN))
                .width(150)
                .action(DialogAction.customClick(nextCallback, clickOptions))
                .build());
        if (!state.permittedTagIds.isEmpty()) {
            actions.add(ActionButton.builder(Component.text("Tags"))
                    .width(150)
                    .action(DialogAction.customClick(tagsCallback, clickOptions))
                    .build());
        }

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Step 2 of 3: Details"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Landlord: " + player.getName()))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(actions,
                        ActionButton.builder(Component.text("Cancel")).width(150).build(),
                        1)));
        player.showDialog(dialog);
    }

    private void showTagDialog(@NotNull Player player, @NotNull SubregionState state) {
        RealtyTags tags = realtyTags.get();
        List<DialogInput> inputs = new ArrayList<>();
        for (int i = 0; i < state.permittedTagIds.size(); i++) {
            String tagId = state.permittedTagIds.get(i);
            ConfigRegionTag tag = tags.get(tagId);
            if (tag == null) {
                continue;
            }
            inputs.add(DialogInput.bool(TAG_INPUT_PREFIX + i, tag.tagDisplayName())
                    .initial(state.selectedTags.contains(tagId))
                    .onTrue("true").onFalse("false").build());
        }

        ClickCallback.Options clickOptions = CLICK_OPTIONS;

        DialogActionCallback doneCallback = (response, audience) -> {
            saveTags(state, response);
            showCreateDialog(player, state);
        };

        List<ActionButton> actions = new ArrayList<>();
        actions.add(ActionButton.builder(Component.text("Done", NamedTextColor.GREEN))
                .width(150)
                .action(DialogAction.customClick(doneCallback, clickOptions))
                .build());

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Step 2 of 3: Tags"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Pick the tags for this subregion."))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(actions,
                        ActionButton.builder(Component.text("Cancel")).width(150).build(),
                        1)));
        player.showDialog(dialog);
    }

    private void showConfirmDialog(@NotNull Player player, @NotNull SubregionState state) {
        double price = parsePrice(state.price);

        String renewals = state.unlimitedRenewals ? "Unlimited" : state.maxRenewals;

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text()
                .append(Component.text("Renting out "))
                .append(Component.text(state.name, NamedTextColor.AQUA))
                .build()));
        body.add(DialogBody.plainMessage(Component.text("Landlord: " + player.getName())));
        body.add(DialogBody.plainMessage(Component.text()
                .append(Component.text("Price: "))
                .append(Component.text(CurrencyFormatter.format(price), NamedTextColor.GREEN))
                .build()));
        body.add(DialogBody.plainMessage(Component.text()
                .append(Component.text("Lease: "))
                .append(Component.text(durationSummary(state), NamedTextColor.GREEN))
                .build()));
        body.add(DialogBody.plainMessage(Component.text("Renewals: " + renewals)));
        if (!state.selectedTags.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text(
                    "Tags: " + String.join(", ", state.selectedTags))));
        }

        ClickCallback.Options clickOptions = CLICK_OPTIONS;

        DialogActionCallback confirmCallback = (response, audience) -> submit(player, state);
        DialogActionCallback backCallback = (response, audience) -> showCreateDialog(player, state);

        List<ActionButton> actions = new ArrayList<>();
        actions.add(ActionButton.builder(Component.text("Confirm", NamedTextColor.GREEN))
                .width(150)
                .action(DialogAction.customClick(confirmCallback, clickOptions))
                .build());
        actions.add(ActionButton.builder(Component.text("Back"))
                .width(150)
                .action(DialogAction.customClick(backCallback, clickOptions))
                .build());

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Step 3 of 3: Confirm"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body)
                        .inputs(List.of())
                        .build())
                .type(DialogType.multiAction(actions,
                        ActionButton.builder(Component.text("Cancel")).width(150).build(),
                        actions.size())));
        player.showDialog(dialog);
    }

    private void submit(@NotNull Player player, @NotNull SubregionState state) {
        RegionManager regionManager = regionManager(state.world);
        if (regionManager == null || !validate(player, state, regionManager)) {
            return;
        }
        ProtectedRegion parent = regionManager.getRegion(state.parentId);
        if (parent == null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_NO_FREEHOLD,
                    Placeholder.unparsed("region", state.parentId)));
            return;
        }
        WorldGuardRegion parentRegion = new WorldGuardRegion(parent, state.world);
        double price = parsePrice(state.price);
        long durationSeconds = resolveDuration(state).toSeconds();
        int maxRenewals = resolveMaxRenewals(state);
        String name = state.name;

        api.quickCreateSubregion(parentRegion, name, state.selection, price, durationSeconds,
                        maxRenewals, player.getUniqueId())
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.QuickCreateSubregionResult.Success s -> {
                            wandManager.clear(player.getUniqueId());
                            playerStates.remove(player.getUniqueId());
                            applyTags(s.regionId(), new LinkedHashSet<>(state.selectedTags));
                            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CREATE_SUCCESS,
                                    Placeholder.unparsed("region", s.regionId()),
                                    Placeholder.unparsed("parent", s.parentId())));
                        }
                        case RealtyPaperApi.QuickCreateSubregionResult.NoFreeholdContract nfc ->
                                player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_NO_FREEHOLD,
                                        Placeholder.unparsed("region", nfc.parentId())));
                        case RealtyPaperApi.QuickCreateSubregionResult.RegionExists re ->
                                player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_REGION_EXISTS,
                                        Placeholder.unparsed("region", re.regionId())));
                        case RealtyPaperApi.QuickCreateSubregionResult.Error error ->
                                player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CREATE_ERROR,
                                        Placeholder.unparsed("error", error.message())));
                    }
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CREATE_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    private void applyTags(@NotNull String regionId, @NotNull Set<String> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        executorState.dbExec().execute(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                RegionTagMapper mapper = session.regionTagMapper();
                for (String tagId : tagIds) {
                    if (!mapper.exists(tagId, regionId)) {
                        mapper.insert(tagId, regionId);
                    }
                }
            }
        });
    }

    /**
     * Validates the current form, messaging the player and returning {@code false} on the first
     * problem. Geometry/ownership were already enforced at {@link #open}; this re-checks the
     * user-entered fields plus name uniqueness and sibling overlap against the chosen parent.
     */
    private boolean validate(@NotNull Player player, @NotNull SubregionState state,
                             @NotNull RegionManager regionManager) {
        if (state.name == null || !VALID_NAME_PATTERN.matcher(state.name).matches()) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_INVALID_NAME,
                    Placeholder.unparsed("region", String.valueOf(state.name))));
            return false;
        }
        if (regionManager.getRegion(state.name) != null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_REGION_EXISTS,
                    Placeholder.unparsed("region", state.name)));
            return false;
        }
        ProtectedRegion parent = regionManager.getRegion(state.parentId);
        if (parent == null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_NO_FREEHOLD,
                    Placeholder.unparsed("region", String.valueOf(state.parentId))));
            return false;
        }
        ProtectedRegion sibling = SubregionSelectionValidator.overlappingSibling(
                state.selection, parent, regionManager);
        if (sibling != null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_OVERLAPS_SIBLING,
                    Placeholder.unparsed("sibling", sibling.getId())));
            return false;
        }
        if (parsePrice(state.price) <= 0) {
            player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                    Placeholder.unparsed("error", "Price must be more than 0.")));
            return false;
        }
        Duration duration = resolveDuration(state);
        if (duration == null || duration.isZero() || duration.isNegative()) {
            player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                    Placeholder.unparsed("error", "Lease length must be more than 0.")));
            return false;
        }
        if (resolveMaxRenewals(state) == null) {
            player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                    Placeholder.unparsed("error", "Max renewals must be 0 or more.")));
            return false;
        }
        return true;
    }

    private void saveCreate(@NotNull SubregionState state, @NotNull DialogResponseView response) {
        String parent = response.getText(INPUT_PARENT);
        if (parent != null) {
            state.parentId = parent;
        }
        String name = response.getText(INPUT_NAME);
        if (name != null) {
            state.name = name.trim();
        }
        String price = response.getText(INPUT_PRICE);
        if (price != null) {
            state.price = price;
        }
        String amount = response.getText(INPUT_DURATION_AMOUNT);
        if (amount != null) {
            state.durationAmount = amount.trim();
        }
        String unit = response.getText(INPUT_DURATION_UNIT);
        if (unit != null) {
            state.durationUnit = unit;
        }
        Boolean unlimited = response.getBoolean(INPUT_UNLIMITED_RENEWALS);
        if (unlimited != null) {
            state.unlimitedRenewals = unlimited;
        }
        String renewals = response.getText(INPUT_MAX_RENEWALS);
        if (renewals != null) {
            state.maxRenewals = renewals.trim();
        }
    }

    private void saveTags(@NotNull SubregionState state, @NotNull DialogResponseView response) {
        for (int i = 0; i < state.permittedTagIds.size(); i++) {
            Boolean on = response.getBoolean(TAG_INPUT_PREFIX + i);
            if (on == null) {
                continue;
            }
            String tagId = state.permittedTagIds.get(i);
            if (on) {
                state.selectedTags.add(tagId);
            } else {
                state.selectedTags.remove(tagId);
            }
        }
    }

    /**
     * Returns the max-renewals value to store: {@code -1} for unlimited, otherwise the entered
     * count. Returns {@code null} if the entered count isn't a valid non-negative number.
     */
    private Integer resolveMaxRenewals(@NotNull SubregionState state) {
        if (state.unlimitedRenewals) {
            return UNLIMITED_RENEWALS;
        }
        try {
            int value = Integer.parseInt(state.maxRenewals.trim());
            return value < 0 ? null : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Duration resolveDuration(@NotNull SubregionState state) {
        long amount;
        try {
            amount = Long.parseLong(state.durationAmount.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (amount <= 0) {
            return null;
        }
        return Duration.ofSeconds(amount * durationUnitOf(state).seconds);
    }

    private static DurationUnit durationUnitOf(@NotNull SubregionState state) {
        try {
            return DurationUnit.valueOf(state.durationUnit);
        } catch (IllegalArgumentException ex) {
            return DurationUnit.DAYS;
        }
    }

    private static String durationSummary(@NotNull SubregionState state) {
        return state.durationAmount + " "
                + durationUnitOf(state).label.toLowerCase(java.util.Locale.ROOT);
    }

    private static double parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static RegionManager regionManager(@NotNull World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
    }
}
