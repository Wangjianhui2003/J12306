package com.jianhui.project.ticketservice.canal;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractExecuteStrategy;
import com.jianhui.project.ticketservice.common.enums.CanalExecuteStrategyMarkEnum;
import com.jianhui.project.ticketservice.common.enums.SeatStatusEnum;
import com.jianhui.project.ticketservice.mq.event.CanalBinlogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jianhui.project.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;

/**
 * 列车余票缓存更新组件
 * 处理rocketmq消费的 binlog 数据(canal传来)
 */
@Component
@RequiredArgsConstructor
public class TicketAvailabilityCacheUpdateHandler implements AbstractExecuteStrategy<CanalBinlogEvent, Void> {

    private final DistributedCache distributedCache;

    @Override
    public String mark() {
//        mark为表名t_seat
        return CanalExecuteStrategyMarkEnum.T_SEAT.getActualTable();
    }

    @Override
    public void execute(CanalBinlogEvent message) {
//        过滤数据,只监听t_seat表的seat_status变更:0（可售状态）转变为 1（锁定状态），或者从 1 变为 0
        List<Map<String, Object>> messageDataList = new ArrayList<>();
        List<Map<String, Object>> actualOldDataList = new ArrayList<>();
        for (int i = 0; i < message.getOld().size(); i++) {
            Map<String, Object> oldDataMap = message.getOld().get(i);
//            seat_status数据存在
            if (oldDataMap.get("seat_status") != null && StrUtil.isNotBlank(oldDataMap.get("seat_status").toString())) {
//                当前数据
                Map<String, Object> currentDataMap = message.getData().get(i);
//                筛选出当前数据为可售状态或者锁定状态
                if(StrUtil.equalsAny(currentDataMap.get("seat_status").toString(),
                        String.valueOf(SeatStatusEnum.AVAILABLE.getCode()),
                        String.valueOf(SeatStatusEnum.LOCKED.getCode()))){
                    actualOldDataList.add(oldDataMap);
                    messageDataList.add(currentDataMap);
                }
            }
        }
        if (CollUtil.isEmpty(messageDataList) || CollUtil.isEmpty(actualOldDataList)) {
            return;
        }
//        余票缓存更新
        Map<String, Map<Integer, Integer>> cacheChangeKeyMap = new HashMap<>();
//        遍历当前数据
        for(int i = 0;i < messageDataList.size();i++){
            Map<String, Object> each = messageDataList.get(i);
            Map<String, Object> actualOldData = actualOldDataList.get(i);
            String seatStatus = actualOldData.get("seat_status").toString();
            int increment = Objects.equals(seatStatus, "0") ? -1 : 1;
            String trainId = each.get("train_id").toString();
//            余票缓存
            String hashCacheKey = TRAIN_STATION_REMAINING_TICKET + trainId + "_" + each.get("start_station") + "_" + each.get("end_station");
//            特定列车:座位类型->数量
            Map<Integer, Integer> seatTypeMap = cacheChangeKeyMap.get(hashCacheKey);
            if (CollUtil.isEmpty(seatTypeMap)) {
                seatTypeMap = new HashMap<>();
            }
            Integer seatType = Integer.parseInt(each.get("seat_type").toString());
            Integer num = seatTypeMap.get(seatType);
//            对前面修改过的数据进行+increment,没有修改过就置increment
            seatTypeMap.put(seatType, num == null ? increment : num + increment);
            cacheChangeKeyMap.put(hashCacheKey, seatTypeMap);
        }
//        修改缓存
        StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
        cacheChangeKeyMap.forEach((cacheKey, cacheVal) -> cacheVal.forEach((seatType, num) -> instance.opsForHash().increment(cacheKey, String.valueOf(seatType), num)));
    }
}
