package com.jianhui.project.ticketservice.mq.comsumer;

import cn.hutool.core.collection.CollUtil;
import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractStrategyChoose;
import com.jianhui.project.ticketservice.common.constant.TicketRocketMQConstant;
import com.jianhui.project.ticketservice.common.enums.CanalExecuteStrategyMarkEnum;
import com.jianhui.project.ticketservice.mq.event.CanalBinlogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 列车车票余量缓存更新消费端
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TicketRocketMQConstant.CANAL_COMMON_SYNC_TOPIC_KEY,
        consumerGroup = TicketRocketMQConstant.CANAL_COMMON_SYNC_CG_KEY
)
public class CanalCommonSyncBinlogConsumer implements RocketMQListener<CanalBinlogEvent> {

    private final AbstractStrategyChoose abstractStrategyChoose;

    /**
     * 余票缓存更新类型
     */
    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;

//    @Idempotent(
//            uniqueKeyPrefix = "index12306-ticket:binlog_sync:",
//            key = "#message.getId()+'_'+#message.hashCode()",
//            type = IdempotentTypeEnum.SPEL,
//            scene = IdempotentSceneEnum.MQ,
//            keyTimeout = 7200L
//    )
    @Override
    public void onMessage(CanalBinlogEvent message) {
//        TODO:余票 Binlog 更新延迟问题如何解决？
//        跳过:旧数据为空,非更新,非binlog模式
        if (message.getIsDdl()
                || CollUtil.isEmpty(message.getOld())
                || !Objects.equals(message.getType(),"UPDATE")
                || !Objects.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            return;
        }
//        策略模式
        abstractStrategyChoose.chooseAndExecute(
                message.getTable(),
                message,
                CanalExecuteStrategyMarkEnum.isPatternMatch(message.getTable())
        );
    }
}
