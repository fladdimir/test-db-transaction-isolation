package com.example.demo.isolation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = { @Index(name = "OVLP_IDX", columnList = "res,stt,ent") })
class Meeting {

    @Id
    @GeneratedValue
    private Integer id;

    @Column(name = "res")
    private String resource;

    @Column(name = "stt")
    private int startTime;

    @Column(name = "ent")
    private int endTime;

    private String extraText;

    Meeting(String resource, int startTime, int endTime) {
        this(null, resource, startTime, endTime, "");
    }

}
