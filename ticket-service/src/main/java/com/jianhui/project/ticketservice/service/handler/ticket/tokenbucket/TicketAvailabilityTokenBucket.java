package com.jianhui.project.ticketservice.service.handler.ticket.tokenbucket;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.jianhui.project.framework.starter.bases.Singleton;
import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.framework.starter.common.toolkit.Assert;
import com.jianhui.project.framework.starter.convention.exception.ServiceException;
import com.jianhui.project.ticketservice.common.enums.VehicleTypeEnum;
import com.jianhui.project.ticketservice.dao.entity.TrainDO;
import com.jianhui.project.ticketservice.dao.mapper.TrainMapper;
import com.jianhui.project.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import com.jianhui.project.ticketservice.dto.domain.RouteDTO;
import com.jianhui.project.ticketservice.dto.domain.SeatTypeCountDTO;
import com.jianhui.project.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.jianhui.project.ticketservice.remote.dto.TicketOrderDetailRespDTO;
import com.jianhui.project.ticketservice.remote.dto.TicketOrderPassengerDetailRespDTO;
import com.jianhui.project.ticketservice.service.SeatService;
import com.jianhui.project.ticketservice.service.TrainStationService;
import com.jianhui.project.ticketservice.service.handler.ticket.dto.TokenResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.jianhui.project.ticketservice.common.constant.J12306Constant.ADVANCE_TICKET_DAY;
import static com.jianhui.project.ticketservice.common.constant.RedisKeyConstant.*;

/**
 * 列车车票余量令牌桶操作，应对海量并发场景下满足并行、限流以及防超卖等场景
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketAvailabilityTokenBucket {

    private final TrainStationService trainStationService;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;
    private final SeatService seatService;
    private final TrainMapper trainMapper;

    private static final String LUA_TICKET_AVAILABILITY_TOKEN_BUCKET_PATH = "lua/ticket_availability_token_bucket.lua";
    private static final String LUA_TICKET_AVAILABILITY_ROLLBACK_TOKEN_BUCKET_PATH = "lua/ticket_availability_rollback_token_bucket.lua";

    /**
     * 获取车站间令牌桶中的令牌访问
     * 如果返回 {@link Boolean#TRUE} 代表可以参与接下来的购票下单流程
     * 如果返回 {@link Boolean#FALSE} 代表当前访问出发站点和到达站点令牌已被拿完，无法参与购票下单等逻辑
     *
     * @param requestParam 购票请求参数入参
     * @return 是否获取列车车票余量令牌桶中的令牌返回结果
     */
    public TokenResultDTO takeTokenFromBucket(PurchaseTicketReqDTO requestParam) {
//        缓存取数据
        TrainDO trainDO = distributedCache.safeGet(
                TRAIN_INFO + requestParam.getTrainId(),
                TrainDO.class,
                () -> trainMapper.selectById(requestParam.getTrainId()),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS);

//        查询列车站点间的路线
        List<RouteDTO> routeDTOList = trainStationService
                .listTrainStationRoute(requestParam.getTrainId(), trainDO.getStartStation(), trainDO.getEndStation());

//        获取token
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        String tokenBucketHashKey = TICKET_AVAILABILITY_TOKEN_BUCKET + requestParam.getTrainId();
//        如果缓存中没有train的数据
        if (!distributedCache.hasKey(tokenBucketHashKey)) {
            RLock rLock = redissonClient.getLock(String.format(LOCK_TICKET_AVAILABILITY_TOKEN_BUCKET, requestParam.getTrainId()));
            if (!rLock.tryLock()) {
                throw new ServiceException("购票异常，请稍候再试");
            }
            try {
//                  双重校验
                if (!distributedCache.hasKey(tokenBucketHashKey)) {
                    Map<String, String> ticketAvailabilityTokenMap = new HashMap<>();
                    List<Integer> seatTypes = VehicleTypeEnum.findSeatTypesByCode(trainDO.getTrainType());
                    for (RouteDTO each : routeDTOList) {
//                        获得该列车的座位类型+数量
                        List<SeatTypeCountDTO> seatTypeCountDTOList = seatService
                                .listSeatTypeCount(Long.parseLong(requestParam.getTrainId()), each.getStartStation(), each.getEndStation(), seatTypes);
//                        key:站点+座位类型 : value:座位数量
                        for (SeatTypeCountDTO eachSeatTypeCountDTO : seatTypeCountDTOList) {
                            String buildCacheKey = StrUtil.join("_", each.getStartStation(), each.getEndStation(), eachSeatTypeCountDTO.getSeatType());
                            ticketAvailabilityTokenMap.put(buildCacheKey, String.valueOf(eachSeatTypeCountDTO.getSeatCount()));
                        }
                    }
                    stringRedisTemplate.opsForHash().putAll(tokenBucketHashKey, ticketAvailabilityTokenMap);
                }
            } finally {
                rLock.unlock();
            }
        }
//            从单例容器中获取lua脚本
        DefaultRedisScript<String> script = Singleton.get(LUA_TICKET_AVAILABILITY_TOKEN_BUCKET_PATH, () -> {
            DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(LUA_TICKET_AVAILABILITY_TOKEN_BUCKET_PATH)));
            redisScript.setResultType(String.class);
            return redisScript;
        });
        Assert.notNull(script);

