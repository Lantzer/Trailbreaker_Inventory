package com.TbInventory.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CoolerManagementController {

    @GetMapping("/cooler-management")
    public String coolerManagement(Model model) {
        model.addAttribute("title", "Cooler Management");
        return "cooler-management";
    }
}
