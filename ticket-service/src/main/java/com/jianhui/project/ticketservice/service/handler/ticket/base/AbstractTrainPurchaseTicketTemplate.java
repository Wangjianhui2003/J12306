package com.jianhui.project.ticketservice.service.handler.ticket.base;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jianhui.project.framework.starter.bases.ApplicationContextHolder;
import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractExecuteStrategy;
import com.jianhui.project.ticketservice.dto.domain.RouteDTO;
import com.jianhui.project.ticketservice.dto.domain.TrainSeatBaseDTO;
import com.jianhui.project.ticketservice.service.TrainStationService;
import com.jianhui.project.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import com.jianhui.project.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static com.jianhui.project.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;

/**
 * 抽象高铁购票模板基础服务(策略模式)
 */
public abstract class AbstractTrainPurchaseTicketTemplate implements IPurchaseTicket, AbstractExecuteStrategy<SelectSeatDTO, List<TrainPurchaseTicketRespDTO>>, CommandLineRunner {

    private DistributedCache distributedCache;
    private String ticketAvailabilityCacheUpdateType;
    private TrainStationService trainStationService;

    /**
     * 选择座位
     *
     * @param requestParam 购票请求入参
     * @return 乘车人座位
     */
    protected abstract List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam);

    /**
     * 执行具体策略的选座逻辑
     * 扣减余票缓存(如果不是binlog模式)
     */
    @Override
    public List<TrainPurchaseTicketRespDTO> executeResp(SelectSeatDTO requestParam) {
//        执行具体策略的选座逻辑
        List<TrainPurchaseTicketRespDTO> actualResult = selectSeats(requestParam);
//        扣减车厢余票缓存，扣减站点余票缓存
        if (CollUtil.isNotEmpty(actualResult) && !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            String trainId = requestParam.getRequestParam().getTrainId();
            String departure = requestParam.getRequestParam().getDeparture();
            String arrival = requestParam.getRequestParam().getArrival();
            StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
//            列出扣减的站点
            List<RouteDTO> routeDTOList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
            routeDTOList.forEach(each -> {
//                扣减缓存
                String keySuffix = StrUtil.join("_", trainId, each.getStartStation(), each.getEndStation());
                stringRedisTemplate.opsForHash().increment(TRAIN_STATION_REMAINING_TICKET + keySuffix, String.valueOf(requestParam.getSeatType()), -actualResult.size());
            });
        }
        return actualResult;
    }

    /**
     * 构建基础的列车座位信息
     */
    protected TrainSeatBaseDTO buildTrainSeatBaseDTO(SelectSeatDTO requestParam) {
        return TrainSeatBaseDTO.builder()
                .trainId(requestParam.getRequestParam().getTrainId())
                .departure(requestParam.getRequestParam().getDeparture())
                .arrival(requestParam.getRequestParam().getArrival())
                .chooseSeatList(requestParam.getRequestParam().getChooseSeats())
                .passengerSeatDetails(requestParam.getPassengerSeatDetails())
                .build();
    }

    @Override
    public void run(String... args) throws Exception {
        distributedCache = ApplicationContextHolder.getBean(DistributedCache.class);
        trainStationService = ApplicationContextHolder.getBean(TrainStationService.class);
        ConfigurableEnvironment configurableEnvironment = ApplicationContextHolder.getBean(ConfigurableEnvironment.class);
        ticketAvailabilityCacheUpdateType = configurableEnvironment.getProperty("ticket.availability.cache-update.type", "");
    }
}

