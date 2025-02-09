package com.jianhui.project.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试
 */
@RestController
public class TestController {
    @GetMapping("/test")
    String testHandler(){
        return "Hello";
    }
}