//            统计 requestParam 中所有乘客按座位类型 (seatType) 分组后的数量
//            例如：{0: 2, 1: 1} 表示座位类型为 0 的座位有 2 个，座位类型为 1 的座位有 1 个
        Map<Integer, Long> seatTypeCountMap = requestParam.getPassengers().stream()
                .collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType, Collectors.counting()));
//            转为对应JSON字符串
        JSONArray seatTypeCountArray = seatTypeCountMap.entrySet().stream().map(entry -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("seatType", String.valueOf(entry.getKey()));
            jsonObject.put("count", String.valueOf(entry.getValue()));
            return jsonObject;
        }).collect(Collectors.toCollection(JSONArray::new));

//            列出要扣减的站点线
        List<RouteDTO> takeoutRouteDTOList = trainStationService
                .listTakeoutTrainStationRoute(requestParam.getTrainId(), requestParam.getDeparture(), requestParam.getArrival());

//            lua脚本的key
        String luaScriptKey = StrUtil.join("_", requestParam.getDeparture(), requestParam.getArrival());
//            执行lua脚本
        String resultStr = stringRedisTemplate.execute(script, Lists.newArrayList(tokenBucketHashKey, luaScriptKey), JSON.toJSONString(seatTypeCountArray), JSON.toJSONString(takeoutRouteDTOList));

//            解析脚本执行结果
        TokenResultDTO tokenResultDTO = JSON.parseObject(resultStr, TokenResultDTO.class);

//            判空返回结果
        return tokenResultDTO == null
                ? TokenResultDTO.builder().tokenIsNull(true).build()
                : tokenResultDTO;
    }


    /**
     * 回滚列车余量令牌，一般为订单取消或长时间未支付触发
     *
     * @param requestParam 回滚列车余量令牌入参
     */
    public void rollbackInBucket(TicketOrderDetailRespDTO requestParam) {

        DefaultRedisScript<Long> script = Singleton.get(LUA_TICKET_AVAILABILITY_ROLLBACK_TOKEN_BUCKET_PATH, () -> {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource( new ResourceScriptSource( new ClassPathResource(LUA_TICKET_AVAILABILITY_ROLLBACK_TOKEN_BUCKET_PATH)));
            redisScript.setResultType(Long.class);
            return redisScript;
        });
        Assert.notNull(script);

        List<TicketOrderPassengerDetailRespDTO> passengerDetails = requestParam.getPassengerDetails();
        Map<Integer, Long> seatTypeCountMap = passengerDetails.stream()
                .collect(Collectors.groupingBy(TicketOrderPassengerDetailRespDTO::getSeatType, Collectors.counting()));

        JSONArray seatTypeCountJsonArray = seatTypeCountMap.entrySet().stream()
                .map(entry -> {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("seatType", String.valueOf(entry.getKey()));
                    jsonObject.put("count", String.valueOf(entry.getValue()));
                    return jsonObject;
                }).collect(Collectors.toCollection(JSONArray::new));

        StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
//        准备key
        String bucketKey = TICKET_AVAILABILITY_TOKEN_BUCKET + requestParam.getTrainId();
        String luaScriptKey = StrUtil.join("_", requestParam.getDeparture(), requestParam.getArrival());

//        要扣减的站线
        List<RouteDTO> reduceRouteDTOList = trainStationService.listTakeoutTrainStationRoute(
                String.valueOf(requestParam.getTrainId()),
                requestParam.getDeparture(),
                requestParam.getArrival());

//        执行
        Long result = instance.execute(
                script,
                Lists.newArrayList(bucketKey, luaScriptKey),
                JSON.toJSONString(seatTypeCountJsonArray),
                JSON.toJSONString(reduceRouteDTOList));

        if (result == null || !result.equals(0L)) {
            log.error("回滚列车余票令牌失败，订单信息：{}", JSON.toJSONString(requestParam));
            throw new ServiceException("回滚列车余票令牌失败");
        }
    }

    /**
     * 删除令牌，一般在令牌与数据库不一致情况下触发
     *
     * @param requestParam 删除令牌容器参数
     */
    public void delTokenInBucket(PurchaseTicketReqDTO requestParam) {
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        String tokenBucketHashKey = TICKET_AVAILABILITY_TOKEN_BUCKET + requestParam.getTrainId();
        stringRedisTemplate.delete(tokenBucketHashKey);
    }
}
