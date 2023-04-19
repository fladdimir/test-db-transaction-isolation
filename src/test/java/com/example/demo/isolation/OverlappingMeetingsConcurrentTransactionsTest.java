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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class OverlappingMeetingsConcurrentTransactionsTest {

    @Autowired
    MeetingRepository repository;

    @Autowired
    PlatformTransactionManager tm;

    private TransactionTemplate txt;

    @BeforeEach
    void beforeEach() {
        repository.deleteAllInBatch();
        txt = new TransactionTemplate(tm);
    }

    String resource = "r1";
    int start = 0;
    int end = 1;

    @Test
    void test_insertCheck_serialTransactions() {

        Runnable doNothing = () -> {
        };
        checkOverlapAndInsertInTx(resource, start, end, doNothing);
        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource, start, end))
                .isEqualTo(1);

        // no 2nd insert in subsequent transaction:
        checkOverlapAndInsertInTx(resource, start, end, doNothing);
        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource, start, end))
                .isEqualTo(1);
    }

    @Test
    void test_insertCheck_concurrentTransactions_repeatableRead() throws Exception {

        txt.setIsolationLevel(TransactionTemplate.ISOLATION_REPEATABLE_READ);

        var cyclicBarrier = new CyclicBarrier(2);
        Runnable waitForConcurrentThread = waitForConcurrentThread(cyclicBarrier);

        var t1 = new Thread(() -> checkOverlapAndInsertInTx(resource, start, end, waitForConcurrentThread));
        var t2 = new Thread(() -> checkOverlapAndInsertInTx(resource, start, end, waitForConcurrentThread));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource, start, end))
                .isEqualTo(2); // two concurrent inserts, no conflict detected !
    }

    @Test
    void test_insertCheck_concurrentTransactions_serializable() throws Exception {

        txt.setIsolationLevel(TransactionTemplate.ISOLATION_SERIALIZABLE);

        var cyclicBarrier = new CyclicBarrier(2);
        Runnable waitForConcurrentThread = waitForConcurrentThread(cyclicBarrier);

        var t1 = new Thread(() -> checkOverlapAndInsertInTx(resource, start, end, waitForConcurrentThread));
        var t2 = new Thread(() -> checkOverlapAndInsertInTx(resource, start, end, waitForConcurrentThread));

        List<Throwable> exceptions = new LinkedList<>();
        t1.setUncaughtExceptionHandler((t, e) -> exceptions.add(e));
        t2.setUncaughtExceptionHandler((t, e) -> exceptions.add(e));

        t1.start();
        t2.start();
        t1.join();
        t2.join();
        // conflict detected, just one insert
        assertThat(repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource, start, end))
                .isEqualTo(1);

        // one insert failed:
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0)).hasRootCauseInstanceOf(PSQLException.class).rootCause()
                .hasMessageContaining(
                        "ERROR: could not serialize access due to read/write dependencies among transactions");
    }

    private Runnable waitForConcurrentThread(CyclicBarrier cyclicBarrier) {
        return () -> {
            try {
                cyclicBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    void checkOverlapAndInsertInTx(String resource, int start, int end, Runnable actionBetweenCheckAndInsert) {
        txt.executeWithoutResult(txs -> checkOverlapAndInsert(resource, start, end, actionBetweenCheckAndInsert));
    }

    private void checkOverlapAndInsert(String resource, int start, int end, Runnable actionBetweenCheckAndInsert) {
        if (!repository.existsByResourceAndEndTimeGreaterThanAndStartTimeLessThan(resource, start, end)) {
            actionBetweenCheckAndInsert.run();
            repository.save(new Meeting(resource, start, end));
        }
    }

}
