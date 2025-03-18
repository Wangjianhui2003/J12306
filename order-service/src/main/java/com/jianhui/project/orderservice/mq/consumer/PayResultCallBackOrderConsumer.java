package com.jianhui.project.orderservice.mq.consumer;

import com.jianhui.project.orderservice.common.constant.OrderRocketMQConstant;
import com.jianhui.project.orderservice.common.enums.OrderItemStatusEnum;
import com.jianhui.project.orderservice.common.enums.OrderStatusEnum;
import com.jianhui.project.orderservice.dto.domain.OrderStatusReversalDTO;
import com.jianhui.project.orderservice.mq.domin.MessageWrapper;
import com.jianhui.project.orderservice.mq.event.PayResultCallbackOrderEvent;
import com.jianhui.project.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付结果回调订单消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.PAY_GLOBAL_TOPIC_KEY,
        selectorExpression = OrderRocketMQConstant.PAY_RESULT_CALLBACK_TAG_KEY,
        consumerGroup = OrderRocketMQConstant.PAY_RESULT_CALLBACK_ORDER_CG_KEY
)
public class PayResultCallBackOrderConsumer implements RocketMQListener<MessageWrapper<PayResultCallbackOrderEvent>> {

    private final OrderService orderService;

//    @Idempotent(
//            uniqueKeyPrefix = "index12306-order:pay_result_callback:",
//            key = "#message.getKeys()+'_'+#message.hashCode()",
//            type = IdempotentTypeEnum.SPEL,
//            scene = IdempotentSceneEnum.MQ,
//            keyTimeout = 7200L
//    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void onMessage(MessageWrapper<PayResultCallbackOrderEvent> message) {
        PayResultCallbackOrderEvent payResultCallbackOrderEvent = message.getMessage();
        OrderStatusReversalDTO orderStatusReversalDTO = OrderStatusReversalDTO.builder()
                .orderSn(payResultCallbackOrderEvent.getOrderSn())
                .orderStatus(OrderStatusEnum.ALREADY_PAID.getStatus())
                .orderItemStatus(OrderItemStatusEnum.ALREADY_PAID.getStatus())
                .build();
//        改变订单状态
        orderService.statusReversal(orderStatusReversalDTO);
//        处理订单回调(设置支付时间和支付渠道)
        orderService.payCallbackOrder(payResultCallbackOrderEvent);
    }
}
