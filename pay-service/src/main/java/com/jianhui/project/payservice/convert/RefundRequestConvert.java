package com.jianhui.project.payservice.convert;

import com.jianhui.project.framework.starter.common.toolkit.BeanUtil;
import com.jianhui.project.payservice.common.enums.PayChannelEnum;
import com.jianhui.project.payservice.dto.RefundCommand;
import com.jianhui.project.payservice.dto.base.AliRefundRequest;
import com.jianhui.project.payservice.dto.base.RefundRequest;

import java.util.Objects;

/**
 * 退款请求入参转换器
 */
public class RefundRequestConvert {

    /**
     * {@link RefundCommand} to {@link RefundRequest}
     *
     * @param refundCommand 退款请求参数
     * @return {@link RefundRequest}
     */
    public static RefundRequest command2RefundRequest(RefundCommand refundCommand){
        RefundRequest refundRequest = null;
//        如果是支付宝渠道,则转换为AliRefundRequest
        if(Objects.equals(refundCommand.getChannel(), PayChannelEnum.ALI_PAY.getCode())){
            refundRequest = BeanUtil.convert(refundCommand, AliRefundRequest.class);
        }
        return refundRequest;
    }
}
