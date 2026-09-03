package io.github.md5sha256.realty.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps the Pterodactyl egg in step with the service it launches.
 *
 * <p>An egg is not exercised by any other test: it is data a panel reads, so a value
 * that drifts out of step with the code fails silently in production rather than in
 * the build. The two couplings that matter are asserted here.</p>
 */
class PterodactylEggTest {

    private static final Path EGG = Path.of("pterodactyl-egg.json");

    private static JsonNode egg() throws IOException {
        Assertions.assertTrue(Files.exists(EGG),
                "egg not found at " + EGG.toAbsolutePath()
                        + " -- this test expects the realty-rest project directory as its "
                        + "working directory");
        return new ObjectMapper().readTree(Files.readString(EGG));
    }

    @Test
    void theStartupDoneStringMatchesWhatTheServiceActuallyLogs() throws IOException {
        // config.startup is itself a JSON document embedded as a string.
        String startup = egg().get("config").get("startup").asText();
        String done = new ObjectMapper().readTree(startup).get("done").asText();

        Assertions.assertEquals(RealtyRestServer.LISTENING_LOG_PREFIX, done,
                "the egg's done string must be text RealtyRestServer.start() actually logs, "
                        + "or the panel waits forever on a server that is already up");
    }

    /**
     * Variables the running service reads from its environment. Every one of these
     * is a {@code RestConfiguration} lookup.
     */
    private static final Set<String> RUNTIME_VARIABLES = Set.of(
            "REALTY_DB_URL",
            "REALTY_DB_USERNAME",
            "REALTY_DB_PASSWORD",
            "REALTY_REST_HOST",
            "REALTY_REST_MAX_PAGE_SIZE",
            "REALTY_REST_CORS_ORIGINS",
            "REALTY_REST_MODULE_URL",
            "REALTY_REST_MODULE_SECRET",
            "REALTY_REST_MODULE_TIMEOUT_MS");

    /**
     * Wings injects {@code SERVER_PORT} into every container from the server's primary
     * allocation, and the yolks entrypoint expands {@code {{SERVER_PORT}}} in the startup
     * command. The port is therefore never a panel variable: asking the operator to type
     * it a second time only lets it disagree with the allocation, and a disagreement means
     * a server that is up but unreachable.
     */
    @Test
    void theBindPortComesFromTheAllocationNotAPanelVariable() throws IOException {
        JsonNode egg = egg();
        Assertions.assertTrue(egg.get("startup").asText().contains("REALTY_REST_PORT={{SERVER_PORT}}"),
                "the startup command must pass the allocation's SERVER_PORT to the service");
        for (JsonNode variable : egg.get("variables")) {
            String name = variable.get("env_variable").asText();
            Assertions.assertNotEquals("REALTY_REST_PORT", name,
                    "the bind port must not be a panel variable");
            Assertions.assertFalse(name.equals("SERVER_PORT") || name.equals("SERVER_IP"),
                    name + " is reserved by Wings and must not be declared by the egg");
        }
    }

    /**
     * Variables only the install script reads. The service never sees these, so
     * they are listed separately rather than loosening the runtime check to
     * "at least these" -- which would stop catching a panel variable nothing reads.
     */
    private static final Set<String> INSTALL_VARIABLES = Set.of("REALTY_REST_VERSION");

    @Test
    void everyDocumentedEnvironmentVariableIsDeclaredAsAPanelVariable() throws IOException {
        Set<String> declared = new TreeSet<>();
        for (JsonNode variable : egg().get("variables")) {
            declared.add(variable.get("env_variable").asText());
        }

        Set<String> expected = new HashSet<>(RUNTIME_VARIABLES);
        expected.addAll(INSTALL_VARIABLES);

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(declared);
        Assertions.assertTrue(missing.isEmpty(),
                "variables the egg must expose but does not: " + missing);

        Set<String> extra = new TreeSet<>(declared);
        extra.removeAll(expected);
        Assertions.assertTrue(extra.isEmpty(),
                "panel variables nothing reads: " + extra);
    }

    /**
     * The install script downloads a prebuilt jar rather than compiling one. The
     * URL it builds is the published contract between this egg and the release
     * workflow's asset name; if either side moves, every install 404s on someone
     * else's panel rather than failing in this build.
     */
    @Test
    void theInstallScriptDownloadsThePinnedReleaseAsset() throws IOException {
        String script = egg().get("scripts").get("installation").get("script").asText();

        Assertions.assertTrue(script.contains("${REALTY_REST_VERSION"),
                "the install script must take its version from REALTY_REST_VERSION");
        Assertions.assertTrue(script.contains("releases/download/${VERSION}/"),
                "the download URL must address the release tag for that version, "
                        + "which carries no v prefix");
        Assertions.assertTrue(script.contains("realty-rest-${VERSION}-all.jar"),
                "the asset name must match what the release workflow uploads");
        Assertions.assertTrue(script.contains("MCCitiesNetwork/realty"),
                "the default repository must be this project");
        Assertions.assertTrue(script.contains("--fail"),
                "curl must fail on an HTTP error rather than write an error page to the jar");
    }

