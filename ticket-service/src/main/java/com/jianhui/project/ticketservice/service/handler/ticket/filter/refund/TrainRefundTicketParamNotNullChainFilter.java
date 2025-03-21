package com.jianhui.project.ticketservice.service.handler.ticket.filter.refund;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jianhui.project.framework.starter.convention.exception.ClientException;
import com.jianhui.project.ticketservice.common.enums.RefundTypeEnum;
import com.jianhui.project.ticketservice.dto.req.RefundTicketReqDTO;
import org.springframework.stereotype.Component;

@Component
public class TrainRefundTicketParamNotNullChainFilter implements TrainRefundTicketChainFilter<RefundTicketReqDTO>{

    @Override
    public void handler(RefundTicketReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getOrderSn())) {
            throw new ClientException("订单号不能为空");
        }
        if (requestParam.getType() == null) {
            throw new ClientException("退款类型不能为空");
        }
        if (requestParam.getType().equals(RefundTypeEnum.PARTIAL_REFUND.getType())) {
            if (CollUtil.isEmpty(requestParam.getSubOrderRecordIdReqList())) {
                throw new ClientException("部分退款子订单记录集合不能为空");
            }
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
