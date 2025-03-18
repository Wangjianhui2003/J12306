package com.jianhui.project.userservice;

import org.junit.jupiter.api.Test;

import java.util.Optional;

public class UserServiceTests {
    @Test
    public void test1(){
        String hello = "aaa";
        System.out.println(Optional.ofNullable(hello).orElse("bbb"));
    }
}
