package com.medistock.repository;

import com.medistock.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    boolean existsByMedicineId(Long medicineId);
}