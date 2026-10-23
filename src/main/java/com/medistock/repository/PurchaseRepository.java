package com.medistock.repository;

import com.medistock.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findTop100ByOrderByPurchaseDateDescIdDesc();
    boolean existsBySupplierId(Long supplierId);
    boolean existsByMedicineId(Long medicineId);

    /** Purchases dated within [from, to] (inclusive) with supplier and medicine loaded, for reports. */
    @Query("select p from Purchase p join fetch p.supplier join fetch p.medicine "
            + "where p.purchaseDate >= :rangeStart and p.purchaseDate <= :rangeEnd order by p.purchaseDate desc")
    List<Purchase> findWithDetailsBetween(@Param("rangeStart") LocalDate rangeStart, @Param("rangeEnd") LocalDate rangeEnd);
}
