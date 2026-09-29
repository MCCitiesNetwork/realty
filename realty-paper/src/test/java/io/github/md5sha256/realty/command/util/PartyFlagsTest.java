package io.github.md5sha256.realty.command.util;

import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.FlagContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartyFlagsTest {

    /** A command context in which exactly the flags named in {@code present} were given. */
    private static CommandContext<?> contextWith(String... present) {
        Set<String> given = Set.of(present);
        FlagContext flags = mock(FlagContext.class);
        when(flags.isPresent(anyString())).thenAnswer(call -> given.contains(call.<String>getArgument(0)));
        CommandContext<?> ctx = mock(CommandContext.class);
        when(ctx.flags()).thenReturn(flags);
        return ctx;
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
}
