package com.example.licenta.Models;

import com.example.licenta.Enum.ParkingLot.DayOfWeek;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "price_intervals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceInterval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_time")
    private String startTime;

    @Column(name = "end_time")
    private String endTime;

    @ElementCollection
    @CollectionTable(name = "price_interval_days", joinColumns = @JoinColumn(name = "interval_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day")
    private List<DayOfWeek> days = new ArrayList<>();

    private Double price;

    private Integer duration;
}