package com.jianhui.project.ticketservice.mq.comsumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jianhui.project.framework.starter.convention.exception.ServiceException;
import com.jianhui.project.framework.starter.convention.result.Result;
import com.jianhui.project.ticketservice.common.constant.TicketRocketMQConstant;
import com.jianhui.project.ticketservice.common.enums.SeatStatusEnum;
import com.jianhui.project.ticketservice.dao.entity.SeatDO;
import com.jianhui.project.ticketservice.dao.mapper.SeatMapper;
import com.jianhui.project.ticketservice.mq.domin.MessageWrapper;
import com.jianhui.project.ticketservice.mq.event.PayResultCallbackTicketEvent;
import com.jianhui.project.ticketservice.remote.TicketOrderRemoteService;
import com.jianhui.project.ticketservice.remote.dto.TicketOrderDetailRespDTO;
import com.jianhui.project.ticketservice.remote.dto.TicketOrderPassengerDetailRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 支付结果回调购票消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TicketRocketMQConstant.PAY_GLOBAL_TOPIC_KEY,
        selectorExpression = TicketRocketMQConstant.PAY_RESULT_CALLBACK_TAG_KEY,
        consumerGroup = TicketRocketMQConstant.PAY_RESULT_CALLBACK_TICKET_CG_KEY
)
public class PayResultCallBackTicketConsumer implements RocketMQListener<MessageWrapper<PayResultCallbackTicketEvent>> {

    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final SeatMapper seatMapper;

//    @Idempotent(
//            uniqueKeyPrefix = "index12306-ticket:pay_result_callback:",
//            key = "#message.getKeys()+'_'+#message.hashCode()",
//            type = IdempotentTypeEnum.SPEL,
//            scene = IdempotentSceneEnum.MQ,
//            keyTimeout = 7200L
//    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void onMessage(MessageWrapper<PayResultCallbackTicketEvent> message) {
        Result<TicketOrderDetailRespDTO> ticketOrderDetailResult;
        try {
//            查订单
            ticketOrderDetailResult = ticketOrderRemoteService.queryTicketOrderByOrderSn(message.getMessage().getOrderSn());
            if (!ticketOrderDetailResult.isSuccess() && Objects.isNull(ticketOrderDetailResult.getData())) {
                throw new ServiceException("支付结果回调查询订单失败");
            }
        } catch (Throwable ex) {
            log.error("支付结果回调查询订单失败", ex);
            throw ex;
        }
//        支付成功,将t_seat表中对应的座位状态改为已售出
        TicketOrderDetailRespDTO ticketOrderDetail = ticketOrderDetailResult.getData();
        for (TicketOrderPassengerDetailRespDTO each : ticketOrderDetail.getPassengerDetails()) {
            LambdaUpdateWrapper<SeatDO> updateWrapper = Wrappers.lambdaUpdate(SeatDO.class)
                    .eq(SeatDO::getTrainId, ticketOrderDetail.getTrainId())
                    .eq(SeatDO::getCarriageNumber, each.getCarriageNumber())
                    .eq(SeatDO::getSeatNumber, each.getSeatNumber())
                    .eq(SeatDO::getSeatType, each.getSeatType())
                    .eq(SeatDO::getStartStation, ticketOrderDetail.getDeparture())
                    .eq(SeatDO::getEndStation, ticketOrderDetail.getArrival());
            SeatDO updateSeatDO = new SeatDO();
            updateSeatDO.setSeatStatus(SeatStatusEnum.SOLD.getCode());
            seatMapper.update(updateSeatDO, updateWrapper);
        }
    }
}
