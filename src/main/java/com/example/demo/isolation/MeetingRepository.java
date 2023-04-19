package com.example.demo.isolation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Integer> {

    // existing overlap: existingStart < end && existingEnd > start
    public boolean existsByResourceAndEndTimeGreaterThanAndStartTimeLessThan(String resource, int start, int end);

    public long countByResourceAndEndTimeGreaterThanAndStartTimeLessThan(String resource, int start, int end);

}
