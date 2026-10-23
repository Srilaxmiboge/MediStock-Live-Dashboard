package com.medistock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row per real action taken in the system (sale, purchase, stock change, ...). */
@Entity
@Table(name = "activity_log", indexes = @Index(name = "idx_activity_time", columnList = "occurred_at"))
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_type", nullable = false, length = 30)
    private String type;

    @Column(length = 500)
    private String details;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public ActivityLog() {}

    public ActivityLog(String type, String details, BigDecimal amount, String username, LocalDateTime occurredAt) {
        this.type = type;
        this.details = details;
        this.amount = amount;
        this.username = username;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getDetails() { return details; }
    public BigDecimal getAmount() { return amount; }
    public String getUsername() { return username; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
