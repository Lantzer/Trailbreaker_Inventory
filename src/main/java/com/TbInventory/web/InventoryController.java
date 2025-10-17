package com.TbInventory.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InventoryController {

    @GetMapping("/inventory")
    public String inventory(Model model) {
        model.addAttribute("title", "Inventory");
        return "inventory";
    }
}
