package com.TbInventory.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OutgoingCiderController {

    @GetMapping("/outgoing-cider")
    public String outgoingCider(Model model) {
        model.addAttribute("title", "Outgoing Cider");
        return "outgoing-cider";
    }
}
