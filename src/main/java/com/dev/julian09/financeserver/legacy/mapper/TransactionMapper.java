package com.dev.julian09.financeserver.legacy.mapper;

import com.dev.julian09.financeserver.legacy.model.dto.TransactionDTO;
import com.dev.julian09.financeserver.legacy.model.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "serverId", ignore = true)
    @Mapping(target = "localId", expression = "java(java.util.UUID.fromString(transactionDTO.getLocalId()))")
    Transaction transactionDTOToTransaction(TransactionDTO transactionDTO);

    @Mapping(target = "localId", expression = "java(transaction.getLocalId().toString())")
    @Mapping(target = "synced", constant = "true")
    TransactionDTO transactionToTransactionDTO(Transaction transaction);
}
