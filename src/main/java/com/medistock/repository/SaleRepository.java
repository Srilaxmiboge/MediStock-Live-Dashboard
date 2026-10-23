package com.medistock.repository;

import com.medistock.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    List<Sale> findTop100ByOrderBySoldAtDesc();

    List<Sale> findTop5ByInvoiceNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCaseOrderBySoldAtDesc(
            String invoiceNumber, String customerName);

    /** Sales in [from, to) with their line items loaded, for charts and reports. */
    @Query("select distinct s from Sale s left join fetch s.items "
            + "where s.soldAt >= :rangeStart and s.soldAt < :rangeEnd order by s.soldAt desc")
    List<Sale> findWithItemsBetween(@Param("rangeStart") LocalDateTime rangeStart, @Param("rangeEnd") LocalDateTime rangeEnd);
}
