package com.paymentsystem.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
public class HomeController {
    @GetMapping("/")
    public String home() {
        return "<h1>Hello World!</h1>";
    }
}