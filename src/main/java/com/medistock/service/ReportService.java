package com.medistock.service;

import com.medistock.dto.InsightDtos.*;
import com.medistock.model.Medicine;
import com.medistock.model.Purchase;
import com.medistock.model.Sale;
import com.medistock.model.SaleItem;
import com.medistock.repository.MedicineRepository;
import com.medistock.repository.PurchaseRepository;
import com.medistock.repository.SaleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Sales, purchase and stock totals for any date range. */
@Service
public class ReportService {
    private static final long MAX_RANGE_DAYS = 366;

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final MedicineRepository medicineRepository;
    private final SettingsService settingsService;

    public ReportService(SaleRepository saleRepository, PurchaseRepository purchaseRepository,
                         MedicineRepository medicineRepository, SettingsService settingsService) {
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.medicineRepository = medicineRepository;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public ReportSummary summarize(String fromText, String toText) {
        LocalDate today = LocalDate.now();
        LocalDate to = parse(toText, today);
        LocalDate from = parse(fromText, to.minusDays(29));
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The start date must not be after the end date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a range of 366 days or fewer.");
        }

        // ---- Sales
        List<Sale> sales = saleRepository.findWithItemsBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        Map<LocalDate, BigDecimal> dayRevenue = new HashMap<>();
        Map<LocalDate, Integer> dayBills = new HashMap<>();
        Map<LocalDate, Integer> dayUnits = new HashMap<>();
        Map<String, String> itemNames = new HashMap<>();
        Map<String, int[]> itemUnits = new HashMap<>();
        Map<String, BigDecimal> itemRevenue = new HashMap<>();
        BigDecimal revenue = BigDecimal.ZERO;
        int units = 0;

        for (Sale sale : sales) {
            LocalDate day = sale.getSoldAt().toLocalDate();
            revenue = revenue.add(sale.getTotal());
            dayRevenue.merge(day, sale.getTotal(), BigDecimal::add);
            dayBills.merge(day, 1, Integer::sum);
            for (SaleItem item : sale.getItems()) {
                units += item.getQuantity();
                dayUnits.merge(day, item.getQuantity(), Integer::sum);
                String key = item.getMedicineName().trim().toLowerCase(Locale.ROOT);
                itemNames.putIfAbsent(key, item.getMedicineName().trim());
                itemUnits.computeIfAbsent(key, k -> new int[1])[0] += item.getQuantity();
                itemRevenue.merge(key, item.getLineTotal(), BigDecimal::add);
            }
        }
        BigDecimal averageBill = sales.isEmpty() ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(sales.size()), 2, RoundingMode.HALF_UP);

