package com.medistock.dto;

import com.medistock.model.Medicine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class WorkflowDtos {
    private WorkflowDtos() {}

    public record SupplierRequest(String name, String contactName, String phone, String email, String address) {}

    public record PurchaseRequest(Long supplierId, Long medicineId, Integer quantity, BigDecimal unitCost,
                                  LocalDate purchaseDate) {}

    public record PurchaseView(Long id, String supplierName, Long medicineId, String medicineName,
                               int quantity, BigDecimal unitCost, BigDecimal lineTotal, LocalDate purchaseDate) {}

    public record SaleLineRequest(Long medicineId, Integer quantity) {}

    public record SaleRequest(String customerName, List<SaleLineRequest> items) {}

    public record SaleLineView(Long medicineId, String medicineName, int quantity, BigDecimal unitPrice,
                               BigDecimal lineTotal) {}

    public record SaleView(Long id, String invoiceNumber, String customerName, LocalDateTime soldAt,
                           BigDecimal total, List<SaleLineView> items) {}

    public record AlertSummary(List<Medicine> expired, List<Medicine> expiringSoon, List<Medicine> lowStock,
                               int expiryDays, int lowStockThreshold) {}
}