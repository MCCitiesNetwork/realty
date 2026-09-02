package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import org.apache.ibatis.session.ExecutorType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

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

    private static @NotNull SqlSessionWrapper stubSession() {
        RealtyWorldMapper realtyWorldMapper = worldMapperHandler();
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

    private static @NotNull RealtyWorldMapper worldMapperHandler() {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("selectAll".equals(method.getName())) {
                return List.of();
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

        private StubDatabase(boolean failing) {
            this.failing = failing;
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
            return stubSession();
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
