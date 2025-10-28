package com.TbInventory.web;

import com.TbInventory.model.FermBatch;
import com.TbInventory.model.FermTank;
import com.TbInventory.model.FermTransaction;
import com.TbInventory.model.UnitType;
import com.TbInventory.service.FermenterService;
import com.TbInventory.web.dto.TransactionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class FermentersController {

    private final FermenterService fermenterService;

    public FermentersController(FermenterService fermenterService) {
        this.fermenterService = fermenterService;
    }

    @GetMapping("/fermenters")
    public String fermenters(Model model) {
        model.addAttribute("title", "Fermenters");

        // Fetch all tanks from database
        model.addAttribute("tanks", fermenterService.getAllTanks());

        return "fermenters";
    }

    @GetMapping("/fermenters/{label}/details")
    public String tankDetails(@PathVariable String label, Model model) {
        // Fetch tank details by label
        FermTank tank = fermenterService.getTankByLabel(label);
        model.addAttribute("tank", tank);

        // If tank has an active batch, fetch batch details and transactions
        if (tank.getCurrentBatchId() != null) {
            FermBatch batch = fermenterService.getBatchById(tank.getCurrentBatchId());
            model.addAttribute("batch", batch);

            var transactions = fermenterService.getBatchTransactions(tank.getCurrentBatchId());
            model.addAttribute("transactions", transactions);
        }

        // Return just the modal body fragment
        return "fragments/tank-details :: tank-details-content";
    }

    // ==================== JSON API Endpoints ====================

    @GetMapping("/api/fermenters/{label}")
    @ResponseBody
    public FermTank getTankJson(@PathVariable String label) {
        return fermenterService.getTankByLabel(label);
    }

    @GetMapping("/api/volume-units")
    @ResponseBody
    public List<UnitType> getVolumeUnits() {
        return fermenterService.getVolumeUnits();
    }

    // ==================== Batch Endpoints ====================

    @PostMapping("/api/batches/start")
    @ResponseBody
    public ResponseEntity<?> startBatch(@RequestBody TransactionRequest request) {
        try {
            // Validate required fields for starting a batch
            if (request.getTankId() == null) {
                throw new RuntimeException("Tank ID is required");
            }
            if (request.getNewBatchLabel() == null || request.getNewBatchLabel().trim().isEmpty()) {
                throw new RuntimeException("Batch label is required");
            }
            if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Initial quantity must be greater than zero");
            }

            // Default to Cider Addition (ID 1) if not specified
            Integer transactionTypeId = request.getTransactionTypeId();
            if (transactionTypeId == null) {
                transactionTypeId = 1; // Cider Addition
            }

            // Call service to create batch + initial transaction atomically
            FermBatch batch = fermenterService.startBatch(
                request.getTankId(),
                request.getNewBatchLabel(),
                transactionTypeId,
                request.getQuantity(),
                request.getNotes()
            );

            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Batch started successfully");
            response.put("batchId", batch.getId());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            // Return error response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ==================== Transaction Endpoints ====================

    @PostMapping("/api/transactions")
    @ResponseBody
    public ResponseEntity<?> addTransaction(@RequestBody TransactionRequest request) {
        try {
            // TODO: Get user ID from authentication context (for now, use hardcoded value)
            Integer userId = 1;

            // Add transaction using service layer
            FermTransaction transaction = fermenterService.addTransaction(
                request.getBatchId(),
                request.getTransactionTypeId(),
                request.getQuantity(),
                LocalDateTime.now(), // Use current time for transaction date
                userId,
                request.getNotes()
            );

            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Transaction added successfully");
            response.put("transactionId", transaction.getId());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            // Return error response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
