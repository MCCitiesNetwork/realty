package io.github.md5sha256.realty.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface CustomCommandBean<C> {

    @NotNull Collection<LiteralArgumentBuilder<C>> commands();

    interface Single<C> extends CustomCommandBean<C> {
        @NotNull LiteralArgumentBuilder<C> command();

        @Override
        default @NotNull Collection<LiteralArgumentBuilder<C>> commands() {
            return List.of(command());
        }
    }

}
