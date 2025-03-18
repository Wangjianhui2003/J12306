package com.jianhui.project.payservice.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.framework.starter.common.toolkit.BeanUtil;
import com.jianhui.project.framework.starter.convention.exception.ServiceException;
import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractStrategyChoose;
import com.jianhui.project.payservice.common.enums.TradeStatusEnum;
import com.jianhui.project.payservice.convert.RefundRequestConvert;
import com.jianhui.project.payservice.dao.entity.PayDO;
import com.jianhui.project.payservice.dao.mapper.PayMapper;
import com.jianhui.project.payservice.dto.RefundCommand;
import com.jianhui.project.payservice.dto.base.PayRequest;
import com.jianhui.project.payservice.dto.base.PayResponse;
import com.jianhui.project.payservice.dto.base.RefundRequest;
import com.jianhui.project.payservice.dto.base.RefundResponse;
import com.jianhui.project.payservice.dto.req.PayCallbackReqDTO;
import com.jianhui.project.payservice.dto.req.RefundReqDTO;
import com.jianhui.project.payservice.dto.resp.PayInfoRespDTO;
import com.jianhui.project.payservice.dto.resp.PayRespDTO;
import com.jianhui.project.payservice.dto.resp.RefundRespDTO;
import com.jianhui.project.payservice.mq.event.PayResultCallbackOrderEvent;
import com.jianhui.project.payservice.mq.produce.PayResultCallbackOrderSendProduce;
import com.jianhui.project.payservice.service.PayService;
import com.jianhui.project.payservice.service.payid.PayIdGeneratorManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.jianhui.project.payservice.common.constant.RedisKeyConstant.ORDER_PAY_RESULT_INFO;

/**
 * 支付接口层实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final DistributedCache distributedCache;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final PayMapper payMapper;
    private final PayResultCallbackOrderSendProduce payResultCallbackOrderSendProduce;

//    @Idempotent(
//            type = IdempotentTypeEnum.SPEL,
//            uniqueKeyPrefix = "index12306-pay:lock_create_pay:",
//            key = "#requestParam.getOutOrderSn()"
//    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PayRespDTO commonPay(PayRequest requestParam) {
//        尝试从分布式缓存中获取已存在的支付结果，避免重复支付
        PayRespDTO cacheResult = distributedCache.get(ORDER_PAY_RESULT_INFO + requestParam.getOrderSn(), PayRespDTO.class);
        if (cacheResult != null){
            return cacheResult;
        }
//        策略模式处理支付请求,得到响应结果
        PayResponse payResponse = abstractStrategyChoose.chooseAndExecuteResp(requestParam.buildMark(), requestParam);
//        构造支付实体类
        PayDO insertPay = BeanUtil.convert(requestParam, PayDO.class);
        String paySn = PayIdGeneratorManager.generateId(requestParam.getOrderSn());
        insertPay.setPaySn(paySn);
        insertPay.setStatus(TradeStatusEnum.WAIT_BUYER_PAY.tradeCode());
        insertPay.setTotalAmount(requestParam.getTotalAmount().multiply(new BigDecimal(100)).setScale(0,BigDecimal.ROUND_HALF_UP).intValue());
//        插入表
        int insert = payMapper.insert(insertPay);
        if(insert <= 0){
            log.error("支付单创建失败，支付聚合根：{}", JSON.toJSONString(requestParam));
            throw new ServiceException("支付单创建失败");
        }
//        将返回结果payResponse存入缓存
        distributedCache.put(ORDER_PAY_RESULT_INFO + requestParam.getOrderSn(),JSON.toJSONString(payResponse),10, TimeUnit.MINUTES);
        return BeanUtil.convert(payResponse, PayRespDTO.class);
    }

    @Override
    public void callbackPay(PayCallbackReqDTO requestParam) {
//        查询支付记录
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        PayDO payDO = payMapper.selectOne(queryWrapper);
        if(payDO == null){
            log.error("支付单不存在，orderRequestId：{}", requestParam.getOrderRequestId());
            throw new ServiceException("支付单不存在");
        }
//        更新支付记录
        payDO.setTradeNo(requestParam.getTradeNo());
        payDO.setStatus(requestParam.getStatus());
        payDO.setPayAmount(requestParam.getPayAmount());
        payDO.setGmtPayment(requestParam.getGmtPayment());
        LambdaUpdateWrapper<PayDO> updateWrapper = Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        int update = payMapper.update(payDO, updateWrapper);
        if(update <= 0){
            log.error("修改支付单支付结果失败，支付单信息：{}", JSON.toJSONString(payDO));
            throw new ServiceException("修改支付单支付结果失败");
        }
//        订单支付状态为成功,发送消息
        if (Objects.equals(requestParam.getStatus(), TradeStatusEnum.TRADE_SUCCESS.tradeCode())){
            payResultCallbackOrderSendProduce.sendMessage(BeanUtil.convert(payDO, PayResultCallbackOrderEvent.class));
        }
    }

    @Override
    public PayInfoRespDTO getPayInfoByOrderSn(String orderSn) {
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, orderSn);
        PayDO payDO = payMapper.selectOne(queryWrapper);
        return BeanUtil.convert(payDO, PayInfoRespDTO.class);
    }

    @Override
    public PayInfoRespDTO getPayInfoByPaySn(String paySn) {
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getPaySn, paySn);
        PayDO payDO = payMapper.selectOne(queryWrapper);
        return BeanUtil.convert(payDO, PayInfoRespDTO.class);
    }

    @Override
    public RefundRespDTO commonRefund(RefundReqDTO requestParam) {
//        查询支付单
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        PayDO payDO = payMapper.selectOne(queryWrapper);
        if (Objects.isNull(payDO)) {
            log.error("支付单不存在，orderSn：{}", requestParam.getOrderSn());
            throw new ServiceException("支付单不存在");
        }
//        PayDO -> RefundCommand -> RefundRequest
        RefundCommand refundCommand = BeanUtil.convert(payDO, RefundCommand.class);
        RefundRequest refundRequest = RefundRequestConvert.command2RefundRequest(refundCommand);
//        策略模式处理退款
//        策略模式：通过策略模式封装退款渠道和退款场景，用户退款时动态选择对应的退款组件
        RefundResponse refundResponse = abstractStrategyChoose
                .chooseAndExecuteResp(refundRequest.buildMark(), refundRequest);
//        更新状态
        payDO.setStatus(refundResponse.getStatus());
        LambdaUpdateWrapper<PayDO> updateWrapper = Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        int update = payMapper.update(payDO, updateWrapper);
        if(update <= 0){
            log.error("修改支付单退款结果失败，支付单信息：{}", JSON.toJSONString(payDO));
            throw new ServiceException("修改支付单退款结果失败");
        }
        return null;
    }
}
