package com.TbInventory.web;

import com.TbInventory.model.FermBatch;
import com.TbInventory.model.FermTank;
import com.TbInventory.service.FermenterService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
}
