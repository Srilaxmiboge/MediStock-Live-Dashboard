package com.medistock.service;

import com.medistock.dto.InsightDtos.*;
import com.medistock.model.AppSetting;
import com.medistock.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Pharmacy settings stored in the database. application.properties only supplies the first-run defaults;
 * once saved from the Settings screen, the database value wins.
 */
@Service
public class SettingsService {
    private static final String NAME = "pharmacy.name";
    private static final String ADDRESS = "pharmacy.address";
    private static final String PHONE = "pharmacy.phone";
    private static final String EXPIRY_DAYS = "alerts.expiry-days";
    private static final String LOW_STOCK = "alerts.low-stock-threshold";

    private final AppSettingRepository settingRepository;
    private final MedicineRepository medicineRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final SaleRepository saleRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityService activityService;

    @Value("${medistock.alerts.expiry-days:30}")
    private int defaultExpiryDays;

    @Value("${medistock.alerts.low-stock-threshold:10}")
    private int defaultLowStockThreshold;

    public SettingsService(AppSettingRepository settingRepository, MedicineRepository medicineRepository,
                           SupplierRepository supplierRepository, PurchaseRepository purchaseRepository,
                           SaleRepository saleRepository, ActivityLogRepository activityLogRepository,
                           ActivityService activityService) {
        this.settingRepository = settingRepository;
        this.medicineRepository = medicineRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseRepository = purchaseRepository;
        this.saleRepository = saleRepository;
        this.activityLogRepository = activityLogRepository;
        this.activityService = activityService;
    }

    @Transactional(readOnly = true)
    public SettingsView get() {
        Map<String, String> stored = new HashMap<>();
        for (AppSetting setting : settingRepository.findAll()) {
            if (setting.getValue() != null) {
                stored.put(setting.getKey(), setting.getValue());
            }
        }
        return new SettingsView(
                text(stored, NAME, "MediStock"),
                text(stored, ADDRESS, ""),
                text(stored, PHONE, ""),
                number(stored, EXPIRY_DAYS, defaultExpiryDays),
                number(stored, LOW_STOCK, defaultLowStockThreshold));
    }

    @Transactional
    public SettingsView update(SettingsRequest request) {
        if (request == null || !StringUtils.hasText(request.pharmacyName())) {
            throw badRequest("Pharmacy name is required.");
        }
        if (request.pharmacyName().trim().length() > 120) {
            throw badRequest("Pharmacy name must be 120 characters or fewer.");
        }
        String address = request.pharmacyAddress() == null ? "" : request.pharmacyAddress().trim();
        String phone = request.pharmacyPhone() == null ? "" : request.pharmacyPhone().trim();
        if (address.length() > 240 || phone.length() > 40) {
            throw badRequest("Address must be 240 characters or fewer and phone 40 or fewer.");
        }
        if (request.expiryDays() == null || request.expiryDays() < 1 || request.expiryDays() > 365) {
            throw badRequest("Near-expiry window must be between 1 and 365 days.");
        }
        if (request.lowStockThreshold() == null || request.lowStockThreshold() < 0
                || request.lowStockThreshold() > 100000) {
            throw badRequest("Low-stock threshold must be between 0 and 100000 units.");
        }
        settingRepository.save(new AppSetting(NAME, request.pharmacyName().trim()));
        settingRepository.save(new AppSetting(ADDRESS, address));
        settingRepository.save(new AppSetting(PHONE, phone));
        settingRepository.save(new AppSetting(EXPIRY_DAYS, String.valueOf(request.expiryDays())));
        settingRepository.save(new AppSetting(LOW_STOCK, String.valueOf(request.lowStockThreshold())));
        activityService.record("Settings", "Updated pharmacy and alert settings");
        return get();
    }

    @Transactional(readOnly = true)
    public SystemInfo systemInfo() {
        return new SystemInfo(medicineRepository.count(), supplierRepository.count(), purchaseRepository.count(),
                saleRepository.count(), activityLogRepository.count(), LocalDateTime.now(),
                ZoneId.systemDefault().getId());
    }

    private static String text(Map<String, String> stored, String key, String fallback) {
        String value = stored.get(key);
        if (key.equals(NAME)) {
            return StringUtils.hasText(value) ? value : fallback;
        }
        return value == null ? fallback : value;
    }

    private static int number(Map<String, String> stored, String key, int fallback) {
        try {
            return Integer.parseInt(stored.getOrDefault(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
