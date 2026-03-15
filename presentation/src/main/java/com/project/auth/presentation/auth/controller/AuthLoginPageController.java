package com.project.auth.presentation.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthLoginPageController {

    @GetMapping({"/", "/login"})
    public String loginPage() {
        return "forward:/auth-login.html";
    }
}
