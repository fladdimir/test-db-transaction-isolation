package com.example.demo.isolation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OverlappingMeetingsTest {

    @Autowired
    MeetingRepository repository;

    @BeforeEach
    void beforeEach() {
        repository.deleteAllInBatch();
    }

    @ParameterizedTest
    @CsvSource({ "1,3", "1,6", "2,5", "2,6" })
    void test_query_overlap(int start, int end) {

        int existingStart = 2;
        int existingEnd = 5;

        repository.save(new Meeting("r1", existingStart, existingEnd));

        boolean result = repository.existsByResourceAndEndTimeGreaterThanAndStartTimeLessThan("r1", start, end);

        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "0,1", "0,2", "5,6", "6,7" })
    void test_query_no_overlap(int start, int end) {

        int existingStart = 2;
        int existingEnd = 5;

        repository.save(new Meeting("r1", existingStart, existingEnd));

        boolean result = repository.existsByResourceAndEndTimeGreaterThanAndStartTimeLessThan("r1", start, end);

        assertThat(result).isFalse();
    }

    @Test
    void test_query_no_overlap_different_resources() {

        int existingStart = 2;
        int existingEnd = 5;

        repository.save(new Meeting("r1", existingStart, existingEnd));

        boolean result = repository.existsByResourceAndEndTimeGreaterThanAndStartTimeLessThan("r2", existingStart,
                existingEnd);

        assertThat(result).isFalse();
    }

    @Test
    void test_query_count() {

        int existingStart = 2;
        int existingEnd = 5;

        long count = repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan("r1", existingStart,
                existingEnd);
        assertThat(count).isEqualTo(0);

        repository.save(new Meeting("r1", existingStart, existingEnd));

        count = repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan("r1", existingStart,
                existingEnd);
        assertThat(count).isEqualTo(1);

        repository.save(new Meeting("r1", existingStart, existingEnd));

        count = repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan("r1", existingStart,
                existingEnd);
        assertThat(count).isEqualTo(2);

        repository.save(new Meeting("r2", existingStart, existingEnd));

        count = repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan("r1", existingStart,
                existingEnd);
        assertThat(count).isEqualTo(2);

        count = repository.countByResourceAndEndTimeGreaterThanAndStartTimeLessThan("r2", existingStart,
                existingEnd);
        assertThat(count).isEqualTo(1);
    }

}
