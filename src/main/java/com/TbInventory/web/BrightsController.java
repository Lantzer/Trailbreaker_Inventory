package com.TbInventory.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BrightsController {

    @GetMapping("/brights")
    public String brights(Model model) {
        model.addAttribute("title", "Brights");
        return "brights";
    }
}
