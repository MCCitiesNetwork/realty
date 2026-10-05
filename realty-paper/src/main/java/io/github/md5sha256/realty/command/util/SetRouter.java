package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads a region's contracts and the sender's {@link ActorContext}, asks {@link SetRouting} what a
 * {@code /realty set} command may do, and either refuses or hands the decision on, so each command only
 * has to act on the answer.
 *
 * <p>The reads, and the context's Treasury and permission-plugin lookups, run on the database executor.
 * The decision, every message and the callback run on the main thread, so the callback may fire
 * cancellable events.</p>
 */
public record SetRouter(@NotNull RealtyPaperApi api, @NotNull ActorContexts actors,
                        @NotNull MessageContainer messages, @NotNull ExecutorState executorState,
                        @NotNull Logger logger) {

    /** Which {@link ActorContext} test "holds the region" uses. */
    public enum HolderTest { MANAGES, REASSIGNS }

    /** What a command needs once it is allowed to proceed. */
    public record Routed(@NotNull SetRouting.Outcome outcome, @NotNull ActorContext actor,
                         @Nullable LeaseholdContractEntity lease) {

        /** For an {@code ApplyNow} outcome only: whether the write must refuse a region that was just rented. */
        public boolean vacantOnly() {
            if (outcome instanceof SetRouting.Outcome.ApplyNow applyNow) {
                return applyNow.vacantOnly();
            }
            throw new IllegalStateException("vacantOnly is only defined for an ApplyNow outcome, not " + outcome);
        }
    }

    private record Read(@NotNull ActorContext actor, @Nullable FreeholdContractEntity freehold,
                        @Nullable LeaseholdContractEntity lease) {}

    /**
     * Decides and either sends the refusal message or calls {@code onRouted} with ApplyNow, Schedule or
     * Request. {@code noPermissionKey} is the message for a freehold caller who does not hold the region.
     * A failure while reading, deciding or inside the callback is logged and reported to the sender.
     *
     * @param othersPermission the node that makes the sender's context bypass the holder test
     * @param extra            further parties to test the sender against, such as a new landlord
     */
    public void route(@NotNull CommandSender sender, @NotNull WorldGuardRegion region,
                      @NotNull SetRouting.Kind kind, @NotNull HolderTest holderTest,
                      @NotNull String othersPermission, @NotNull String noPermissionKey,
                      boolean nowFlag, boolean unlimitedExtensions,
                      @NotNull Consumer<Routed> onRouted, @NotNull Party... extra) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        Player player = sender instanceof Player p ? p : null;
        boolean bypass = player != null && sender.hasPermission(othersPermission);
        boolean mayUseNow = player == null || sender.hasPermission("realty.command.set.now");
        CompletableFuture<ActorContext> actorFuture = player == null
                ? CompletableFuture.completedFuture(ActorContext.console())
                : CompletableFuture.supplyAsync(() -> actors.forRegion(player, bypass, region, extra),
                        executorState.dbExec());
        actorFuture.thenCompose(actor -> api.getFreeholdContract(regionId, worldId)
                        .thenCombine(api.getLeaseholdContract(regionId, worldId),
                                (freehold, lease) -> new Read(actor, freehold, lease)))
                .whenCompleteAsync((read, error) -> {
                    try {
                        if (error != null) {
                            reportFailure(sender, error);
                            return;
                        }
                        decideAndAct(sender, regionId, read, kind, holderTest, noPermissionKey,
                                mayUseNow, nowFlag, unlimitedExtensions, onRouted);
                    } catch (Throwable ex) {
                        reportFailure(sender, ex);
                    }
                }, executorState.mainThreadExec());
    }

    private void decideAndAct(@NotNull CommandSender sender, @NotNull String regionId, @NotNull Read read,
                              @NotNull SetRouting.Kind kind, @NotNull HolderTest holderTest,
                              @NotNull String noPermissionKey, boolean mayUseNow, boolean nowFlag,
                              boolean unlimitedExtensions, @NotNull Consumer<Routed> onRouted) {
        ActorContext actor = read.actor();
        LeaseholdContractEntity lease = read.lease();
        Party holder = SetRouting.holderOf(read.freehold(), lease);
        boolean holds = holder != null && switch (holderTest) {
            case MANAGES -> actor.mayManage(holder);
            case REASSIGNS -> actor.mayReassign(holder);
        };
        boolean tenant = SetRouting.isTenant(actor, lease);
        boolean canPropose = lease != null && SetRouting.canPropose(actor, lease);
        SetRouting.Outcome outcome = SetRouting.decide(SetRouting.tenureOf(read.freehold(), lease), kind,
                tenant, holds, mayUseNow, nowFlag, unlimitedExtensions, canPropose);
        if (outcome instanceof SetRouting.Outcome.Refused refused) {
            String key = switch (refused.reason()) {
                case NOT_HOLDER -> lease != null ? MessageKeys.SET_NOT_LANDLORD : noPermissionKey;
                case NEEDS_NOW -> MessageKeys.SET_RENTED_NEEDS_NOW;
                case NOW_NOT_PERMITTED -> MessageKeys.SET_RENTED_NO_NOW_PERMISSION;
                case LEASE_ENDING -> MessageKeys.MODIFY_TERMINATING;
                case UNLIMITED_NEEDS_NOW -> MessageKeys.SET_UNLIMITED_NEEDS_NOW;
                case CONSOLE_NEEDS_NOW -> MessageKeys.SET_CONSOLE_NEEDS_NOW;
            };
            sender.sendMessage(messages.messageFor(key, Placeholder.unparsed("region", regionId)));
            return;
        }
        onRouted.accept(new Routed(outcome, actor, lease));
    }

    /** Sends {@code errorKey} with the error text to the sender and logs it. */
    public <T> @NotNull CompletableFuture<T> reportWriteFailure(@NotNull CompletableFuture<T> write,
                                                                @NotNull CommandSender sender,
                                                                @NotNull String errorKey) {
        return write.whenComplete((result, error) -> {
            if (error != null) {
                Throwable cause = unwrap(error);
                logger.log(Level.SEVERE, "A set command failed", cause);
                sender.sendMessage(messages.messageFor(errorKey,
                        Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
            }
        });
    }

    private void reportFailure(@NotNull CommandSender sender, @NotNull Throwable error) {
        Throwable cause = unwrap(error);
        logger.log(Level.SEVERE, "Failed to route a set command", cause);
        sender.sendMessage(messages.messageFor(MessageKeys.SET_CHECK_PERMISSIONS_ERROR,
                Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
    }

    private static @NotNull Throwable unwrap(@NotNull Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
