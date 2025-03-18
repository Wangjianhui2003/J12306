package com.jianhui.project.payservice.handler;

import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractExecuteStrategy;
import com.jianhui.project.payservice.dto.base.PayRequest;
import com.jianhui.project.payservice.dto.base.PayResponse;
import com.jianhui.project.payservice.handler.base.AbstractPayHandler;

public class AliPayNativeHandler extends AbstractPayHandler implements AbstractExecuteStrategy<PayRequest, PayResponse> {

    @Override
    public PayResponse pay(PayRequest payRequest) {
        return null;
    }

    @Override
    public String mark() {
        return AbstractExecuteStrategy.super.mark();
    }

    @Override
    public PayResponse executeResp(PayRequest requestParam) {
        return AbstractExecuteStrategy.super.executeResp(requestParam);
    }
}
