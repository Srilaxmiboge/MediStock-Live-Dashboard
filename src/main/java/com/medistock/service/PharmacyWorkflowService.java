package com.medistock.service;

import com.medistock.dto.InsightDtos.SettingsView;
import com.medistock.dto.WorkflowDtos.*;
import com.medistock.model.*;
import com.medistock.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PharmacyWorkflowService {
    private final MedicineRepository medicineRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SettingsService settingsService;
    private final ActivityService activityService;

    public PharmacyWorkflowService(MedicineRepository medicineRepository, SupplierRepository supplierRepository,
                                   PurchaseRepository purchaseRepository, SaleRepository saleRepository,
                                   SaleItemRepository saleItemRepository, SettingsService settingsService,
                                   ActivityService activityService) {
        this.medicineRepository = medicineRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseRepository = purchaseRepository;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.settingsService = settingsService;
        this.activityService = activityService;
    }

    @Transactional(readOnly = true)
    public List<Supplier> getSuppliers() {
        return supplierRepository.findAll();
    }

    @Transactional
    public Supplier addSupplier(SupplierRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw badRequest("Supplier name is required.");
        }
        Supplier supplier = new Supplier();
        supplier.setName(request.name().trim());
        supplier.setContactName(clean(request.contactName()));
        supplier.setPhone(clean(request.phone()));
        supplier.setEmail(clean(request.email()));
        supplier.setAddress(clean(request.address()));
        Supplier saved = supplierRepository.save(supplier);
        activityService.record("Supplier", "Added supplier " + saved.getName());
        return saved;
    }

    @Transactional
    public Supplier updateSupplier(Long supplierId, SupplierRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw badRequest("Supplier name is required.");
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> notFound("Supplier not found."));
        supplier.setName(request.name().trim());
        supplier.setContactName(clean(request.contactName()));
        supplier.setPhone(clean(request.phone()));
        supplier.setEmail(clean(request.email()));
        supplier.setAddress(clean(request.address()));
        Supplier saved = supplierRepository.save(supplier);
        activityService.record("Supplier", "Updated supplier " + saved.getName());
        return saved;
    }

    @Transactional
    public void deleteSupplier(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> notFound("Supplier not found."));
        if (purchaseRepository.existsBySupplierId(supplierId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier has purchase history and cannot be deleted.");
        }
        supplierRepository.delete(supplier);
        activityService.record("Supplier", "Deleted supplier " + supplier.getName());
    }

    @Transactional(readOnly = true)
    public List<PurchaseView> getPurchases() {
        return purchaseRepository.findTop100ByOrderByPurchaseDateDescIdDesc().stream()
                .map(this::toPurchaseView)
                .toList();
    }

    @Transactional
    public PurchaseView recordPurchase(PurchaseRequest request) {
        if (request == null || request.supplierId() == null || request.medicineId() == null
                || request.quantity() == null || request.quantity() <= 0 || request.unitCost() == null
                || request.unitCost().signum() < 0 || request.purchaseDate() == null) {
            throw badRequest("Select a supplier and medicine lot, and enter a valid quantity, cost, and date.");
        }

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> notFound("Supplier not found."));
        Medicine medicine = medicineRepository.findById(request.medicineId())
                .orElseThrow(() -> notFound("Medicine lot not found."));

        try {
            medicine.setQuantity(Math.addExact(medicine.getQuantity(), request.quantity()));
        } catch (ArithmeticException exception) {
            throw badRequest("Purchase quantity exceeds the supported stock limit.");
        }
        medicineRepository.save(medicine);

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setMedicine(medicine);
        purchase.setQuantity(request.quantity());
        purchase.setUnitCost(request.unitCost().setScale(2, RoundingMode.HALF_UP));
        purchase.setPurchaseDate(request.purchaseDate());
        Purchase saved = purchaseRepository.save(purchase);
        activityService.record("Purchase",
                "Received " + request.quantity() + " × " + medicine.getName() + " from " + supplier.getName(),
                saved.getUnitCost().multiply(BigDecimal.valueOf(saved.getQuantity())).setScale(2, RoundingMode.HALF_UP));
        return toPurchaseView(saved);
    }

    @Transactional(readOnly = true)
    public List<SaleView> getSales() {
        return saleRepository.findTop100ByOrderBySoldAtDesc().stream()
                .map(this::toSaleView)
                .toList();
    }

    @Transactional(readOnly = true)
    public SaleView getSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> notFound("Sale not found."));
        return toSaleView(sale);
    }

    @Transactional
    public SaleView recordSale(SaleRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw badRequest("Add at least one medicine to the sale.");
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (SaleLineRequest line : request.items()) {
            if (line == null || line.medicineId() == null || line.quantity() == null || line.quantity() <= 0) {
                throw badRequest("Every sale line needs a medicine lot and a positive quantity.");
            }
            try {
                quantities.merge(line.medicineId(), line.quantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw badRequest("Sale quantity exceeds the supported limit.");
            }
        }

        Map<Long, Medicine> medicines = medicineRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Medicine::getId, Function.identity()));
        LocalDate today = LocalDate.now();
        for (Map.Entry<Long, Integer> requested : quantities.entrySet()) {
            Medicine medicine = medicines.get(requested.getKey());
            if (medicine == null) {
                throw notFound("A selected medicine lot was not found.");
            }
            if (medicine.getQuantity() < requested.getValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock for " + medicine.getName() + ". Available: " + medicine.getQuantity() + ".");
            }
            LocalDate expiry = parseExpiry(medicine.getExpiryDate());
            if (expiry != null && expiry.isBefore(today)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        medicine.getName() + " has expired and cannot be sold.");
            }
        }

        Sale sale = new Sale();
        sale.setInvoiceNumber("MS-" + LocalDate.now().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        sale.setCustomerName(clean(request.customerName()));
        sale.setSoldAt(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> requested : quantities.entrySet()) {
            Medicine medicine = medicines.get(requested.getKey());
            int quantity = requested.getValue();
            BigDecimal unitPrice = BigDecimal.valueOf(medicine.getPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

            medicine.setQuantity(medicine.getQuantity() - quantity);
            medicineRepository.save(medicine);

            SaleItem item = new SaleItem();
            item.setMedicine(medicine);
            item.setMedicineName(medicine.getName());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setLineTotal(lineTotal);
            sale.addItem(item);
            total = total.add(lineTotal);
        }
        sale.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        Sale saved = saleRepository.save(sale);
        int soldUnits = quantities.values().stream().mapToInt(Integer::intValue).sum();
        activityService.record("Sale",
                "Invoice " + saved.getInvoiceNumber() + " · "
                        + (saved.getCustomerName() == null ? "Walk-in" : saved.getCustomerName()) + " · "
                        + soldUnits + (soldUnits == 1 ? " unit" : " units"),
                saved.getTotal());
        return toSaleView(saved);
    }

    @Transactional(readOnly = true)
    public AlertSummary getAlerts() {
        LocalDate today = LocalDate.now();
        SettingsView settings = settingsService.get();
        int expiryDays = settings.expiryDays();
        int lowStockThreshold = settings.lowStockThreshold();
        LocalDate expiryLimit = today.plusDays(Math.max(0, expiryDays));
        List<Medicine> allMedicines = medicineRepository.findAll();

        List<Medicine> expired = allMedicines.stream()
                .filter(medicine -> {
                    LocalDate expiry = parseExpiry(medicine.getExpiryDate());
                    return expiry != null && expiry.isBefore(today) && medicine.getQuantity() > 0;
                })
                .sorted(Comparator.comparing(medicine -> parseExpiry(medicine.getExpiryDate())))
                .toList();
        List<Medicine> expiringSoon = allMedicines.stream()
                .filter(medicine -> {
                    LocalDate expiry = parseExpiry(medicine.getExpiryDate());
                    return expiry != null && !expiry.isBefore(today) && !expiry.isAfter(expiryLimit)
                            && medicine.getQuantity() > 0;
                })
                .sorted(Comparator.comparing(medicine -> parseExpiry(medicine.getExpiryDate())))
                .toList();
        List<Medicine> lowStock = allMedicines.stream()
                .filter(medicine -> medicine.getQuantity() <= lowStockThreshold)
                .sorted(Comparator.comparingInt(Medicine::getQuantity))
                .toList();

        return new AlertSummary(expired, expiringSoon, lowStock, expiryDays, lowStockThreshold);
    }

    @Transactional
    public Medicine addMedicine(Medicine request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw badRequest("Medicine name is required.");
        }
        if (!StringUtils.hasText(request.getCategory())) {
            throw badRequest("Category is required.");
        }
        if (request.getName().trim().length() > 120 || request.getCategory().trim().length() > 80) {
            throw badRequest("Name must be 120 characters or fewer and category 80 or fewer.");
        }
        if (request.getQuantity() < 0) {
            throw badRequest("Opening stock cannot be negative.");
        }
        if (Double.isNaN(request.getPrice()) || Double.isInfinite(request.getPrice()) || request.getPrice() < 0) {
            throw badRequest("Retail price must be zero or more.");
        }
        if (parseExpiry(request.getExpiryDate()) == null) {
            throw badRequest("Enter a valid expiry date.");
        }
        Medicine medicine = new Medicine(request.getName().trim(), request.getCategory().trim(),
                request.getQuantity(), request.getPrice(), request.getExpiryDate().trim());
        medicine.setCreatedAt(LocalDateTime.now());
        Medicine saved = medicineRepository.save(medicine);
        activityService.record("Inventory", "Added lot " + saved.getName() + " (" + saved.getCategory() + ") · "
                + saved.getQuantity() + " units");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SaleView> searchSales(String query) {
        String term = query == null ? "" : query.trim();
        if (term.isEmpty()) {
            return List.of();
        }
        return saleRepository
                .findTop5ByInvoiceNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCaseOrderBySoldAtDesc(term, term)
                .stream().map(this::toSaleView).toList();
    }

    @Transactional
    public void deleteMedicine(Long medicineId) {
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> notFound("Medicine lot not found."));
        if (purchaseRepository.existsByMedicineId(medicineId) || saleItemRepository.existsByMedicineId(medicineId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This lot has purchase or sale history and cannot be deleted.");
        }
        medicineRepository.delete(medicine);
        activityService.record("Inventory", "Deleted lot " + medicine.getName() + " (" + medicine.getQuantity() + " units)");
    }

    private PurchaseView toPurchaseView(Purchase purchase) {
        BigDecimal lineTotal = purchase.getUnitCost().multiply(BigDecimal.valueOf(purchase.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return new PurchaseView(purchase.getId(), purchase.getSupplier().getName(), purchase.getMedicine().getId(),
                purchase.getMedicine().getName(), purchase.getQuantity(), purchase.getUnitCost(), lineTotal,
                purchase.getPurchaseDate());
    }

    private SaleView toSaleView(Sale sale) {
        List<SaleLineView> lines = sale.getItems().stream()
                .map(item -> new SaleLineView(item.getMedicine().getId(), item.getMedicineName(), item.getQuantity(),
                        item.getUnitPrice(), item.getLineTotal()))
                .toList();
        return new SaleView(sale.getId(), sale.getInvoiceNumber(), sale.getCustomerName(), sale.getSoldAt(),
                sale.getTotal(), lines);
    }

    private LocalDate parseExpiry(String expiryDate) {
        if (!StringUtils.hasText(expiryDate)) {
            return null;
        }
        try {
            return LocalDate.parse(expiryDate.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}