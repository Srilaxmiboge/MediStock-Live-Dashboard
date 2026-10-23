package com.medistock.controller;

import com.medistock.model.Medicine;
import com.medistock.repository.MedicineRepository;
import com.medistock.service.PharmacyWorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicines")
public class MedicineController {

    private final MedicineRepository medicineRepository;
    private final PharmacyWorkflowService workflowService;

    public MedicineController(MedicineRepository medicineRepository, PharmacyWorkflowService workflowService) {
        this.medicineRepository = medicineRepository;
        this.workflowService = workflowService;
    }

    @GetMapping
    public List<Medicine> getAllMedicines() {
        return medicineRepository.findAll();
    }

    @GetMapping("/search")
    public List<Medicine> searchMedicines(@RequestParam(name = "q", defaultValue = "") String q) {
        String term = q.trim();
        if (term.isEmpty()) {
            return medicineRepository.findAll();
        }
        return medicineRepository.findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(term, term);
    }

    @PostMapping
    public Medicine addMedicine(@RequestBody Medicine medicine) {
        return workflowService.addMedicine(medicine);
    }

    @DeleteMapping("/{id}")
    public void deleteMedicine(@PathVariable Long id) {
        workflowService.deleteMedicine(id);
    }
}