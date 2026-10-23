package com.medistock.service;

import com.medistock.model.ActivityLog;
import com.medistock.model.Purchase;
import com.medistock.model.Sale;
import com.medistock.repository.ActivityLogRepository;
import com.medistock.repository.PurchaseRepository;
import com.medistock.repository.SaleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Writes the audit trail that powers "Recent Activity" on the dashboard. */
@Service
public class ActivityService {
    private final ActivityLogRepository activityLogRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;

    public ActivityService(ActivityLogRepository activityLogRepository, SaleRepository saleRepository,
                           PurchaseRepository purchaseRepository) {
        this.activityLogRepository = activityLogRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
    }

    /** Records an action by the currently signed-in user, as part of the caller's transaction. */
    @Transactional
    public void record(String type, String details, BigDecimal amount) {
        activityLogRepository.save(new ActivityLog(type, truncate(details, 480), amount, currentUsername(),
                LocalDateTime.now()));
    }

    public void record(String type, String details) {
        record(type, details, null);
    }

    public static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * Existing databases already hold sales and purchases from before the activity log existed.
     * On first start, import the latest ones so Recent Activity is not empty. Who made them is unknown.
     */
    @Transactional
    public void backfillIfEmpty() {
        if (activityLogRepository.count() > 0) {
            return;
        }
        List<ActivityLog> rows = new ArrayList<>();
        for (Sale sale : saleRepository.findTop100ByOrderBySoldAtDesc()) {
            int units = sale.getItems().stream().mapToInt(item -> item.getQuantity()).sum();
            String customer = sale.getCustomerName() == null ? "Walk-in" : sale.getCustomerName();
            rows.add(new ActivityLog("Sale",
                    "Invoice " + sale.getInvoiceNumber() + " · " + customer + " · " + units
                            + (units == 1 ? " unit" : " units"),
                    sale.getTotal(), null, sale.getSoldAt()));
        }
        for (Purchase purchase : purchaseRepository.findTop100ByOrderByPurchaseDateDescIdDesc()) {
            BigDecimal total = purchase.getUnitCost().multiply(BigDecimal.valueOf(purchase.getQuantity()));
            rows.add(new ActivityLog("Purchase",
                    "Received " + purchase.getQuantity() + " × " + purchase.getMedicine().getName() + " from "
                            + purchase.getSupplier().getName(),
                    total, null, purchase.getPurchaseDate().atStartOfDay()));
        }
        activityLogRepository.saveAll(rows);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
