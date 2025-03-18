package com.jianhui.project.payservice.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jianhui.project.framework.starter.common.toolkit.BeanUtil;
import com.jianhui.project.framework.starter.convention.exception.ServiceException;
import com.jianhui.project.framework.starter.convention.result.Result;
import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractStrategyChoose;
import com.jianhui.project.payservice.common.enums.TradeStatusEnum;
import com.jianhui.project.payservice.convert.RefundRequestConvert;
import com.jianhui.project.payservice.dao.entity.PayDO;
import com.jianhui.project.payservice.dao.entity.RefundDO;
import com.jianhui.project.payservice.dao.mapper.PayMapper;
import com.jianhui.project.payservice.dao.mapper.RefundMapper;
import com.jianhui.project.payservice.dto.RefundCommand;
import com.jianhui.project.payservice.dto.RefundCreateDTO;
import com.jianhui.project.payservice.dto.base.RefundRequest;
import com.jianhui.project.payservice.dto.base.RefundResponse;
import com.jianhui.project.payservice.dto.req.RefundReqDTO;
import com.jianhui.project.payservice.dto.resp.RefundRespDTO;
import com.jianhui.project.payservice.mq.event.RefundResultCallbackOrderEvent;
import com.jianhui.project.payservice.mq.produce.RefundResultCallbackOrderSendProduce;
import com.jianhui.project.payservice.remote.TicketOrderRemoteService;
import com.jianhui.project.payservice.remote.dto.TicketOrderDetailRespDTO;
import com.jianhui.project.payservice.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

/**
 * 退款接口层实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final PayMapper payMapper;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final RefundMapper refundMapper;
    private final RefundResultCallbackOrderSendProduce refundResultCallbackOrderSendProduce;
    private final TicketOrderRemoteService ticketOrderRemoteService;

    @Override
    public RefundRespDTO commonRefund(RefundReqDTO requestParam) {
        RefundRespDTO refundRespDTO = null;
//        查询支付单
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        PayDO payDO = payMapper.selectOne(queryWrapper);
        if (payDO == null) {
            log.error("支付单不存在，orderSn：{}", requestParam.getOrderSn());
            throw new ServiceException("支付单不存在");
        }
        payDO.setPayAmount(payDO.getPayAmount() - (requestParam.getRefundAmount()));
//        payDO -> refundCreateDTO 创建退款记录入库
        RefundCreateDTO refundCreateDTO = BeanUtil.convert(payDO, RefundCreateDTO.class);
        refundCreateDTO.setPaySn(payDO.getPaySn());
        createRefund(refundCreateDTO);
//        策略模式处理退款
        RefundCommand refundCommand = BeanUtil.convert(payDO, RefundCommand.class);
        refundCommand.setPayAmount(new BigDecimal(requestParam.getRefundAmount()));
        RefundRequest refundRequest = RefundRequestConvert.command2RefundRequest(refundCommand);
        RefundResponse result = abstractStrategyChoose.chooseAndExecuteResp(refundRequest.buildMark(), refundRequest);
        payDO.setStatus(result.getStatus());
//        更新支付单
        LambdaUpdateWrapper<PayDO> updateWrapper = Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        int updateResult = payMapper.update(payDO, updateWrapper);
        if (updateResult <= 0) {
            log.error("修改支付单退款结果失败，支付单信息：{}", JSON.toJSONString(payDO));
            throw new ServiceException("修改支付单退款结果失败");
        }
//        更新退款记录状态和交易凭证号
        LambdaQueryWrapper<RefundDO> refundUpdateWrapper = Wrappers.lambdaQuery(RefundDO.class)
                .eq(RefundDO::getOrderSn, requestParam.getOrderSn());
        RefundDO refundDO = new RefundDO();
        refundDO.setTradeNo(result.getTradeNo());
        refundDO.setStatus(result.getStatus());
        int refundUpdateResult = refundMapper.update(refundDO, refundUpdateWrapper);
        if (refundUpdateResult <= 0) {
            log.error("修改退款单退款结果失败，退款单信息：{}", JSON.toJSONString(refundDO));
            throw new ServiceException("修改退款单退款结果失败");
        }
//        退款成功，回调订单服务告知退款结果，修改订单流转状态
        if (Objects.equals(result.getStatus(), TradeStatusEnum.TRADE_CLOSED.tradeCode())){
            RefundResultCallbackOrderEvent refundResultCallbackOrderEvent = RefundResultCallbackOrderEvent.builder()
                    .orderSn(requestParam.getOrderSn())
                    .refundTypeEnum(requestParam.getRefundTypeEnum())
                    .partialRefundTicketDetailList(requestParam.getRefundDetailReqDTOList())
                    .build();
            refundResultCallbackOrderSendProduce.sendMessage(refundResultCallbackOrderEvent);
        }
//        返回空实体
        return refundRespDTO;
    }

    private void createRefund(RefundCreateDTO requestParam) {
//        查询订单详情
        Result<TicketOrderDetailRespDTO> queryTicketResult = ticketOrderRemoteService.queryTicketOrderByOrderSn(requestParam.getOrderSn());
        if(!queryTicketResult.isSuccess() && queryTicketResult.getData() == null) {
            throw new ServiceException("车票订单不存在");
        }
//        订单详情
        TicketOrderDetailRespDTO orderDetailRespDTO = queryTicketResult.getData();
//        逐条插入退款记录
        requestParam.getRefundDetailReqDTOList().forEach(each -> {
            RefundDO refundDO = new RefundDO();
            refundDO.setPaySn(requestParam.getPaySn());
            refundDO.setOrderSn(requestParam.getOrderSn());
            refundDO.setTrainId(orderDetailRespDTO.getTrainId());
            refundDO.setTrainNumber(orderDetailRespDTO.getTrainNumber());
            refundDO.setDeparture(orderDetailRespDTO.getDeparture());
            refundDO.setArrival(orderDetailRespDTO.getArrival());
            refundDO.setDepartureTime(orderDetailRespDTO.getDepartureTime());
            refundDO.setArrivalTime(orderDetailRespDTO.getArrivalTime());
            refundDO.setRidingDate(orderDetailRespDTO.getRidingDate());
            refundDO.setSeatType(each.getSeatType());
            refundDO.setIdType(each.getIdType());
            refundDO.setIdCard(each.getIdCard());
            refundDO.setRealName(each.getRealName());
            refundDO.setRefundTime(new Date());
            refundDO.setAmount(each.getAmount());
            refundDO.setUserId(each.getUserId());
            refundDO.setUsername(each.getUsername());
            refundMapper.insert(refundDO);
        });
    }
}
