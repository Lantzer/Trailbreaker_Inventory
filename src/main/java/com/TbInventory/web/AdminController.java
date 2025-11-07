package com.TbInventory.web;

import com.TbInventory.service.FermenterService;
import com.TbInventory.service.TankUpdateRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final FermenterService fermenterService;

    public AdminController(FermenterService fermenterService) {
        this.fermenterService = fermenterService;
    }

    // ==================== Admin Dashboard ====================

    @GetMapping
    public String adminDashboard(Model model) {
        model.addAttribute("title", "Admin Dashboard");
        return "admin/dashboard";
    }

    // ==================== Tank Management ====================

    @GetMapping("/tanks")
    public String tankManagement(Model model) {
        model.addAttribute("title", "Tank Management");
        model.addAttribute("tanks", fermenterService.getAllTanksIncludingDeleted());
        return "admin/tanks";
    }

    @GetMapping("/tanks/new")
    public String newTankForm(Model model) {
        model.addAttribute("title", "Create New Tank");
        model.addAttribute("units", fermenterService.getVolumeUnits());
        return "admin/tank-form";
    }

    @PostMapping("/tanks")
    public String createTank(
            @RequestParam("label") String label,
            @RequestParam("capacity") BigDecimal capacity,
            RedirectAttributes redirectAttributes) {
        try {
            fermenterService.createTank(label, capacity);
            redirectAttributes.addFlashAttribute("success", "Tank '" + label + "' created successfully");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/tanks/new";
        }
        return "redirect:/admin/tanks";
    }

    @GetMapping("/tanks/{label}/edit")
    public String editTankForm(@PathVariable String label, Model model) {
        model.addAttribute("title", "Edit Tank: " + label);
        model.addAttribute("tank", fermenterService.getTankByLabel(label));
        model.addAttribute("units", fermenterService.getVolumeUnits());
        return "admin/tank-edit-form";
    }

    @PostMapping("/tanks/{label}/update")
    public String updateTank(
            @PathVariable String label,
            @RequestParam(value = "newLabel", required = false) String newLabel,
            @RequestParam(value = "newCapacity", required = false) BigDecimal newCapacity,
            RedirectAttributes redirectAttributes) {
        try {
            // Build update request with only provided fields
            TankUpdateRequest.TankUpdateRequestBuilder builder = TankUpdateRequest.builder();

            if (newLabel != null && !newLabel.isBlank()) {
                builder.newLabel(newLabel);
            }

            // If new capacity is provided, ensure it's different from current capacity
            // and is a positive number
            if (newCapacity != null){
                // Validate new capacity
                if (newCapacity.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("Capacity must be a positive number");
                }
                // Check if new capacity is different from current capacity
                if (fermenterService.getTankByLabel(label).getCapacity().equals(newCapacity)) {
                    throw new RuntimeException("New capacity must be different from current capacity");
                }
            }

            // 
            if (newCapacity != null && newCapacity.compareTo(BigDecimal.ZERO) > 0) {
                builder.newCapacity(newCapacity);
            }
    
            TankUpdateRequest updateRequest = builder.build();
            fermenterService.updateTank(label, updateRequest);

            // Use updated label for redirect message
            String updatedLabel = newLabel != null && !newLabel.isBlank() ? newLabel : label;
            redirectAttributes.addFlashAttribute("success", "Tank '" + updatedLabel + "' updated successfully");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/tanks/" + label + "/edit";
        }
        return "redirect:/admin/tanks";
    }

    @PostMapping("/tanks/{label}/delete")
    public String deleteTank(@PathVariable String label, RedirectAttributes redirectAttributes) {
        try {
            fermenterService.softDeleteTank(label);
            redirectAttributes.addFlashAttribute("success", "Tank '" + label + "' deleted successfully");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tanks";
    }

    @PostMapping("/tanks/{label}/restore")
    public String restoreTank(@PathVariable String label, RedirectAttributes redirectAttributes) {
        try {
            fermenterService.restoreTank(label);
            redirectAttributes.addFlashAttribute("success", "Tank '" + label + "' restored successfully");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tanks";
    }

    // ==================== Fermenter Batches View ====================

    @GetMapping("/fermenter-batches")
    public String viewFermenterBatches(Model model) {
        model.addAttribute("title", "View Fermenter Batches");
        model.addAttribute("batches", fermenterService.getAllFermenterBatches());
        return "admin/fermenter-batches";
    }

    @GetMapping("/batches/{batchId}/details")
    public String batchDetails(@PathVariable Integer batchId, Model model) {
        // Get batch details (1 DB call)
        var batch = fermenterService.getBatchById(batchId);
        model.addAttribute("batch", batch);

        // Get transaction history (1 DB call)
        var transactions = fermenterService.getBatchTransactions(batchId);
        model.addAttribute("transactions", transactions);

        // Return just the modal body fragment
        return "admin/fragments/batch-details :: batch-details-content";
    }
}
