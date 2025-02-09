package com.jianhui.project;

import com.jianhui.test.teststarter.annotation.EnableJService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@EnableJService //自定义的测试starter
@SpringBootApplication
public class TestMain {

    public static void main(String[] args) {
        SpringApplication.run(TestMain.class);
    }
}