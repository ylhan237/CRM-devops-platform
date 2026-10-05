package com.crm.event_management_system.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="event_name",nullable = false, unique = true)
    private String name;

    @Column(name="start_date", nullable=false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;

    @Column(name="end_date", nullable=false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @Column(name="start_time", nullable=false)
    private LocalTime start_time;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @Column(name="end_time", nullable=false)
    private LocalTime end_time;

    @Column(name="expected_person", nullable=false)
    private int expected_person;

    // BigDecimal, and not float or double.
    //
    // A float carries 24 bits of mantissa, which is about 7 significant decimal
    // digits. A budget of 1 000 000.10 stored in one comes back as 1000000.125. That
    // is not rounding, it is the wrong number, and for a monetary amount it is the
    // kind of wrong that ends up in a reconciliation.
    //
    // BigDecimal maps to DECIMAL, which is what the column should have been from the
    // start. ddl-auto is update, so Hibernate widens the column on the next start;
    // existing rows are converted by the database.
    //
    // Jackson serialises BigDecimal as a plain JSON number, exactly as it did float,
    // so nothing on the Angular side changes.
    @Column(name="budget", nullable=false, precision = 19, scale = 2)
    private BigDecimal budget;

    @Column(name="description", nullable=false)
    private String description;

    @Column(name="type_id")
    private Long typeId;

    @Column(name="venue")
    private String venue;

}
