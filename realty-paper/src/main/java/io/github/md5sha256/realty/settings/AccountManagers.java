package io.github.md5sha256.realty.settings;

/**
 * Which people on a Treasury account may act for it as a contract party. Authorizers always may;
 * this decides whether plain members may too.
 */
public enum AccountManagers {
    /** Members and authorizers of the account. */
    MEMBERS,
    /** Authorizers of the account only. */
    AUTHORIZERS
}
