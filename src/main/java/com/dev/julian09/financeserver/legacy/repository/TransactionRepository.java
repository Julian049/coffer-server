package com.dev.julian09.financeserver.legacy.repository;

import com.dev.julian09.financeserver.legacy.model.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByOrderByDateDesc();
}