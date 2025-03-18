package com.jianhui.project;

import com.jianhui.test.teststarter.annotation.EnableJService;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.ConcurrentHashMap;


@EnableJService //自定义的测试starter
@SpringBootApplication
public class TestMain {

    public static void main(String[] args) {
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
    }
}