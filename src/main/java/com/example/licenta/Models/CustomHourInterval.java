package com.example.licenta.Models;

import com.example.licenta.Enum.ParkingLot.DayOfWeek;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "custom_hour_intervals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomHourInterval {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @Column(name = "start_time")
    private String startTime;

    @Column(name = "end_time")
    private String endTime;

    @ElementCollection
    @CollectionTable(name = "custom_hour_interval_days", joinColumns = @JoinColumn(name = "interval_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day")
    private List<DayOfWeek> days = new ArrayList<>();
}