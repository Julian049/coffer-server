package com.dev.julian09.financeserver.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter @Setter
public class TransactionDTO {
    private String localId;
    private String title;
    private double value;
    private boolean type;
    private String description;
    private OffsetDateTime date;
    private boolean synced;
}