    /**
     * The panel runs the startup command against whatever the install script left
     * behind, so the two file names must agree.
     */
    @Test
    void theStagedJarNameMatchesTheStartupCommand() throws IOException {
        JsonNode egg = egg();
        String script = egg.get("scripts").get("installation").get("script").asText();
        String startup = egg.get("startup").asText();

        Assertions.assertTrue(startup.contains("realty-rest-all.jar"),
                "unexpected startup command: " + startup);
        Assertions.assertTrue(script.contains("mv realty-rest-all.jar.part realty-rest-all.jar"),
                "the install script must stage the jar under the name the startup command runs");
    }

    /**
     * Nothing is compiled on the panel any more, so the install stage must not
     * still be reaching for a compiler or a clone.
     */
    @Test
    void theInstallScriptNoLongerBuildsFromSource() throws IOException {
        JsonNode egg = egg();
        String script = egg.get("scripts").get("installation").get("script").asText();

        Assertions.assertFalse(script.contains("git clone"),
                "the install script must not clone the repository");
        Assertions.assertFalse(script.contains("gradlew"),
                "the install script must not run a Gradle build");
        Assertions.assertFalse(egg.get("scripts").get("installation").get("container").asText().contains("jdk"),
                "the install container no longer needs a JDK");
    }

    /**
     * Wings runs the startup command as the unprivileged {@code container} user out of
     * {@code /home/container}. A stock upstream JRE image has neither that user nor the
     * entrypoint scaffolding, so the server dies before the JVM prints anything -- an
     * empty console with an instant exit, which is the least diagnosable failure there
     * is. Only a Pterodactyl-flavoured image is a valid choice here.
     */
    @Test
    void everyDockerImageIsAPterodactylCompatibleOne() throws IOException {
        JsonNode images = egg().get("docker_images");
        Assertions.assertTrue(images.size() > 0, "the egg must offer at least one image");
        for (JsonNode image : images) {
            String reference = image.asText();
            Assertions.assertTrue(reference.contains("yolks"),
                    "not a Pterodactyl image: " + reference
                            + " -- Wings needs the yolks entrypoint and container user, "
                            + "so a stock JRE image fails to start with no output at all");
        }
    }

    /**
     * Every variable an operator has to fill in must be viewable. In the panel's client-facing
     * startup tab {@code user_viewable} decides whether the field is rendered at all, and
     * {@code user_editable} only decides whether a rendered field accepts input, so the pair
     * {@code viewable: false, editable: true} describes a field nobody can type into. That is
     * how the database password ended up unenterable by anyone short of a panel root admin.
     *
     * <p>A required variable is, by definition, one every deployment must set correctly --
     * whether or not it ships a default. A dummy default (the database credentials below
     * ship "change-me"-style placeholders so panel server creation doesn't block on an empty
     * required field) is a value the operator is expected to overwrite, not one that makes
     * the field optional, so it must stay reachable regardless. Deriving the set from the
     * egg's own rules rather than listing names keeps a future required variable from
     * repeating this.</p>
     */
    @Test
    void everyRequiredVariableIsReachableFromThePanel() throws IOException {
        for (JsonNode variable : egg().get("variables")) {
            String name = variable.get("env_variable").asText();
            if (!variable.get("rules").asText().contains("required")) {
                continue;
            }
            Assertions.assertTrue(variable.get("user_viewable").asBoolean(),
                    name + " is required but is not viewable, so the panel never renders a"
                            + " field for it");
            Assertions.assertTrue(variable.get("user_editable").asBoolean(),
                    name + " is required but is not editable");
        }
    }

    /**
     * The database password is a credential, and making it enterable also makes it readable
     * by anyone with startup access to the server. That is the accepted trade -- an
     * unenterable credential is not a working deployment -- but it is a deliberate choice
     * rather than an oversight, so it is pinned here where anyone changing it will see why.
     */
    @Test
    void theDatabasePasswordIsEnterableFromThePanel() throws IOException {
        JsonNode password = variable("REALTY_DB_PASSWORD");
        Assertions.assertTrue(password.get("user_viewable").asBoolean(),
                "an admin must be able to enter the database password in the panel");
        Assertions.assertTrue(password.get("user_editable").asBoolean());
    }

    /**
     * The module secret stays hidden for now, which it can afford to be: unlike the database
     * password it is optional, so leaving it unset is a supported deployment -- the service
     * simply runs without module enrichment rather than failing to start. It carries the same
     * unenterable-field problem for anyone who does want enrichment, and the second assertion
     * here is what forces the question to be revisited if it ever becomes required.
     */
    @Test
    void theModuleSecretRemainsHidden() throws IOException {
        JsonNode secret = variable("REALTY_REST_MODULE_SECRET");
        Assertions.assertFalse(secret.get("user_viewable").asBoolean(),
                "the module secret is optional, so it need not be exposed to panel viewers");
        Assertions.assertFalse(secret.get("rules").asText().contains("required"),
                "if the module secret ever becomes required it must become viewable too");
    }

    private static JsonNode variable(String envVariable) throws IOException {
        for (JsonNode variable : egg().get("variables")) {
            if (variable.get("env_variable").asText().equals(envVariable)) {
                return variable;
            }
        }
        return Assertions.fail("the egg declares no " + envVariable);
    }
}
