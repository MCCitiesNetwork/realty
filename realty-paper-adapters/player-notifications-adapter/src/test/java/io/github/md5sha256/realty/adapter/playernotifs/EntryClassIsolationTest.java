package io.github.md5sha256.realty.adapter.playernotifs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Guards the one property that keeps a PlayerNotifications-less server booting: the module's entry
 * class names no PlayerNotifications type.
 *
 * <p>{@code ModuleLoader} resolves the entry class named in {@code module-manifest.yml} with
 * {@code Class.forName}, and the JVM resolves the types a class names in its fields and method
 * signatures while loading and verifying it. When {@code PlayerNotificationsAdapterModule} held
 * {@code NotificationService} and {@code RealtyNotificationRenderer} fields, that resolution failed
 * on a server without PlayerNotifications with</p>
 *
 * <pre>
 * java.lang.NoClassDefFoundError: io/github/md5sha256/playernotifications/api/render/NotificationRenderer
 *     at java.lang.Class.forName(Class.java:547)
 *     at ...ModuleLoader.loadModule(ModuleLoader.java:87)
 *     at ...ModuleLifecycleManager.start(ModuleLifecycleManager.java:71)
 *     at io.github.md5sha256.realty.Realty.onEnable
 * </pre>
 *
 * <p>{@code NoClassDefFoundError} is an {@code Error}; the lifecycle manager catches only
 * {@code ModuleInitializationException | RuntimeException} around {@code initialize}, so it escaped
 * {@code start()} and aborted Realty's whole {@code onEnable}. The entry class's presence check
 * never got the chance to run. This test asserts the property at the bytecode level rather than by
 * loading the class, because a compiling reference is exactly what a source-level review misses —
 * and because the entry class cannot be loaded in a unit test at all: its supertypes come from
 * {@code compileOnly} dependencies that are absent from the test runtime, which is the same
 * absence the production crash is about.</p>
 */
class EntryClassIsolationTest {

    /** The package whose classes must not appear in the entry class's constant pool. */
    private static final String PN_PACKAGE = "io/github/md5sha256/playernotifications/";

    /** The entry class, named as a resource: it must never be <em>loaded</em> here — see below. */
    private static final String ENTRY_CLASS_RESOURCE =
            "io/github/md5sha256/realty/adapter/playernotifs/PlayerNotificationsAdapterModule.class";

    /** The bridge's internal name, which the entry class must delegate to. */
    private static final String BRIDGE_INTERNAL_NAME =
            "io/github/md5sha256/realty/adapter/playernotifs/PlayerNotificationsBridge";

    /**
     * Scans a class file for the package name as raw ASCII. Class-file constant pools store type
     * names as modified-UTF-8 in internal form ({@code a/b/C}), so a plain byte scan finds any
     * reference — field descriptor, method descriptor, {@code Class} constant or signature
     * attribute — with no bytecode library needed.
     */
    private static String readClassFile(String resource) throws IOException {
        byte[] bytes;
        try (InputStream in = EntryClassIsolationTest.class.getClassLoader().getResourceAsStream(resource)) {
            Assertions.assertNotNull(in, "class file not found on the test classpath: " + resource);
            bytes = in.readAllBytes();
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Test
    void entryClassNamesNoPlayerNotificationsType() throws IOException {
        String ascii = readClassFile(ENTRY_CLASS_RESOURCE);
        Assertions.assertFalse(ascii.contains(PN_PACKAGE),
                "PlayerNotificationsAdapterModule references " + PN_PACKAGE + " — loading it on a server "
                        + "without PlayerNotifications throws NoClassDefFoundError out of ModuleLoader and "
                        + "takes Realty's onEnable down with it. Move that state into "
                        + "PlayerNotificationsBridge.");
    }

    /**
     * The other half of the property: the entry class still delegates to the bridge, which is what
     * holds the PlayerNotifications-typed state. A refactor that dropped the delegation would pass
     * the scan above while quietly deleting the module's behaviour.
     *
     * <p>The bridge is safe for the entry class to name even though it references
     * PlayerNotifications internally: it ships in this module's own jar, and the JVM resolves a
     * referenced class's own references only when that class is first used — which happens after
     * the presence check in {@code initialize} has passed.</p>
     */
    @Test
    void entryClassDelegatesToTheBridge() throws IOException {
        String ascii = readClassFile(ENTRY_CLASS_RESOURCE);
        Assertions.assertTrue(ascii.contains(BRIDGE_INTERNAL_NAME),
                "PlayerNotificationsAdapterModule no longer references PlayerNotificationsBridge");
    }
}
