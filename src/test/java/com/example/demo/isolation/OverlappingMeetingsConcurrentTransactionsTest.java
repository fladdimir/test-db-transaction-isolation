package com.example.demo.isolation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class OverlappingMeetingsConcurrentTransactionsTest {

    @Autowired
    MeetingRepository repository;

    @Autowired
    PlatformTransactionManager tm;

    private TransactionTemplate txt;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void beforeEach() {
        repository.deleteAllInBatch();
        txt = new TransactionTemplate(tm);
    }

    String resource1 = "r1";
    String resource2 = "r2";
    int start = 0;
    int end = 1;

    @Test
    void test_insertCheck_serialTransactions() {

        Runnable doNothing = () -> {
        };
        checkOverlapAndInsertInTx(resource1, start, end, doNothing);
        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource1, start, end))
                .isEqualTo(1);

        // no 2nd insert in subsequent transaction:
        checkOverlapAndInsertInTx(resource1, start, end, doNothing);
        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource1, start, end))
                .isEqualTo(1);
    }

    @Test
    void test_insertCheck_concurrentTransactions_repeatableRead() throws Exception {

        txt.setIsolationLevel(TransactionTemplate.ISOLATION_REPEATABLE_READ);

        var cyclicBarrier = new CyclicBarrier(2);
        Runnable waitForConcurrentThread = await(cyclicBarrier);

        var t1 = new Thread(() -> checkOverlapAndInsertInTx(resource1, start, end, waitForConcurrentThread));
        var t2 = new Thread(() -> checkOverlapAndInsertInTx(resource1, start, end, waitForConcurrentThread));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource1, start, end))
                .isEqualTo(2); // two concurrent inserts, no conflict detected !
    }

    @Test
    void test_insertCheck_concurrentTransactions_serializable() throws Exception {

        txt.setIsolationLevel(TransactionTemplate.ISOLATION_SERIALIZABLE);

        var cyclicBarrier = new CyclicBarrier(2);
        Runnable waitForConcurrentThread = await(cyclicBarrier);

        var t1 = new Thread(() -> checkOverlapAndInsertInTx(resource1, start, end, waitForConcurrentThread));
        var t2 = new Thread(() -> checkOverlapAndInsertInTx(resource1, start, end, waitForConcurrentThread));

        List<Throwable> exceptions = new LinkedList<>();
        t1.setUncaughtExceptionHandler((t, e) -> exceptions.add(e));
        t2.setUncaughtExceptionHandler((t, e) -> exceptions.add(e));

        t1.start();
        t2.start();
        t1.join();
        t2.join();
        // conflict detected, just one insert
        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource1, start, end))
                .isEqualTo(1);

        // one insert failed:
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0)).hasRootCauseInstanceOf(PSQLException.class).rootCause()
                .hasMessageContaining(
                        "ERROR: could not serialize access due to read/write dependencies among transactions");
    }

    @Test
    void test_insertCheck_concurrentTransactions_serializable_differentResources() throws Exception {

        // no conflict since inserts are different resources
        // this may only work when executing the check-query via index, since a table
        // scan will "read lock" the complete table
        // https://stackoverflow.com/a/42303225

        txt.setIsolationLevel(TransactionTemplate.ISOLATION_SERIALIZABLE);

        var cyclicBarrier = new CyclicBarrier(2);
        Runnable waitForConcurrentThread = await(cyclicBarrier);

        var t1 = new Thread(() -> checkOverlapAndInsertInTx(resource1, start, end, waitForConcurrentThread));
        var t2 = new Thread(() -> checkOverlapAndInsertInTx(resource2, start, end, waitForConcurrentThread));

        List<Throwable> exceptions = new LinkedList<>();
        t1.setUncaughtExceptionHandler((t, e) -> exceptions.add(e));
        t2.setUncaughtExceptionHandler((t, e) -> exceptions.add(e));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        if (!exceptions.isEmpty()) {
            var e = new IllegalStateException();
            exceptions.forEach(e::addSuppressed);
            throw e;
        }

        // no conflicts (naively expected)
        // -> depending on the lock used by the DB a false positive serialization
        // exception might still be thrown on one of the commits
        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource1, start, end))
                .isEqualTo(1);

        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource2, start, end))
                .isEqualTo(1);

        assertThat(exceptions).isEmpty();
    }

    private Runnable await(CyclicBarrier cyclicBarrier) {
        return () -> {
            try {
                cyclicBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    void checkOverlapAndInsertInTx(String resource, int start, int end, Runnable actionBetweenCheckAndInsert) {
        txt.executeWithoutResult(txs -> {
            checkOverlapAndInsert(resource, start, end, actionBetweenCheckAndInsert);
        });
    }

    private void checkOverlapAndInsert(String resource, int start, int end, Runnable actionBetweenCheckAndInsert) {
        if (!repository.existsByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource, start, end)) {
            actionBetweenCheckAndInsert.run();
            repository.save(new Meeting(resource, start, end));
        }
    }

}
