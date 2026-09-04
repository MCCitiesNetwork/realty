package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.NotNull;

/**
 * Outcome of a module call whose whole answer comes from the module.
 *
 * <p>Three cases, because the caller must answer differently to each: an answer, a subject the
 * module does not know (the caller's mistake, a 404), and a module that could not be asked (not
 * the caller's mistake, a 502). The enrichment paths -- geometry on a region, a display name --
 * need none of this and keep degrading to null, because they have something to degrade to.</p>
 */
public sealed interface ModuleResult<T> {

    record Found<T>(@NotNull T value) implements ModuleResult<T> {
    }

    record NotFound<T>() implements ModuleResult<T> {
    }

    record Unavailable<T>() implements ModuleResult<T> {
    }
}
