package com.medistock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Response/request shapes for the dashboard, reports, settings and account screens. */
public final class InsightDtos {
    private InsightDtos() {}

    // ---- Dashboard
    public record TrendPoint(LocalDate date, BigDecimal revenue, int bills, int units) {}

    public record CategorySlice(String name, int lots, int units, int percent) {}

    public record ActivityView(Long id, String type, String details, BigDecimal amount, String user,
                               LocalDateTime occurredAt) {}

    public record DashboardSummary(List<TrendPoint> salesTrend, List<CategorySlice> categories, int totalLots,
                                   List<ActivityView> recentActivity, LocalDateTime generatedAt) {}

    // ---- Reports
    public record DailySales(LocalDate date, int bills, int units, BigDecimal revenue) {}

    public record TopMedicine(String name, int units, BigDecimal revenue) {}

    public record SupplierSpend(String supplier, int orders, int units, BigDecimal spend) {}

    public record CategoryValue(String name, int lots, long units, BigDecimal retailValue) {}

    public record InventoryHealth(int lots, long units, BigDecimal retailValue, int expiredLots,
                                  BigDecimal expiredValue, int expiringLots, int lowStockLots) {}

    public record ReportSummary(LocalDate from, LocalDate to, BigDecimal revenue, int bills, int units,
                                BigDecimal averageBill, BigDecimal purchaseSpend, int purchaseOrders,
                                int purchaseUnits, List<DailySales> daily, List<TopMedicine> topMedicines,
                                List<SupplierSpend> suppliers, List<CategoryValue> inventory,
                                InventoryHealth health) {}

    // ---- Settings
    public record SettingsView(String pharmacyName, String pharmacyAddress, String pharmacyPhone,
                               int expiryDays, int lowStockThreshold) {}

    public record SettingsRequest(String pharmacyName, String pharmacyAddress, String pharmacyPhone,
                                  Integer expiryDays, Integer lowStockThreshold) {}

    public record SystemInfo(long medicines, long suppliers, long purchases, long sales, long activities,
                             LocalDateTime serverTime, String timezone) {}

    // ---- Account
    public record ProfileView(String username, String fullName, String displayName, String email, String role,
                              String roleLabel) {}

    public record ProfileRequest(String fullName, String email) {}

    public record PasswordRequest(String currentPassword, String newPassword) {}
}
