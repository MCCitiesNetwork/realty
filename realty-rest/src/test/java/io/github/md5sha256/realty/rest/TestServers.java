package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import org.apache.ibatis.session.ExecutorType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link RealtyRestServer} over stub {@link RealtyBackend} and
 * {@link Database} implementations, for tests that only need the HTTP layer.
 *
 * <p>{@link RealtyBackend} and {@link SqlSessionWrapper} are large interfaces with
 * many accessors that no endpoint under test calls, so both are backed by a
 * {@link Proxy} that throws {@link UnsupportedOperationException} from every method
 * except the handful a test actually exercises.</p>
 */
final class TestServers {

    private TestServers() {
    }

    static @NotNull RealtyRestServer withHealthyDatabase() {
        return new RealtyRestServer(stubBackend(), new StubDatabase(false), defaultSettings());
    }

    static @NotNull RealtyRestServer withFailingDatabase() {
        return new RealtyRestServer(stubBackend(), new StubDatabase(true), defaultSettings());
    }

    static @NotNull RealtyRestServer withWorlds() {
        List<RealtyWorldEntity> worlds = List.of(
                new RealtyWorldEntity(UUID.randomUUID(), "world"),
                new RealtyWorldEntity(UUID.randomUUID(), "My World"));
        return new RealtyRestServer(stubBackend(), new StubDatabase(false, worlds), defaultSettings());
    }

    static @NotNull RealtyRestServer withNoWorlds() {
        return new RealtyRestServer(stubBackend(), new StubDatabase(false, List.of()), defaultSettings());
    }

    /**
     * A {@link Database} backed by the given worlds, for tests that exercise
     * {@link WorldLookup} directly rather than through a {@link RealtyRestServer}.
     */
    static @NotNull Database databaseWithWorlds(@NotNull List<RealtyWorldEntity> worlds) {
        return new StubDatabase(false, worlds, false);
    }

    /**
     * A {@link Database} whose {@code selectByName} fails the test if invoked, for
     * proving a caller short-circuits before ever reaching a name lookup.
     */
    static @NotNull Database databaseThatFailsIfSelectByNameCalled(@NotNull List<RealtyWorldEntity> worlds) {
        return new StubDatabase(false, worlds, true);
    }

    private static @NotNull RestSettings defaultSettings() {
        return new RestSettings("localhost", 0, 100, null, null, 1500);
    }

    private static @NotNull RealtyBackend stubBackend() {
        InvocationHandler handler = (proxy, method, args) -> {
            throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        return (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
    }

    private static @NotNull SqlSessionWrapper stubSession(@NotNull List<RealtyWorldEntity> worlds,
                                                            boolean failOnSelectByName) {
        RealtyWorldMapper realtyWorldMapper = worldMapperHandler(worlds, failOnSelectByName);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("realtyWorldMapper".equals(method.getName())) {
                return realtyWorldMapper;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            throw new UnsupportedOperationException(
                    "SqlSessionWrapper#" + method.getName() + " is not stubbed for this test");
        };
        return (SqlSessionWrapper) Proxy.newProxyInstance(
                SqlSessionWrapper.class.getClassLoader(),
                new Class<?>[]{SqlSessionWrapper.class},
                handler);
    }

    private static @NotNull RealtyWorldMapper worldMapperHandler(@NotNull List<RealtyWorldEntity> worlds,
                                                                   boolean failOnSelectByName) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("selectAll".equals(method.getName())) {
                return worlds;
            }
            if ("selectByName".equals(method.getName())) {
                if (failOnSelectByName) {
                    throw new AssertionError(
                            "RealtyWorldMapper#selectByName must not be called on this path");
                }
                String name = (String) args[0];
                return worlds.stream()
                        .filter(world -> world.worldName().equals(name))
                        .findFirst()
                        .orElse(null);
            }
            if ("selectById".equals(method.getName())) {
                UUID id = (UUID) args[0];
                return worlds.stream()
                        .filter(world -> world.worldId().equals(id))
                        .findFirst()
                        .orElse(null);
            }
            throw new UnsupportedOperationException(
                    "RealtyWorldMapper#" + method.getName() + " is not stubbed for this test");
        };
        return (RealtyWorldMapper) Proxy.newProxyInstance(
                RealtyWorldMapper.class.getClassLoader(),
                new Class<?>[]{RealtyWorldMapper.class},
                handler);
    }

    /**
     * A {@link Database} whose {@code openSession} methods either return a working
     * stub session or throw, depending on {@code failing}. Every other method throws:
     * this server must never migrate or write, so nothing else should ever be called.
     */
    private static final class StubDatabase implements Database {

        private final boolean failing;
        private final List<RealtyWorldEntity> worlds;
        private final boolean failOnSelectByName;

        private StubDatabase(boolean failing) {
            this(failing, List.of());
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds) {
            this(failing, worlds, false);
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName) {
            this.failing = failing;
            this.worlds = worlds;
            this.failOnSelectByName = failOnSelectByName;
        }

        @Override
        public @NotNull SqlSessionWrapper openSession() {
            return openSession(true);
        }

        @Override
        public @NotNull SqlSessionWrapper openSession(boolean autoCommit) {
            if (this.failing) {
                throw new RuntimeException("stub database is unreachable");
            }
            return stubSession(this.worlds, this.failOnSelectByName);
        }

        @Override
        public @NotNull SqlSessionWrapper openSession(@NotNull ExecutorType executorType, boolean autoCommit) {
            return openSession(autoCommit);
        }

        @Override
        public void initializeSchema(@NotNull Path schemaFilesDirectory) {
            throw new UnsupportedOperationException("realty-rest must never migrate the schema");
        }

        @Override
        public void close() {
            // no-op
        }

    }

}
