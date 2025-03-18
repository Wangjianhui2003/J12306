package com.jianhui.project;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

public class Test1 {

    @Test
    public void test1(){
    }
}

@RequiredArgsConstructor
enum Pay{
    NATIVE(1);

    @Getter
    private final Integer code;

    public static String findNameByCode(Integer code) {
        return Arrays.stream(Pay.values())
                .filter(each -> Objects.equals(each.getCode(), code))
                .findFirst()
                .map(Pay::name)
                .orElse(null);
    }

}