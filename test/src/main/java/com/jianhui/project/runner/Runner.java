package com.jianhui.project.runner;

import com.jianhui.test.teststarter.service.JTestStarterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class Runner implements ApplicationRunner {

    @Autowired
    JTestStarterService jTestStarterService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        jTestStarterService.print();
    }
}
