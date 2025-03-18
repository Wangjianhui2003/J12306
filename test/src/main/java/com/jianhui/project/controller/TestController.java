package com.jianhui.project.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试
 */
@RestController
public class TestController {

    ThreadLocal<String> threadLocal = new ThreadLocal<>();

    @GetMapping("/test")
    String testHandler(){
        threadLocal.set("test");
        threadLocal.get();
        return "Hello";
    }
}
