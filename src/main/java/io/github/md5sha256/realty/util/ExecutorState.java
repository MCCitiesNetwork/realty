package io.github.md5sha256.realty.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public record ExecutorState(@NotNull Executor mainThreadExec, @NotNull Executor dbExec) {
}
