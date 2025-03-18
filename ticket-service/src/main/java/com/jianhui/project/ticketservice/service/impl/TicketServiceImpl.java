package com.jianhui.project.ticketservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.framework.starter.cache.StringRedisTemplateProxy;
import com.jianhui.project.framework.starter.cache.toolkit.CacheUtil;
import com.jianhui.project.framework.starter.common.toolkit.BeanUtil;
import com.jianhui.project.framework.starter.convention.exception.ServiceException;
import com.jianhui.project.framework.starter.convention.result.Result;
import com.jianhui.project.framework.starter.log.annotation.ILog;
import com.jianhui.project.framework.starter.user.core.UserContext;
import com.jianhui.project.frameworks.starter.designpattern.chain.AbstractChainContext;
import com.jianhui.project.ticketservice.common.enums.RefundTypeEnum;
import com.jianhui.project.ticketservice.common.enums.SourceEnum;
import com.jianhui.project.ticketservice.common.enums.TicketChainMarkEnum;
import com.jianhui.project.ticketservice.common.enums.TicketStatusEnum;
import com.jianhui.project.ticketservice.dao.entity.*;
import com.jianhui.project.ticketservice.dao.mapper.*;
import com.jianhui.project.ticketservice.dto.domain.*;
import com.jianhui.project.ticketservice.dto.req.*;
import com.jianhui.project.ticketservice.dto.resp.RefundTicketRespDTO;
import com.jianhui.project.ticketservice.dto.resp.TicketOrderDetailRespDTO;
import com.jianhui.project.ticketservice.dto.resp.TicketPageQueryRespDTO;
import com.jianhui.project.ticketservice.dto.resp.TicketPurchaseRespDTO;
import com.jianhui.project.ticketservice.remote.PayRemoteService;
import com.jianhui.project.ticketservice.remote.TicketOrderRemoteService;
import com.jianhui.project.ticketservice.remote.dto.*;
import com.jianhui.project.ticketservice.service.SeatService;
import com.jianhui.project.ticketservice.service.TicketService;
import com.jianhui.project.ticketservice.service.TrainStationService;
import com.jianhui.project.ticketservice.service.cache.SeatMarginCacheLoader;
import com.jianhui.project.ticketservice.service.handler.ticket.dto.TokenResultDTO;
import com.jianhui.project.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.jianhui.project.ticketservice.service.handler.ticket.select.TrainSeatTypeSelector;
import com.jianhui.project.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket;
import com.jianhui.project.ticketservice.toolkit.TimeStringComparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.jianhui.project.ticketservice.common.constant.J12306Constant.ADVANCE_TICKET_DAY;
import static com.jianhui.project.ticketservice.common.constant.RedisKeyConstant.*;
import static com.jianhui.project.ticketservice.toolkit.DateUtil.calculateHourDifference;
import static com.jianhui.project.ticketservice.toolkit.DateUtil.convertDateToLocalTime;

