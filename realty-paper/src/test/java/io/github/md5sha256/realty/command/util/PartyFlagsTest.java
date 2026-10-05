package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.DefaultParties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.FlagContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PartyFlagsTest {

    private static final Party GOV_SECURITY = Party.account(42, AccountKind.GOVERNMENT);
    private static final Party DEFAULT_LANDLORD = Party.account(7, AccountKind.GOVERNMENT);

    /** Runs every task on the calling thread, so the tests see the result at once. */
    private static final class DirectExecutor extends AbstractExecutorService {
        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private static final ExecutorService DIRECT = new DirectExecutor();
    private static final ExecutorState EXECUTORS = new ExecutorState(DIRECT, DIRECT, DIRECT);

    private final PartyResolver resolver = mock(PartyResolver.class);
    private final CommandSender sender = mock(CommandSender.class);
    // A key with no text renders as the key itself.
    private final MessageContainer messages = new MessageContainer();
    private final AtomicReference<Party> resolved = new AtomicReference<>();

    /** A command context in which exactly the flags named in {@code present} were given. */
    private static CommandContext<?> contextWith(String... present) {
        Set<String> given = Set.of(present);
        FlagContext flags = mock(FlagContext.class);
        when(flags.isPresent(anyString())).thenAnswer(call -> given.contains(call.<String>getArgument(0)));
        CommandContext<?> ctx = mock(CommandContext.class);
        when(ctx.flags()).thenReturn(flags);
        return ctx;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String onlyMessageSent() {
        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(sender).sendMessage(sent.capture());
        return plain(sent.getValue());
    }

    private void resolveOrDefault(String name, PartyFlag flag, Party fallback) {
        PartyFlags.resolveOrDefault(resolver, EXECUTORS, messages, sender, "landlord", name, flag, fallback,
                resolved::set);
    }

    @Test
    void noFlag_readsAsNull() {
        assertEquals(new PartyFlags.Read.One(null), PartyFlags.read(contextWith()));
    }

    @ParameterizedTest
    @EnumSource(PartyFlag.class)
    void oneFlag_readsAsThatFlag(PartyFlag flag) {
        String name = flag.name().toLowerCase(Locale.ROOT);
        assertEquals(new PartyFlags.Read.One(flag), PartyFlags.read(contextWith(name)));
    }

    @Test
    void twoFlags_areTooMany() {
        assertInstanceOf(PartyFlags.Read.TooMany.class, PartyFlags.read(contextWith("government", "group")));
    }

    @Test
    void nameAndFlag_resolvesTheNamedParty() {
        when(resolver.resolve("GovSecurity", PartyFlag.GOVERNMENT, null))
                .thenReturn(new PartyResolver.Resolution.Resolved(GOV_SECURITY));
        resolveOrDefault("GovSecurity", PartyFlag.GOVERNMENT, DEFAULT_LANDLORD);
        assertEquals(GOV_SECURITY, resolved.get());
        verify(sender, never()).sendMessage(any(Component.class));
    }

    @Test
    void typeFlagWithoutAName_isRefused() {
        resolveOrDefault(null, PartyFlag.BUSINESS, DEFAULT_LANDLORD);
        assertNull(resolved.get());
        assertEquals(MessageKeys.PARTY_TYPE_FLAG_WITHOUT_NAME, onlyMessageSent());
        verifyNoInteractions(resolver);
    }

    @Test
    void noNameAndNoDefault_isRefused() {
        resolveOrDefault(null, null, null);
        assertNull(resolved.get());
        assertEquals(MessageKeys.ERROR_DEFAULT_PARTY_UNRESOLVED, onlyMessageSent());
        verifyNoInteractions(resolver);
    }

    @Test
    void noName_usesTheResolvedDefault() {
        resolveOrDefault(null, null, DEFAULT_LANDLORD);
        assertEquals(DEFAULT_LANDLORD, resolved.get());
        verifyNoInteractions(resolver);
        verify(sender, never()).sendMessage(any(Component.class));
    }

    @Test
    void unresolvedDefaultTitleholder_isRefused() {
        DefaultParties defaults = new DefaultParties(GOV_SECURITY, DEFAULT_LANDLORD, null, true, List.of());
        messages.setMessage(MessageKeys.ERROR_DEFAULT_PARTY_UNRESOLVED, "no default <role>");

        assertTrue(PartyFlags.refuseUnresolvedTitleholder(messages, sender, defaults));
        assertEquals("no default titleholder", onlyMessageSent());
    }

    @Test
    void absentDefaultTitleholder_isNotRefused() {
        DefaultParties defaults = new DefaultParties(GOV_SECURITY, DEFAULT_LANDLORD, null, false, List.of());

        assertFalse(PartyFlags.refuseUnresolvedTitleholder(messages, sender, defaults));
        verify(sender, never()).sendMessage(any(Component.class));
    }

    @Test
    void refusal_suppliesNameAndLowerCaseType() {
        messages.setMessage(MessageKeys.PARTY_TYPE_MISMATCH,
                "The account <name> is not a <type> account.");
        String text = plain(PartyFlags.refusal(messages,
                new PartyResolver.Resolution.Refused(MessageKeys.PARTY_TYPE_MISMATCH, "Acme"), PartyFlag.BUSINESS));
        assertTrue(text.contains("Acme"), text);
        assertTrue(text.contains("business"), text);
        assertEquals("The account Acme is not a business account.", text);
    }
}
