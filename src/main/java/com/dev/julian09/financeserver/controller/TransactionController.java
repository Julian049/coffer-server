package com.dev.julian09.financeserver.controller;

import com.dev.julian09.financeserver.model.dto.TransactionDTO;
import com.dev.julian09.financeserver.service.TransactionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    public List<TransactionDTO> findByOrderByDateDesc() {
        return  service.findByOrderByDateDesc();
    }

    @PostMapping
    public TransactionDTO save(@RequestBody TransactionDTO dto) {
        return service.save(dto);
    }

}
