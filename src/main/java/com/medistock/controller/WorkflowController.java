package com.medistock.controller;

import com.medistock.dto.WorkflowDtos.*;
import com.medistock.model.Supplier;
import com.medistock.service.PharmacyWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final PharmacyWorkflowService workflowService;

    public WorkflowController(PharmacyWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/suppliers")
    public List<Supplier> getSuppliers() {
        return workflowService.getSuppliers();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public Supplier addSupplier(@RequestBody SupplierRequest request) {
        return workflowService.addSupplier(request);
    }

    @PutMapping("/suppliers/{id}")
    public Supplier updateSupplier(@PathVariable Long id, @RequestBody SupplierRequest request) {
        return workflowService.updateSupplier(id, request);
    }

    @DeleteMapping("/suppliers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSupplier(@PathVariable Long id) {
        workflowService.deleteSupplier(id);
    }

    @GetMapping("/purchases")
    public List<PurchaseView> getPurchases() {
        return workflowService.getPurchases();
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseView recordPurchase(@RequestBody PurchaseRequest request) {
        return workflowService.recordPurchase(request);
    }

    @GetMapping("/sales")
    public List<SaleView> getSales() {
        return workflowService.getSales();
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleView recordSale(@RequestBody SaleRequest request) {
        return workflowService.recordSale(request);
    }

    @GetMapping("/sales/{id}")
    public SaleView getSale(@PathVariable Long id) {
        return workflowService.getSale(id);
    }

    @GetMapping("/sales/search")
    public List<SaleView> searchSales(@RequestParam(name = "q", defaultValue = "") String q) {
        return workflowService.searchSales(q);
    }

    @GetMapping("/alerts")
    public AlertSummary getAlerts() {
        return workflowService.getAlerts();
    }
}