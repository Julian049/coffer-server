package com.dev.julian09.financeserver.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "server_id")
    private Long serverId;

    @Column(name = "local_id", nullable = false, unique = true)
    private UUID localId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private double value;

    @Column(nullable = false)
    private boolean type;

    @Column
    private String description;

    @Column(nullable = false)
    private OffsetDateTime date;

    @Column(name = "synchronized", nullable = false)
    private boolean synced;

}
