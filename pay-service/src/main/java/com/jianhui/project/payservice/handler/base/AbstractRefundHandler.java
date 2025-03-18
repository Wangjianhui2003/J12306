package com.jianhui.project.payservice.handler.base;

import com.jianhui.project.payservice.dto.base.RefundRequest;
import com.jianhui.project.payservice.dto.base.RefundResponse;

/**
 * 抽象退款组件
 */
public abstract class AbstractRefundHandler {

    /**
     * 支付退款接口
     *
     * @param  refundRequest 退款请求参数
     * @return 退款响应参数
     */
    public abstract RefundResponse refund(RefundRequest refundRequest);
}
