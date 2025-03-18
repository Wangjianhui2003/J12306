package com.jianhui.project.ticketservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.framework.starter.cache.core.CacheLoader;
import com.jianhui.project.framework.starter.cache.toolkit.CacheUtil;
import com.jianhui.project.framework.starter.common.enums.FlagEnum;
import com.jianhui.project.framework.starter.common.toolkit.BeanUtil;
import com.jianhui.project.framework.starter.convention.exception.ClientException;
import com.jianhui.project.ticketservice.common.enums.RegionStationQueryTypeEnum;
import com.jianhui.project.ticketservice.dao.entity.RegionDO;
import com.jianhui.project.ticketservice.dao.entity.StationDO;
import com.jianhui.project.ticketservice.dao.mapper.RegionMapper;
import com.jianhui.project.ticketservice.dao.mapper.StationMapper;
import com.jianhui.project.ticketservice.dto.req.RegionStationQueryReqDTO;
import com.jianhui.project.ticketservice.dto.resp.RegionStationQueryRespDTO;
import com.jianhui.project.ticketservice.dto.resp.StationQueryRespDTO;
import com.jianhui.project.ticketservice.service.RegionStationService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.jianhui.project.ticketservice.common.constant.J12306Constant.ADVANCE_TICKET_DAY;
import static com.jianhui.project.ticketservice.common.constant.RedisKeyConstant.*;

/**
 * 地区以及车站接口实现层
 */
@RestController
@RequiredArgsConstructor
public class RegionStationServiceImpl implements RegionStationService {

    private final DistributedCache distributedCache;
    private final RedissonClient redisson;
    private final StationMapper stationMapper;
    private final RegionMapper regionMapper;

    @Override
    public List<RegionStationQueryRespDTO> listRegionStation(RegionStationQueryReqDTO requestParam) {
        String key;
//        查车站
        if (StrUtil.isNotBlank(requestParam.getName())) {
            key = REGION_STATION + requestParam.getName();
            return safeGetRegionStation(
                    key ,
                    () -> {
                        LambdaQueryWrapper<StationDO> queryWrapper = Wrappers.lambdaQuery(StationDO.class)
//                                模糊查询
                                .likeRight(StationDO::getName, requestParam.getName())
                                .or()
                                .likeRight(StationDO::getSpell, requestParam.getName());
                        List<StationDO> stationDOList = stationMapper.selectList(queryWrapper);
                        return JSON.toJSONString(BeanUtil.convert(stationDOList, RegionStationQueryRespDTO.class));
                    },
                    requestParam.getName()
            );
        }
//        构造城市查询条件
        key = REGION_STATION + requestParam.getQueryType();
        LambdaQueryWrapper<RegionDO> queryWrapper = switch(requestParam.getQueryType()){
            case 0 -> Wrappers.lambdaQuery(RegionDO.class)
                    .eq(RegionDO::getPopularFlag, FlagEnum.TRUE.code());
            case 1 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.A_E.getSpells());
            case 2 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.F_J.getSpells());
            case 3 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.K_O.getSpells());
            case 4 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.P_T.getSpells());
            case 5 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.U_Z.getSpells());
            default -> throw new ClientException("查询失败，请检查查询参数是否正确");
        };
//        查缓存
        return safeGetRegionStation(
                key,
                () -> {
                    List<RegionDO> regionDOList = regionMapper.selectList(queryWrapper);
                    return com.alibaba.fastjson2.JSON.toJSONString(BeanUtil.convert(regionDOList, RegionStationQueryRespDTO.class));
                },
                String.valueOf(requestParam.getQueryType())
        );
    }

    @Override
    public List<StationQueryRespDTO> listAllStation() {
        return distributedCache.safeGet(
                STATION_ALL,
                List.class,
                () -> BeanUtil.convert(stationMapper.selectList(Wrappers.emptyWrapper()), StationQueryRespDTO.class),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS
        );
    }

    /**
     * 安全获取站点 TODO:有safeGet,为什么要这个
     */
    private List<RegionStationQueryRespDTO> safeGetRegionStation(String key, CacheLoader<String> loader, String param) {
        List<RegionStationQueryRespDTO> result = JSON.parseArray(distributedCache.get(key,String.class), RegionStationQueryRespDTO.class);
        if (CollUtil.isNotEmpty(result)) {
            return result;
        }
//        查询缓存或者loadAndSet
        String lockKey = String.format(LOCK_QUERY_REGION_STATION_LIST,param);
        RLock rLock = redisson.getLock(lockKey);
        rLock.lock();
        try{
            result = JSON.parseArray(distributedCache.get(key,String.class), RegionStationQueryRespDTO.class);
            if(CollUtil.isEmpty(result)){
                if(CollUtil.isEmpty(result = loadAndSet(key,loader))){
                    return Collections.emptyList();
                }
            }
        }finally{
            rLock.unlock();
        }
        return result;
    }

    /**
     * 加载并存入缓存
     */
    private List<RegionStationQueryRespDTO> loadAndSet(String key, CacheLoader<String> loader) {
        String result = loader.load();
        if (CacheUtil.isNullOrBlank(result)) {
            return Collections.emptyList();
        }
        List<RegionStationQueryRespDTO> respDTOList = JSON.parseArray(result, RegionStationQueryRespDTO.class);
        distributedCache.put(
                key,
                result,
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS
        );
        return respDTOList;
    }
}
