package io.github.md5sha256.realty.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface CustomCommandBean<C> {

    @NotNull Collection<LiteralArgumentBuilder<? extends C>> commands();

    interface Single<C> extends CustomCommandBean<C> {
        @NotNull LiteralArgumentBuilder<? extends C> command();

        @Override
        @NotNull
        default Collection<LiteralArgumentBuilder<? extends C>> commands() {
            return List.of(command());
        }
    }

}
