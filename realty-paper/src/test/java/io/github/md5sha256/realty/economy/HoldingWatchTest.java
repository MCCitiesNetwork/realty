package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.settings.DefaultParties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HoldingWatchTest {

    private static final UUID BANKER = UUID.randomUUID();

    @Mock
    private RealtyBackend backend;

    private final Logger log = Logger.getLogger("HoldingWatchTest");
    private final List<LogRecord> records = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    /** The escrow player, which a test clears to make the holding account unusable. */
    private UUID escrow = BANKER;

    @BeforeEach
    void captureLog() {
        log.setUseParentHandlers(false);
        log.addHandler(handler);
    }

    @AfterEach
    void releaseLog() {
        log.removeHandler(handler);
    }

    private HoldingWatch watch() {
        return new HoldingWatch(HoldingAccount.vault(
                () -> new DefaultParties(null, null, null, false, escrow, List.of())), backend, log);
    }

    private List<LogRecord> at(Level level) {
        return records.stream().filter(record -> record.getLevel() == level).toList();
    }

    @Test
    void usable_pausesNothing_logsNothing() {
        HoldingWatch watch = watch();

        watch.check();
        watch.check();

        verify(backend, never()).pausePaymentDeadlines();
        Assertions.assertTrue(records.isEmpty(), String.valueOf(records));
    }

    @Test
    void unusable_pausesDeadlinesEachRun() {
        escrow = null;
        HoldingWatch watch = watch();

        watch.check();
        watch.check();
        watch.check();

        verify(backend, times(3)).pausePaymentDeadlines();
    }

    @Test
    void transitionLogging_severeOnceWhenUnusable_infoOnceOnRecovery() {
        HoldingWatch watch = watch();
        watch.check();

        escrow = null;
        watch.check();
        watch.check();
        List<LogRecord> severe = at(Level.SEVERE);
        Assertions.assertEquals(1, severe.size(), String.valueOf(records));
        Assertions.assertTrue(severe.getFirst().getMessage().contains("no escrow player is set in settings.yml"),
                severe.getFirst().getMessage());
        Assertions.assertTrue(severe.getFirst().getMessage().contains("payment deadlines are paused"),
                severe.getFirst().getMessage());

        escrow = BANKER;
        watch.check();
        watch.check();
        Assertions.assertEquals(1, at(Level.INFO).size(), String.valueOf(records));
        Assertions.assertEquals(1, at(Level.SEVERE).size(), String.valueOf(records));
        verify(backend, times(2)).pausePaymentDeadlines();
    }

    @Test
    void unusableFromTheStart_logsSevereOnTheFirstRun() {
        escrow = null;
        HoldingWatch watch = watch();

        watch.check();

        Assertions.assertEquals(1, at(Level.SEVERE).size(), String.valueOf(records));
    }

    @Test
    void aPauseThatFails_isLoggedAndTriedNextRun() {
        escrow = null;
        org.mockito.Mockito.when(backend.pausePaymentDeadlines()).thenThrow(new IllegalStateException("db down"));
        HoldingWatch watch = watch();

        Assertions.assertInstanceOf(HoldingAccount.Unusable.class, watch.check());

        Assertions.assertEquals(1, at(Level.WARNING).size(), String.valueOf(records));
    }
}
