package io.github.md5sha256.realty.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public record ExecutorState(@NotNull Executor mainThreadExec, @NotNull ExecutorService dbExec) {
}