/**
 * 车票服务接口实现
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketDO> implements TicketService, CommandLineRunner {

    private final TrainMapper trainMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final DistributedCache distributedCache;
    private final StationMapper stationMapper;
    private final SeatService seatService;
    private final TrainStationService trainStationService;
    private final RedissonClient redissonClient;
    private final ConfigurableEnvironment environment;
    private final AbstractChainContext<TicketPageQueryReqDTO> ticketPageQueryAbstractChainContext;
    private final AbstractChainContext<PurchaseTicketReqDTO> purchaseTicketAbstractChainContext;
    private final AbstractChainContext<RefundTicketReqDTO> refundReqDTOAbstractChainContext;
    private final StringRedisTemplateProxy stringRedisTemplateProxy;
    private final SeatMarginCacheLoader seatMarginCacheLoader;
    private final TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;
    private final TrainSeatTypeSelector trainSeatTypeSelector;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final PayRemoteService payRemoteService;
    private final TicketService ticketService;

    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;
    @Value("${framework.cache.redis.prefix:}")
    private String cacheRedisPrefix;

    @Override
    public TicketPageQueryRespDTO pageListTicketQueryV1(TicketPageQueryReqDTO requestParam) {
        // 责任链模式 验证城市名称是否存在、不存在加载缓存以及出发日期不能小于当前日期等等
        ticketPageQueryAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_QUERY_FILTER.name(), requestParam);

//        如果站点详细信息stationDetails有null值,读数据库到缓存
        StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
//        code和regionName的映射
        List<Object> stationDetails = instance.opsForHash()
                .multiGet(REGION_TRAIN_STATION_MAPPING, Lists.newArrayList(requestParam.getFromStation(), requestParam.getToStation()));
//        统计null值
        long count = stationDetails.stream().filter(Objects::isNull).count();
//        双重校验锁
        if (count > 0) {
            RLock rLock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION_MAPPING);
            rLock.lock();
            try {
                stationDetails = instance.opsForHash()
                        .multiGet(REGION_TRAIN_STATION_MAPPING, Lists.newArrayList(requestParam.getFromStation(), requestParam.getToStation()));
                count = stationDetails.stream().filter(Objects::isNull).count();
                if (count > 0) {
//                    查站点数据库
                    List<StationDO> stationDOs = stationMapper.selectList(Wrappers.emptyWrapper());
                    Map<String, String> regionTrainStationMap = new HashMap<>();
                    stationDOs.forEach(each -> regionTrainStationMap.put(each.getCode(), each.getRegionName()));
//                    全部存缓存
                    instance.opsForHash().putAll(REGION_TRAIN_STATION_MAPPING, regionTrainStationMap);
                    stationDetails = new ArrayList<>();
//                    查出本次需要的数据
                    stationDetails.add(regionTrainStationMap.get(requestParam.getFromStation()));
                    stationDetails.add(regionTrainStationMap.get(requestParam.getToStation()));
                }
            } finally {
                rLock.unlock();
            }
        }
//        车次实体集合
        List<TicketListDTO> seatResults = new ArrayList<>();
//        起始站点->终点站点缓存的key
        String buildRegionTrainStationHashKey = String.format(REGION_TRAIN_STATION, stationDetails.get(0), stationDetails.get(1));
//        双重校验锁 如果缓存中没有数据则从数据库中查询
        Map<Object, Object> entries = instance.opsForHash().entries(buildRegionTrainStationHashKey);
        if (MapUtil.isEmpty(entries)) {
            RLock rLock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION);
            rLock.lock();
            try {
                entries = instance.opsForHash().entries(buildRegionTrainStationHashKey);
                if (MapUtil.isEmpty(entries)) {
//                    查询满足的从起始站点城市到终点站点城市的车次
                    LambdaQueryWrapper<TrainStationRelationDO> eq = Wrappers.lambdaQuery(TrainStationRelationDO.class)
                            .eq(TrainStationRelationDO::getStartRegion, stationDetails.get(0))
                            .eq(TrainStationRelationDO::getEndRegion, stationDetails.get(1));
                    List<TrainStationRelationDO> trainStationRelationList = trainStationRelationMapper.selectList(eq);
//                    遍历每个满足的始,达车站
                    for (TrainStationRelationDO each : trainStationRelationList) {
//                          获得列车数据trainDO,从缓存取数据,或者从数据库取数据,并放入缓存
                        TrainDO trainDO = distributedCache.safeGet(
                                TRAIN_INFO + each.getTrainId(),
                                TrainDO.class,
                                () -> trainMapper.selectById(each.getTrainId()),
                                ADVANCE_TICKET_DAY,
                                TimeUnit.DAYS);
//                        一个车次
                        TicketListDTO result = new TicketListDTO();
                        result.setTrainId(String.valueOf(trainDO.getId()));
                        result.setTrainNumber(trainDO.getTrainNumber());
                        result.setDepartureTime(convertDateToLocalTime(each.getDepartureTime(), "HH:mm"));
                        result.setArrivalTime(convertDateToLocalTime(each.getArrivalTime(), "HH:mm"));
                        result.setDuration(calculateHourDifference(each.getDepartureTime(), each.getArrivalTime()));
                        result.setDeparture(each.getDeparture());
                        result.setArrival(each.getArrival());
                        result.setDepartureFlag(each.getDepartureFlag());
                        result.setArrivalFlag(each.getArrivalFlag());
                        result.setTrainType(trainDO.getTrainType());
                        result.setTrainBrand(trainDO.getTrainBrand());
//                        设置动车标签
                        if (StrUtil.isNotBlank(trainDO.getTrainTag())) {
                            result.setTrainTags(StrUtil.split(trainDO.getTrainTag(), ","));
                        }
                        long betweenDay = DateUtil.betweenDay(each.getDepartureTime(), each.getArrivalTime(), false);
                        result.setDaysArrived((int) betweenDay);
                        result.setSaleStatus(new Date().after(trainDO.getSaleTime()) ? 0 : 1);
                        result.setSaleTime(convertDateToLocalTime(trainDO.getSaleTime(), "MM-dd HH:mm"));
//                        将result添加到seatResults中
                        seatResults.add(result);
//                        将result转为json字符串添加到map中,key值为列车id_出发站点_到达站点
                        entries.put(
                                CacheUtil.buildKey(String.valueOf(each.getTrainId()), each.getDeparture(), each.getArrival()),
                                JSON.toJSONString(result)
                        );
                    }
//                    添加所有满足条件的车次到缓存
                    instance.opsForHash().putAll(buildRegionTrainStationHashKey, entries);
                }
            } finally {
                rLock.unlock();
            }
        }
//          seatResults为null,说明上面的entries为empty步骤没有执行,已经从缓存中取数据到了entries,赋给seatResults即可
        seatResults = CollUtil.isEmpty(seatResults)
                ? entries.values().stream().map(each -> JSON.parseObject(each.toString(), TicketListDTO.class)).toList()
                : seatResults;
//          根据出发时间排序
        seatResults = seatResults.stream().sorted(new TimeStringComparator()).toList();
//        遍历车次进行补充
        for (TicketListDTO each : seatResults) {
//          获得TrainStationPriceDO的缓存
            String priceListStr = distributedCache.safeGet(
                    String.format(TRAIN_STATION_PRICE, each.getTrainId(), each.getDeparture(), each.getArrival()),
                    String.class,
                    () -> {
                        LambdaQueryWrapper<TrainStationPriceDO> eq = Wrappers.lambdaQuery(TrainStationPriceDO.class)
                                .eq(TrainStationPriceDO::getTrainId, each.getTrainId())
                                .eq(TrainStationPriceDO::getDeparture, each.getDeparture())
                                .eq(TrainStationPriceDO::getArrival, each.getArrival());
//                            selectList:不同座位价格不同
                        return JSON.toJSONString(trainStationPriceMapper.selectList(eq));
                    },
                    ADVANCE_TICKET_DAY,
                    TimeUnit.DAYS
            );
//            转为数组
            List<TrainStationPriceDO> trainStationPriceDOList = JSON.parseArray(priceListStr, TrainStationPriceDO.class);
//            SeatClassDTO list
            ArrayList<SeatClassDTO> seatClassDTOS = new ArrayList<>();
            trainStationPriceDOList.forEach(item -> {
                String seatType = String.valueOf(item.getSeatType());
                String keySuffix = StrUtil.join("_", each.getTrainId(), item.getDeparture(), item.getArrival());
//                查询余票
                Object remainObj = instance.opsForHash().get(TRAIN_STATION_REMAINING_TICKET + keySuffix, seatType);
                int remain = Optional.ofNullable(remainObj)
                        .map(Object::toString)
                        .map(Integer::parseInt)
                        .orElseGet(() -> {
//                            加载余票缓存
                            Map<String, String> seatMarginMap = seatMarginCacheLoader.load(String.valueOf(each.getTrainId()), seatType, item.getDeparture(), item.getArrival());
//                            如果没有余票,则返回0
                            return Optional.ofNullable(seatMarginMap.get(String.valueOf(item.getSeatType()))).map(Integer::parseInt).orElse(0);
                        });
//                添加座位类型
                seatClassDTOS.add(
                        new SeatClassDTO(
                                item.getSeatType(),
                                remain,
                                new BigDecimal(item.getPrice()).divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP),
                                false
                        )
                );
            });
            each.setSeatClassList(seatClassDTOS);
        }
        return TicketPageQueryRespDTO.builder()
                .trainList(seatResults)
//                统计数据
                .departureStationList(buildDepartureStationList(seatResults))
                .arrivalStationList(buildArrivalStationList(seatResults))
                .trainBrandList(buildTrainBrandList(seatResults))
                .seatClassTypeList(buildSeatClassList(seatResults))
                .build();
    }

    @Override
    public TicketPageQueryRespDTO pageListTicketQueryV2(TicketPageQueryReqDTO requestParam) {
        return null;
    }

//    @ILog
//    @Idempotent(
//            uniqueKeyPrefix = "j12306-ticket:lock_purchase-tickets:",
//            key = "T(com.jianhui.project.framework.starter.bases.ApplicationContextHolder).getBean('environment').getProperty('unique-name','')"
//            + "+'_'+"
//            + "T(com.jianhui.project.framework.starter.user.core.UserContext).getUsername()",
//            message = "正在执行下单流程，请稍后...",
//            scene = IdempotentSceneEnum.RESTAPI,
//            type = IdempotentTypeEnum.SPEL
//    )

    @Override
    public TicketPurchaseRespDTO purchaseTicketsV1(PurchaseTicketReqDTO requestParam) {
//        责任链
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(),requestParam);
//        上锁执行,加锁粒度很大,是整个列车
        String lockKey = environment.resolvePlaceholders(String.format(LOCK_PURCHASE_TICKETS, requestParam.getTrainId()));
        RLock rLock = redissonClient.getLock(lockKey);
        rLock.lock();
        try{
            return executePurchaseTickets(requestParam);
        }finally{
            rLock.unlock();
        }
    }
    private final Cache<String, ReentrantLock> localLockMap = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS).build();

    private final Cache<String,Object> tokenTicketsRefreshMap = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES).build();

//    @ILog
//    @Idempotent(
//            uniqueKeyPrefix = "index12306-ticket:lock_purchase-tickets:",
//            key = "T(org.opengoofy.index12306.framework.starter.bases.ApplicationContextHolder).getBean('environment').getProperty('unique-name', '')"
//                    + "+'_'+"
//                    + "T(org.opengoofy.index12306.frameworks.starter.user.core.UserContext).getUsername()",
//            message = "正在执行下单流程，请稍后...",
//            scene = IdempotentSceneEnum.RESTAPI,
//            type = IdempotentTypeEnum.SPEL
//    )

    @Override
    public TicketPurchaseRespDTO purchaseTicketsV2(PurchaseTicketReqDTO requestParam) {
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(), requestParam);
//        取token
        TokenResultDTO tokenResultDTO = ticketAvailabilityTokenBucket.takeTokenFromBucket(requestParam);
//        token获取为null
        if(tokenResultDTO.getTokenIsNull()){
//            双重判断,如果refreshMap中trainId对应的值为空,就触发令牌刷新
            if(tokenTicketsRefreshMap.getIfPresent(requestParam.getTrainId()) == null){
                synchronized (TicketService.class){
                    if(tokenTicketsRefreshMap.getIfPresent(requestParam.getTrainId()) == null){
                        tokenTicketsRefreshMap.put(requestParam.getTrainId(),new Object());
                        tokenIsNullRefreshToken(requestParam,tokenResultDTO);
                    }
                }
            }
            throw new ServiceException("列车站点无余票");
        }
//        本地锁和分布式锁
        ArrayList<ReentrantLock> localLockList = new ArrayList<>();
        ArrayList<RLock> distributedLockList = new ArrayList<>();
//        座位类型->乘车人信息
        Map<Integer, List<PurchaseTicketPassengerDetailDTO>> seatTypeMap = requestParam.getPassengers().stream()
                .collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType));
//        获得本地锁和分布式锁
        seatTypeMap.forEach((seatType,count) -> {
            String lockKey = environment.resolvePlaceholders(String.format(LOCK_PURCHASE_TICKETS_V2, requestParam.getTrainId(), seatType));
            ReentrantLock localLock = localLockMap.getIfPresent(lockKey);
            if(localLock == null){
                synchronized (TicketService.class){
                    if((localLock = localLockMap.getIfPresent(lockKey)) == null){
                        localLock = new ReentrantLock(true);
                        localLockMap.put(lockKey,localLock);
                    }
                }
            }
            localLockList.add(localLock);
            RLock rlock = redissonClient.getFairLock(lockKey);
            distributedLockList.add(rlock);
        });

//        依次加锁解锁
        try{
            localLockList.forEach(ReentrantLock::lock);
            distributedLockList.forEach(RLock::lock);
            return ticketService.executePurchaseTickets(requestParam);
        }finally{
            localLockList.forEach(localLock -> {
                try{
                    localLock.unlock();
                }catch(Throwable ignored){
                }
            });
            distributedLockList.forEach(distributedLock -> {
                try{
                    distributedLock.unlock();
                }catch(Throwable ignored){
                }
            });
        }
    }
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public TicketPurchaseRespDTO executePurchaseTickets(PurchaseTicketReqDTO requestParam) {
        String trainId = requestParam.getTrainId();
//        列车信息
        TrainDO trainDO = distributedCache.safeGet(
                TRAIN_INFO + trainId,
                TrainDO.class,
                () -> trainMapper.selectById(trainId),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS);
//        选座位,进行特殊的选座位算法
        List<TrainPurchaseTicketRespDTO> trainPurchaseTicketResults = trainSeatTypeSelector.select(trainDO.getTrainType(), requestParam);
//        构造车票
        List<TicketDO> ticketDOList = trainPurchaseTicketResults.stream()
                .map(each -> TicketDO.builder()
                        .username(UserContext.getUsername())
                        .trainId(Long.parseLong(requestParam.getTrainId()))
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .passengerId(each.getPassengerId())
                        .ticketStatus(TicketStatusEnum.UNPAID.getCode())
                        .build())
                .toList();

        saveBatch(ticketDOList);

        Result<String> ticketOrderResult;
        List<TicketOrderDetailRespDTO> ticketOrderDetailResults = new ArrayList<>();
//        构造订单详情实体类
        try {
            List<TicketOrderItemCreateRemoteReqDTO> orderItemCreateRemoteReqDTOList = new ArrayList<>();
            trainPurchaseTicketResults.forEach(each -> {
//                远程调用请求
                TicketOrderItemCreateRemoteReqDTO orderItemCreateRemoteReqDTO = TicketOrderItemCreateRemoteReqDTO.builder()
                        .amount(each.getAmount())
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .idCard(each.getIdCard())
                        .idType(each.getIdType())
                        .phone(each.getPhone())
                        .seatType(each.getSeatType())
                        .ticketType(each.getUserType())
                        .realName(each.getRealName())
                        .build();
//                构造车票订单详情
                TicketOrderDetailRespDTO ticketOrderDetailRespDTO = TicketOrderDetailRespDTO.builder()
                        .amount(each.getAmount())
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .idCard(each.getIdCard())
                        .idType(each.getIdType())
                        .seatType(each.getSeatType())
                        .ticketType(each.getUserType())
                        .realName(each.getRealName())
                        .build();
//                加入到数组
                orderItemCreateRemoteReqDTOList.add(orderItemCreateRemoteReqDTO);
                ticketOrderDetailResults.add(ticketOrderDetailRespDTO);
            });
//            查车站关联表
            LambdaQueryWrapper<TrainStationRelationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationRelationDO.class)
                    .eq(TrainStationRelationDO::getTrainId, trainId)
                    .eq(TrainStationRelationDO::getDeparture, requestParam.getDeparture())
                    .eq(TrainStationRelationDO::getArrival, requestParam.getArrival());
            TrainStationRelationDO trainStationRelationDO = trainStationRelationMapper.selectOne(queryWrapper);
//            创建订单请求实体类
            TicketOrderCreateRemoteReqDTO orderCreateRemoteReqDTO = TicketOrderCreateRemoteReqDTO.builder()
                    .departure(requestParam.getDeparture())
                    .arrival(requestParam.getArrival())
                    .orderTime(new Date())
                    .source(SourceEnum.INTERNET.getCode())
                    .trainNumber(trainDO.getTrainNumber())
                    .departureTime(trainStationRelationDO.getDepartureTime())
                    .arrivalTime(trainStationRelationDO.getArrivalTime())
                    .ridingDate(trainStationRelationDO.getDepartureTime())
                    .userId(UserContext.getUserId())
                    .username(UserContext.getUsername())
                    .trainId(Long.parseLong(requestParam.getTrainId()))
//                    详细信息
                    .ticketOrderItems(orderItemCreateRemoteReqDTOList)
                    .build();
//            调用服务,返回订单号
            ticketOrderResult = ticketOrderRemoteService.createTicketOrder(orderCreateRemoteReqDTO);
//            订单号判空
            if (!ticketOrderResult.isSuccess() || StrUtil.isBlank(ticketOrderResult.getData())) {
                log.error("订单服务调用失败，返回结果：{}", ticketOrderResult.getMessage());
                throw new ServiceException("订单服务调用失败");
            }
        } catch (Throwable ex) {
            log.error("远程调用订单服务创建错误，请求参数：{}", JSON.toJSONString(requestParam), ex);
            throw ex;
        }
//        返回订单号和车票订单详情
        return new TicketPurchaseRespDTO(ticketOrderResult.getData(), ticketOrderDetailResults);
    }

    @Override
    public PayInfoRespDTO getPayInfo(String orderSn) {
        return payRemoteService.getPayInfo(orderSn).getData();
    }

    @ILog
    @Override
    public void cancelTicketOrder(CancelTicketOrderReqDTO requestParam) {
//        调用
        Result<Void> cancelOrderResult = ticketOrderRemoteService.cancelTicketOrder(requestParam);
//        判断成功后
        if (cancelOrderResult.isSuccess() && !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
//            查询订单
            Result<com.jianhui.project.ticketservice.remote.dto.TicketOrderDetailRespDTO> ticketOrderDetailResult =
                    ticketOrderRemoteService.queryTicketOrderByOrderSn(requestParam.getOrderSn());
//            获取站点信息
            com.jianhui.project.ticketservice.remote.dto.TicketOrderDetailRespDTO ticketOrderDetail = ticketOrderDetailResult.getData();
            String trainId = String.valueOf(ticketOrderDetail.getTrainId());
            String departure = ticketOrderDetail.getDeparture();
            String arrival = ticketOrderDetail.getArrival();
//            解锁车票状态
            List<TicketOrderPassengerDetailRespDTO> trainPurchaseTicketResults = ticketOrderDetail.getPassengerDetails();
            try {
                seatService.unlock(trainId, departure, arrival, BeanUtil.convert(trainPurchaseTicketResults, TrainPurchaseTicketRespDTO.class));
            } catch (Throwable ex) {
                log.error("[取消订单] 订单号：{} 回滚列车DB座位状态失败", requestParam.getOrderSn(), ex);
                throw ex;
            }
//            令牌桶回滚
            ticketAvailabilityTokenBucket.rollbackInBucket(ticketOrderDetail);
//            余票缓存回滚
            try {
                StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
                Map<Integer, List<TicketOrderPassengerDetailRespDTO>> seatTypeMap = trainPurchaseTicketResults.stream()
                        .collect(Collectors.groupingBy(TicketOrderPassengerDetailRespDTO::getSeatType));
                List<RouteDTO> routeDTOList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
                routeDTOList.forEach(each -> {
                    String keySuffix = StrUtil.join("_", trainId, each.getStartStation(), each.getEndStation());
                    seatTypeMap.forEach((seatType, ticketOrderPassengerDetailRespDTOList) -> {
                        stringRedisTemplate.opsForHash()
                                .increment(TRAIN_STATION_REMAINING_TICKET + keySuffix, String.valueOf(seatType), ticketOrderPassengerDetailRespDTOList.size());
                    });
                });
            } catch (Throwable ex) {
                log.error("[取消关闭订单] 订单号：{} 回滚列车Cache余票失败", requestParam.getOrderSn(), ex);
                throw ex;
            }
        }
    }

    @Override
    public RefundTicketRespDTO commonTicketRefund(RefundTicketReqDTO requestParam) {
        // 责任链模式，验证 1：参数必填
        refundReqDTOAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_REFUND_TICKET_FILTER.name(), requestParam);
//        查询订单
        Result<com.jianhui.project.ticketservice.remote.dto.TicketOrderDetailRespDTO> orderDetailRespDTOResult = ticketOrderRemoteService
                .queryTicketOrderByOrderSn(requestParam.getOrderSn());
        if (!orderDetailRespDTOResult.isSuccess() && Objects.isNull(orderDetailRespDTOResult.getData())) {
            throw new ServiceException("车票订单不存在");
        }
//        查询订单信息中的乘车人订单信息
        com.jianhui.project.ticketservice.remote.dto.TicketOrderDetailRespDTO ticketOrderDetailRespDTO = orderDetailRespDTOResult.getData();
        List<TicketOrderPassengerDetailRespDTO> passengerDetails = ticketOrderDetailRespDTO.getPassengerDetails();
        if (CollectionUtil.isEmpty(passengerDetails)) {
            throw new ServiceException("车票子订单不存在");
        }
//        构造退款req
        RefundReqDTO refundReqDTO = new RefundReqDTO();
//        部分退款
        if (RefundTypeEnum.PARTIAL_REFUND.getType().equals(requestParam.getType())) {
//            查询车票子订单
            TicketOrderItemQueryReqDTO ticketOrderItemQueryReqDTO = new TicketOrderItemQueryReqDTO();
            ticketOrderItemQueryReqDTO.setOrderSn(requestParam.getOrderSn()); //订单号
            ticketOrderItemQueryReqDTO.setOrderItemRecordIds(requestParam.getSubOrderRecordIdReqList()); //子订单号
//            查询
            Result<List<TicketOrderPassengerDetailRespDTO>> queryTicketItemOrderById = ticketOrderRemoteService.queryTicketItemOrderById(ticketOrderItemQueryReqDTO);
//            过滤出部分退款
            List<TicketOrderPassengerDetailRespDTO> partialRefundPassengerDetails = passengerDetails.stream()
                    .filter(item -> queryTicketItemOrderById.getData().contains(item))
                    .collect(Collectors.toList());
//            构造退款DTO
            refundReqDTO.setRefundTypeEnum(RefundTypeEnum.PARTIAL_REFUND);
            refundReqDTO.setRefundDetailReqDTOList(partialRefundPassengerDetails);
//        全部退款
        } else if (RefundTypeEnum.FULL_REFUND.getType().equals(requestParam.getType())) {
            refundReqDTO.setRefundTypeEnum(RefundTypeEnum.FULL_REFUND);
            refundReqDTO.setRefundDetailReqDTOList(passengerDetails);
        }
//        累加退款金额
        if (CollectionUtil.isNotEmpty(passengerDetails)) {
            Integer partialRefundAmount = passengerDetails.stream()
                    .mapToInt(TicketOrderPassengerDetailRespDTO::getAmount)
                    .sum();
            refundReqDTO.setRefundAmount(partialRefundAmount);
        }
        refundReqDTO.setOrderSn(requestParam.getOrderSn());
//        调用退款
        Result<RefundRespDTO> refundRespDTOResult = payRemoteService.commonRefund(refundReqDTO);
        if (!refundRespDTOResult.isSuccess() && Objects.isNull(refundRespDTOResult.getData())) {
            throw new ServiceException("车票订单退款失败");
        }
        return null; // 暂时返回空实体
    }

    /**
     * 构造出发站点列表
     */
    private List<String> buildDepartureStationList(List<TicketListDTO> seatResults) {
        return seatResults.stream().map(TicketListDTO::getDeparture).distinct().toList();
    }

    /**
     * 构造到达站点列表
     */
    private List<String> buildArrivalStationList(List<TicketListDTO> seatResults) {
        return seatResults.stream().map(TicketListDTO::getArrival).distinct().toList();
    }

    /**
     *  够着列车品牌列表
     */
    private List<Integer> buildTrainBrandList(List<TicketListDTO> seatResults) {
        Set<Integer> trainBrandSet = new HashSet<>();
        for(TicketListDTO each : seatResults){
            StrUtil.split(each.getTrainBrand(), ",").forEach(item -> {
                trainBrandSet.add(Integer.parseInt(item));
            });
        }
        return trainBrandSet.stream().toList();
    }

    /**
     *  构造座位类型列表
     */
    private List<Integer> buildSeatClassList(List<TicketListDTO> seatResults) {
        Set<Integer> resultSeatClassList = new HashSet<>();
        for (TicketListDTO each : seatResults) {
            for (SeatClassDTO item : each.getSeatClassList()) {
                resultSeatClassList.add(item.getType());
            }
        }
        return resultSeatClassList.stream().toList();
    }

    /**
     * 用于刷新token缓存的定时线程
     */
    private final ScheduledExecutorService tokenIsNullRefreshExecutor = Executors.newScheduledThreadPool(1);

    /**
     * 如果令牌不足,查询数据库,验证是否存在数据库数据和缓存不一致,如果不一致,删除令牌缓存
     */
    private void tokenIsNullRefreshToken(PurchaseTicketReqDTO requestParam, TokenResultDTO tokenResultDTO) {
        RLock rLock = redissonClient.getLock(String.format(LOCK_TOKEN_BUCKET_ISNULL, requestParam.getTrainId()));
        if(!rLock.tryLock()){
            return;
        }
        tokenIsNullRefreshExecutor.schedule(() -> {
            try{
//                座位类型
                ArrayList<Integer> seatTypes = new ArrayList<>();
//                映射数量
                Map<Integer,Integer> tokenCountMap = new HashMap<>();
//
                tokenResultDTO.getTokenIsNullSeatTypeCounts().stream()
                        .map(each -> each.split("_"))
                        .forEach(split -> {
                            int seatType = Integer.parseInt(split[0]);
                            seatTypes.add(seatType);
                            tokenCountMap.put(seatType, Integer.parseInt(split[1]));
                        });
                List<SeatTypeCountDTO> seatTypeCountDTOList = seatService.listSeatTypeCount(Long.parseLong(requestParam.getTrainId()), requestParam.getDeparture(), requestParam.getArrival(), seatTypes);
                for(SeatTypeCountDTO each : seatTypeCountDTOList){
//                    token数量
                    Integer tokenCount = tokenCountMap.get(each.getSeatType());
//                    如果token数量小于余票数量,删除令牌
                    if (tokenCount < each.getSeatCount()){
                        ticketAvailabilityTokenBucket.delTokenInBucket(requestParam);
                        break;
                    }
                }
            }finally{
                rLock.unlock();
            }
        },10,TimeUnit.SECONDS);
    }

    @Override
    public void run(String... args) throws Exception {

    }
}
