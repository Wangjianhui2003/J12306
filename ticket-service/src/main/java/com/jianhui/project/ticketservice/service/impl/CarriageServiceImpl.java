package com.jianhui.project.ticketservice.service.impl;

import com.jianhui.project.ticketservice.service.CarriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CarriageServiceImpl implements CarriageService {
    @Override
    public List<String> listCarriageNumber(String trainId, Integer carriageType) {
        return List.of();
    }
}
