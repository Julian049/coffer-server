package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
