package com.jianhui.project.payservice.handler;

import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractExecuteStrategy;
import com.jianhui.project.payservice.dto.base.PayCallbackRequest;
import com.jianhui.project.payservice.handler.base.AbstractPayCallbackHandler;

public class AliPayCallbackHandler extends AbstractPayCallbackHandler implements AbstractExecuteStrategy<PayCallbackRequest,Void> {

    @Override
    public void callback(PayCallbackRequest payCallbackRequest) {

    }

    @Override
    public String mark() {
        return AbstractExecuteStrategy.super.mark();
    }

    @Override
    public void execute(PayCallbackRequest requestParam) {
        AbstractExecuteStrategy.super.execute(requestParam);
    }
}
