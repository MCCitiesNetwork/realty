package io.github.md5sha256.realty.command.util;

/**
 * The kind of Treasury-backed party a name resolves to when it is given with one of the type
 * flags ({@code --government}, {@code --business}, {@code --system}, {@code --group}). With no
 * flag, a name is a player.
 */
public enum PartyFlag {
    GOVERNMENT,
    BUSINESS,
    SYSTEM,
    GROUP
}
