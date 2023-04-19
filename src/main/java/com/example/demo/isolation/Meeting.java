package com.example.demo.isolation;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
class Meeting {

    @Id
    @GeneratedValue
    private Integer id;

    private String resource;

    private int startTime;

    private int endTime;

    Meeting(String resource, int startTime, int endTime) {
        this(null, resource, startTime, endTime);
    }

}
