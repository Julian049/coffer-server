package com.dev.julian09.financeserver.legacy.service;

import com.dev.julian09.financeserver.legacy.model.dto.TransactionDTO;
import com.dev.julian09.financeserver.legacy.model.entity.Transaction;

import java.util.List;

public interface TransactionService {
    TransactionDTO save(TransactionDTO dto);
    List<TransactionDTO> findByOrderByDateDesc();
}