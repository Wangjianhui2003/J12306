package com.jianhui.project.payservice.handler;

import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractExecuteStrategy;
import com.jianhui.project.payservice.dto.base.RefundRequest;
import com.jianhui.project.payservice.dto.base.RefundResponse;
import com.jianhui.project.payservice.handler.base.AbstractRefundHandler;

public class AliRefundNativeHandler extends AbstractRefundHandler implements AbstractExecuteStrategy<RefundRequest,RefundResponse> {
    @Override
    public RefundResponse refund(RefundRequest refundRequest) {
        return null;
    }

    @Override
    public String mark() {
        return AbstractExecuteStrategy.super.mark();
    }

    @Override
    public RefundResponse executeResp(RefundRequest requestParam) {
        return AbstractExecuteStrategy.super.executeResp(requestParam);
    }
}
