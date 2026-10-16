package com.dev.julian09.financeserver.legacy.service.impl;

import com.dev.julian09.financeserver.legacy.mapper.TransactionMapper;
import com.dev.julian09.financeserver.legacy.model.dto.TransactionDTO;
import com.dev.julian09.financeserver.legacy.model.entity.Transaction;
import com.dev.julian09.financeserver.legacy.repository.TransactionRepository;
import com.dev.julian09.financeserver.legacy.service.TransactionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final TransactionMapper mapper;

    public TransactionServiceImpl(TransactionRepository repository, TransactionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public TransactionDTO save(TransactionDTO dto) {
        Transaction entity = mapper.transactionDTOToTransaction(dto);
        entity.setSynced(true);

        if (entity.getValue().signum() < 0) {
            entity.setType(false);
        } else {
            entity.setType(true);
        }

        Transaction saved = repository.save(entity);
        return mapper.transactionToTransactionDTO(saved);
    }

    @Override
    public List<TransactionDTO> findByOrderByDateDesc() {
        List<Transaction> transactions = repository.findByOrderByDateDesc();
        List<TransactionDTO> dtos = new ArrayList<>();
        for (Transaction transaction : transactions) {
            TransactionDTO dto = mapper.transactionToTransactionDTO(transaction);
            dtos.add(dto);
        }
        return dtos;
    }
}
