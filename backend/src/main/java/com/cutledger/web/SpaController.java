package com.cutledger.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
    @GetMapping({"/","/campaigns/{id}","/checkout/{id}","/receipts/{id}"})
    String app(){return "forward:/index.html";}
}
