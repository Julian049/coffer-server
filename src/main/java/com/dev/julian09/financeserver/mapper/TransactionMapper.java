package com.dev.julian09.financeserver.mapper;

import com.dev.julian09.financeserver.model.dto.TransactionDTO;
import com.dev.julian09.financeserver.model.entity.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionDTO transactionToTransactionDTO(Transaction transaction);
    Transaction transactionDTOToTransaction(TransactionDTO transactionDTO);

}