        List<DailySales> daily = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            daily.add(new DailySales(day, dayBills.getOrDefault(day, 0), dayUnits.getOrDefault(day, 0),
                    dayRevenue.getOrDefault(day, BigDecimal.ZERO)));
        }

        List<TopMedicine> top = new ArrayList<>();
        for (String key : itemNames.keySet()) {
            top.add(new TopMedicine(itemNames.get(key), itemUnits.get(key)[0], itemRevenue.get(key)));
        }
        top.sort(Comparator.comparing(TopMedicine::revenue).reversed().thenComparing(TopMedicine::name));
        if (top.size() > 10) {
            top = new ArrayList<>(top.subList(0, 10));
        }

        // ---- Purchases
        List<Purchase> purchases = purchaseRepository.findWithDetailsBetween(from, to);
        Map<String, Integer> supplierOrders = new HashMap<>();
        Map<String, Integer> supplierUnits = new HashMap<>();
        Map<String, BigDecimal> supplierSpend = new HashMap<>();
        BigDecimal purchaseSpend = BigDecimal.ZERO;
        int purchaseUnits = 0;
        for (Purchase purchase : purchases) {
            BigDecimal total = purchase.getUnitCost().multiply(BigDecimal.valueOf(purchase.getQuantity()));
            purchaseSpend = purchaseSpend.add(total);
            purchaseUnits += purchase.getQuantity();
            String supplier = purchase.getSupplier().getName();
            supplierOrders.merge(supplier, 1, Integer::sum);
            supplierUnits.merge(supplier, purchase.getQuantity(), Integer::sum);
            supplierSpend.merge(supplier, total, BigDecimal::add);
        }
        List<SupplierSpend> suppliers = new ArrayList<>();
        for (String supplier : supplierSpend.keySet()) {
            suppliers.add(new SupplierSpend(supplier, supplierOrders.get(supplier), supplierUnits.get(supplier),
                    supplierSpend.get(supplier).setScale(2, RoundingMode.HALF_UP)));
        }
        suppliers.sort(Comparator.comparing(SupplierSpend::spend).reversed().thenComparing(SupplierSpend::supplier));

        // ---- Stock on hand today (not date-ranged)
        SettingsView settings = settingsService.get();
        List<Medicine> medicines = medicineRepository.findAll();
        LocalDate expiryLimit = today.plusDays(settings.expiryDays());
        Map<String, String> categoryNames = new HashMap<>();
        Map<String, int[]> categoryLots = new HashMap<>();
        Map<String, long[]> categoryUnits = new HashMap<>();
        Map<String, BigDecimal> categoryValue = new HashMap<>();
        BigDecimal retailValue = BigDecimal.ZERO;
        BigDecimal expiredValue = BigDecimal.ZERO;
        long stockUnits = 0;
        int expiredLots = 0;
        int expiringLots = 0;
        int lowStockLots = 0;

        for (Medicine medicine : medicines) {
            BigDecimal value = BigDecimal.valueOf(medicine.getPrice())
                    .multiply(BigDecimal.valueOf(Math.max(0, medicine.getQuantity()))).setScale(2, RoundingMode.HALF_UP);
            retailValue = retailValue.add(value);
            stockUnits += Math.max(0, medicine.getQuantity());

            String category = StringUtils.hasText(medicine.getCategory()) ? medicine.getCategory().trim() : "Uncategorised";
            String key = category.toLowerCase(Locale.ROOT);
            categoryNames.putIfAbsent(key, category);
            categoryLots.computeIfAbsent(key, k -> new int[1])[0] += 1;
            categoryUnits.computeIfAbsent(key, k -> new long[1])[0] += Math.max(0, medicine.getQuantity());
            categoryValue.merge(key, value, BigDecimal::add);

            LocalDate expiry = parseExpiry(medicine.getExpiryDate());
            if (expiry != null && medicine.getQuantity() > 0) {
                if (expiry.isBefore(today)) {
                    expiredLots++;
                    expiredValue = expiredValue.add(value);
                } else if (!expiry.isAfter(expiryLimit)) {
                    expiringLots++;
                }
            }
            if (medicine.getQuantity() <= settings.lowStockThreshold()) {
                lowStockLots++;
            }
        }
        List<CategoryValue> inventory = new ArrayList<>();
        for (String key : categoryNames.keySet()) {
            inventory.add(new CategoryValue(categoryNames.get(key), categoryLots.get(key)[0],
                    categoryUnits.get(key)[0], categoryValue.get(key)));
        }
        inventory.sort(Comparator.comparing(CategoryValue::retailValue).reversed().thenComparing(CategoryValue::name));

        InventoryHealth health = new InventoryHealth(medicines.size(), stockUnits, retailValue, expiredLots,
                expiredValue, expiringLots, lowStockLots);
        return new ReportSummary(from, to, revenue, sales.size(), units, averageBill,
                purchaseSpend.setScale(2, RoundingMode.HALF_UP), purchases.size(), purchaseUnits, daily, top,
                suppliers, inventory, health);
    }

    private LocalDate parse(String text, LocalDate fallback) {
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates must look like 2026-10-31.");
        }
    }

    private LocalDate parseExpiry(String expiryDate) {
        if (!StringUtils.hasText(expiryDate)) {
            return null;
        }
        try {
            return LocalDate.parse(expiryDate.trim());
        } catch (DateTimeException exception) {
            return null;
        }
    }
}
