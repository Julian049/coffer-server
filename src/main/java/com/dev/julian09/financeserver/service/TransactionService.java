package com.dev.julian09.financeserver.service;

import com.dev.julian09.financeserver.model.dto.TransactionDTO;
import com.dev.julian09.financeserver.model.entity.Transaction;

import java.util.List;

public interface TransactionService {
    TransactionDTO save(TransactionDTO dto);
    List<TransactionDTO> findByOrderByDateDesc();
}