package com.jianhui.project.ticketservice.mq.comsumer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.framework.starter.common.toolkit.BeanUtil;
import com.jianhui.project.framework.starter.convention.result.Result;
import com.jianhui.project.ticketservice.common.constant.TicketRocketMQConstant;
import com.jianhui.project.ticketservice.dto.domain.RouteDTO;
import com.jianhui.project.ticketservice.dto.req.CancelTicketOrderReqDTO;
import com.jianhui.project.ticketservice.mq.domin.MessageWrapper;
import com.jianhui.project.ticketservice.mq.event.DelayCloseOrderEvent;
import com.jianhui.project.ticketservice.remote.TicketOrderRemoteService;
import com.jianhui.project.ticketservice.remote.dto.TicketOrderDetailRespDTO;
import com.jianhui.project.ticketservice.remote.dto.TicketOrderPassengerDetailRespDTO;
import com.jianhui.project.ticketservice.service.SeatService;
import com.jianhui.project.ticketservice.service.TrainStationService;
import com.jianhui.project.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.jianhui.project.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jianhui.project.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;

/**
 * 延迟关闭订单消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TicketRocketMQConstant.ORDER_DELAY_CLOSE_TOPIC_KEY,
        selectorExpression = TicketRocketMQConstant.ORDER_DELAY_CLOSE_TAG_KEY,
        consumerGroup = TicketRocketMQConstant.TICKET_DELAY_CLOSE_CG_KEY
)
public class DelayCloseOrderConsumer implements RocketMQListener<MessageWrapper<DelayCloseOrderEvent>> {

    private final SeatService seatService;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final TrainStationService trainStationService;
    private final DistributedCache distributedCache;
    private final TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;

    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;

//    @Idempotent(
//            uniqueKeyPrefix = "index12306-ticket:delay_close_order:",
//            key = "#delayCloseOrderEventMessageWrapper.getKeys()+'_'+#delayCloseOrderEventMessageWrapper.hashCode()",
//            type = IdempotentTypeEnum.SPEL,
//            scene = IdempotentSceneEnum.MQ,
//            keyTimeout = 7200L
//    )
    @Override
    public void onMessage(MessageWrapper<DelayCloseOrderEvent> message) {
        log.info("[延迟关闭订单] 开始消费：{}", JSON.toJSONString(message));
        DelayCloseOrderEvent delayCloseOrderEvent = message.getMessage();
        String orderSn = delayCloseOrderEvent.getOrderSn();
        Result<Boolean> closedTickOrder;
        try {
//            调用远程服务关闭订单
            closedTickOrder = ticketOrderRemoteService.closeTickOrder(new CancelTicketOrderReqDTO(orderSn));
        } catch (Throwable ex) {
            log.error("[延迟关闭订单] 订单号：{} 远程调用订单服务失败", orderSn, ex);
            throw ex;
        }
        if (closedTickOrder.isSuccess() && !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
//            调用关闭订单时发现已经支付,退出处理
            if (!closedTickOrder.getData()) {
                log.info("[延迟关闭订单] 订单号：{} 用户已支付订单", orderSn);
                return;
            }
            String trainId = delayCloseOrderEvent.getTrainId();
            String departure = delayCloseOrderEvent.getDeparture();
            String arrival = delayCloseOrderEvent.getArrival();
            List<TrainPurchaseTicketRespDTO> trainPurchaseTicketResults = delayCloseOrderEvent.getTrainPurchaseTicketResults();
//            车票解锁
            try {
                seatService.unlock(trainId, departure, arrival, trainPurchaseTicketResults);
            } catch (Throwable ex) {
                log.error("[延迟关闭订单] 订单号：{} 回滚列车DB座位状态失败", orderSn, ex);
                throw ex;
            }
            try{
                StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
//                按座位类型分组
                Map<Integer, List<TrainPurchaseTicketRespDTO>> seatTypeMap = trainPurchaseTicketResults.stream()
                        .collect(Collectors.groupingBy(TrainPurchaseTicketRespDTO::getSeatType));
//                要扣减的站点,用于回滚
                List<RouteDTO> routeDTOList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
                routeDTOList.forEach(each -> {
                    String keySuffix = StrUtil.join("_", trainId, each.getStartStation(), each.getEndStation());
                    seatTypeMap.forEach((seatType, trainPurchaseTicketRespDTOList) -> {
//                        将余票缓存增加
                        stringRedisTemplate.opsForHash().increment(
                                TRAIN_STATION_REMAINING_TICKET + keySuffix,
                                String.valueOf(seatType),
                                trainPurchaseTicketRespDTOList.size());
                    });
                });
//                回滚令牌桶
                TicketOrderDetailRespDTO ticketOrderDetail = BeanUtil.convert(delayCloseOrderEvent, TicketOrderDetailRespDTO.class);
                ticketOrderDetail.setPassengerDetails(BeanUtil.convert(delayCloseOrderEvent.getTrainPurchaseTicketResults(), TicketOrderPassengerDetailRespDTO.class));
                ticketAvailabilityTokenBucket.rollbackInBucket(ticketOrderDetail);
            }catch(Throwable ex){
                log.error("[延迟关闭订单] 订单号：{} 回滚列车Cache余票失败", orderSn, ex);
                throw ex;
            }
        }
    }
}
