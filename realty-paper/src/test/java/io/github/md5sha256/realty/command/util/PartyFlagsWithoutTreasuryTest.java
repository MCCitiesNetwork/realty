package io.github.md5sha256.realty.command.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Building {@link PartyFlags}' suggestion providers on a server without Treasury. A lambda that
 * captures a {@code TreasuryApi} loads that class when it is created, null or not, so a provider
 * built that way fails Realty's enable with {@code NoClassDefFoundError} whenever Treasury is not
 * installed. The test classpath always has Treasury, so these load {@code PartyFlags} through a
 * class loader that cannot see it.
 *
 * <p>The calls go through {@link Caller} rather than reflection on {@code PartyFlags}: reflecting
 * over its methods loads every parameter type, Treasury's included, whereas an ordinary call, as
 * Realty makes, does not.
 */
class PartyFlagsWithoutTreasuryTest {

    private static final String TREASURY_PACKAGE = "net.democracycraft.treasury.";

    @Test
    void suggestionsBuildWithoutTreasury() throws Exception {
        try (URLClassLoader loader = withoutTreasury()) {
            Class<?> treasuryApi = Class.forName(TREASURY_PACKAGE + "api.TreasuryApi", false,
                    getClass().getClassLoader());
            Assertions.assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass(treasuryApi.getName()), "the loader must hide Treasury");

            Assertions.assertNotNull(call(loader, "suggestions"));
        }
    }

    @Test
    void accountSuggestionsBuildWithoutTreasury() throws Exception {
        try (URLClassLoader loader = withoutTreasury()) {
            Assertions.assertNotNull(call(loader, "accountSuggestions"));
        }
    }

    private static Object call(ClassLoader loader, String name) throws Exception {
        Method method = loader.loadClass(Caller.class.getName()).getMethod(name);
        try {
            return method.invoke(null);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof NoClassDefFoundError error) {
                Assertions.fail(name + " needs Treasury to build its provider", error);
            }
            throw ex;
        }
    }

    /** Calls into {@code PartyFlags} as Realty does, with no Treasury type in its own signatures. */
    public static final class Caller {

        private static final Executor DIRECT = Runnable::run;

        public static Object suggestions() {
            return PartyFlags.suggestions(null, null, DIRECT);
        }

        public static Object accountSuggestions() {
            return PartyFlags.accountSuggestions(null, DIRECT);
        }
    }

    /**
     * Every entry of the test classpath except Treasury, loaded child-first so {@code PartyFlags}
     * and everything it links against come from here rather than the application class loader.
     */
    private static URLClassLoader withoutTreasury() throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!new File(entry).getName().startsWith("treasury-api")) {
                urls.add(new File(entry).toURI().toURL());
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }
}
