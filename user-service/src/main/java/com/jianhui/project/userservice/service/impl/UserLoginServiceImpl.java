package com.jianhui.project.userservice.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.framework.starter.common.toolkit.BeanUtil;
import com.jianhui.project.framework.starter.convention.exception.ClientException;
import com.jianhui.project.framework.starter.convention.exception.ServiceException;
import com.jianhui.project.framework.starter.user.core.UserContext;
import com.jianhui.project.framework.starter.user.core.UserInfoDTO;
import com.jianhui.project.framework.starter.user.toolkit.JWTUtil;
import com.jianhui.project.frameworks.starter.designpattern.chain.AbstractChainContext;
import com.jianhui.project.userservice.common.enums.UserChainMarkEnum;
import com.jianhui.project.userservice.dao.entity.*;
import com.jianhui.project.userservice.dao.mapper.*;
import com.jianhui.project.userservice.dto.req.UserDeletionReqDTO;
import com.jianhui.project.userservice.dto.req.UserLoginReqDTO;
import com.jianhui.project.userservice.dto.req.UserRegisterReqDTO;
import com.jianhui.project.userservice.dto.resp.UserLoginRespDTO;
import com.jianhui.project.userservice.dto.resp.UserQueryRespDTO;
import com.jianhui.project.userservice.dto.resp.UserRegisterRespDTO;
import com.jianhui.project.userservice.service.UserLoginService;
import com.jianhui.project.userservice.toolkit.UserReuseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.jianhui.project.userservice.common.constant.RedisKeyConstant.*;
import static com.jianhui.project.userservice.common.enums.UserRegisterErrorCodeEnum.*;

/**
 * 用户登录服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserLoginServiceImpl implements UserLoginService {

    private final AbstractChainContext abstractChainContext;
    private final RedissonClient redissonClient;
    private final UserMapper userMapper;
    private final UserMailMapper userMailMapper;
    private final UserReuseMapper userReuseMapper;
    private final DistributedCache distributedCache;
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final UserPhoneMapper userPhoneMapper;
    private final UserServiceImpl userService;
    private final UserDeletionMapper userDeletionMapper;

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        String usernameOrMailOrPhone = requestParam.getUsernameOrMailOrPhone();
//        是否是邮箱
        boolean mailFlag = false;
        for (char c : usernameOrMailOrPhone.toCharArray()) {
            if (c == '@') {
                mailFlag = true;
                break;
            }
        }
        String username;
//        邮箱登录
        if (mailFlag) {
            LambdaQueryWrapper<UserMailDO> eq = Wrappers.lambdaQuery(UserMailDO.class)
                    .eq(UserMailDO::getMail, usernameOrMailOrPhone);
            username = Optional.ofNullable(userMailMapper.selectOne(eq))
                    .map(UserMailDO::getUsername)
                    .orElseThrow(() -> new ClientException("用户名/手机号/邮箱不存在"));
        } else {
//            查询用户手机表,看是否是手机号登录
            LambdaQueryWrapper<UserPhoneDO> eq = Wrappers.lambdaQuery(UserPhoneDO.class)
                    .eq(UserPhoneDO::getPhone, usernameOrMailOrPhone);
            username = Optional.ofNullable(userPhoneMapper.selectOne(eq))
                    .map(UserPhoneDO::getUsername)
                    .orElse(null);
        }
//        username还是空,就用户名登录
        username = Optional.ofNullable(username).orElse(usernameOrMailOrPhone);
//        查询userDO
        LambdaQueryWrapper<UserDO> select = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getPassword, requestParam.getPassword())
                .select(UserDO::getId, UserDO::getUsername, UserDO::getRealName);
        UserDO userDO = userMapper.selectOne(select);
//        非空,用id+name+realname座位jwt的信息
        if (userDO != null) {
            UserInfoDTO userInfoDTO = UserInfoDTO.builder()
                    .userId(String.valueOf(userDO.getId()))
                    .username(userDO.getUsername())
                    .realName(userDO.getRealName())
                    .build();
//            生成JWT令牌
            String accessToken = JWTUtil.generateAccessToken(userInfoDTO);
//            构造返回值
            UserLoginRespDTO userLoginRespDTO = new UserLoginRespDTO(
                    userInfoDTO.getUserId(),
                    requestParam.getUsernameOrMailOrPhone(),
                    userDO.getRealName(),
                    accessToken);
//            accessToken作为key,respDTO作为value
            distributedCache.put(accessToken, JSON.toJSONString(userLoginRespDTO), 30, TimeUnit.MINUTES);
            return userLoginRespDTO;
        }
        throw new ServiceException("账号不存在或密码错误");
    }

    @Override
    public UserLoginRespDTO checkLogin(String accessToken) {
        return distributedCache.get(accessToken, UserLoginRespDTO.class);
    }

    @Override
    public void logout(String accessToken) {
        if (StrUtil.isNotBlank(accessToken)) {
            distributedCache.delete(accessToken);
        }
    }

    @Override
    public Boolean hasUsername(String username) {
        boolean contains = userRegisterCachePenetrationBloomFilter.contains(username);
        if (contains) {
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            return instance.opsForSet().isMember(USER_REGISTER_REUSE_SHARDING + UserReuseUtil.hashShardingIdx(username), username);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRegisterRespDTO register(UserRegisterReqDTO requestParam) {
//        责任链处理
        abstractChainContext.handler(UserChainMarkEnum.USER_REGISTER_FILTER.name(), requestParam);
//        注册逻辑
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER + requestParam.getUsername());
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            throw new ServiceException(HAS_USERNAME_NOTNULL);
        }
        try {
//            插入用户信息
            try {
                int inserted = userMapper.insert(BeanUtil.convert(requestParam, UserDO.class));
                if (inserted < 1) {
                    throw new ServiceException(USER_REGISTER_FAIL);
                }
            } catch (DuplicateKeyException dke) {
                log.error("用户名[{}]重复注册", requestParam.getUsername());
                throw new ServiceException(HAS_USERNAME_NOTNULL);
            }
//            插入邮箱信息
            if (StrUtil.isNotBlank(requestParam.getMail())) {
                UserMailDO userMailDO = UserMailDO.builder()
                        .username(requestParam.getUsername())
                        .mail(requestParam.getMail())
                        .build();
                try {
                    userMailMapper.insert(userMailDO);
                } catch (DuplicateKeyException dke) {
                    log.error("用户 [{}] 注册邮箱 [{}] 重复", requestParam.getUsername(), requestParam.getMail());
                    throw new ServiceException(MAIL_REGISTERED);
                }
            }
            String username = requestParam.getUsername();
//            复用数据库删除
            userReuseMapper.delete(Wrappers.update(new UserReuseDO(username)));
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
//            复用缓存删除
            instance.opsForSet()
                    .remove(USER_REGISTER_REUSE_SHARDING + UserReuseUtil.hashShardingIdx(username),
                            username);
//            布隆过滤器添加
            userRegisterCachePenetrationBloomFilter.add(username);
        } finally {
            lock.unlock();
        }
        return BeanUtil.convert(requestParam, UserRegisterRespDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletion(UserDeletionReqDTO requestParam) {
        String username = UserContext.getUsername();
        if (!username.equals(requestParam.getUsername())){
            throw new ClientException("注销账号与登录账号不一致");
        }
        RLock lock = redissonClient.getLock(USER_DELETION + requestParam.getUsername());
//        TODO:为什么用lock而不是tryLock?
        lock.lock();
        try {
            UserQueryRespDTO userQueryRespDTO = userService.queryUserByUsername(username);
//        注销用户信息插入到t_user_deletion
            UserDeletionDO userDeletionDO = UserDeletionDO.builder()
                    .idType(userQueryRespDTO.getIdType())
                    .idCard(userQueryRespDTO.getIdCard())
                    .build();
            userDeletionMapper.insert(userDeletionDO);
//        删除用户信息,逻辑删除,修改数据库表记录 del_flag,mybatis-plus不支持update修改del_flag
            UserDO userDO = new UserDO();
            userDO.setDeletionTime(System.currentTimeMillis());
            userDO.setUsername(username);
            userMapper.deletionUser(userDO);
//            删除电话
            UserPhoneDO userPhoneDO = UserPhoneDO.builder()
                    .phone(userQueryRespDTO.getPhone())
                    .deletionTime(System.currentTimeMillis())
                    .build();
            userPhoneMapper.deletionUser(userPhoneDO);
//        有邮箱,删除
            if (StrUtil.isNotBlank(userQueryRespDTO.getMail())) {
                UserMailDO userMailDO = UserMailDO.builder()
                        .mail(userQueryRespDTO.getMail())
                        .deletionTime(System.currentTimeMillis())
                        .build();
                userMailMapper.deletionUser(userMailDO);
            }
//        删除缓存中的token
            distributedCache.delete(UserContext.getToken());
//        用户名可以重新使用,插入到reuse表
            userReuseMapper.insert(new UserReuseDO(username));
//        插入到用户名可重用缓存
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            instance.opsForSet().add(USER_REGISTER_REUSE_SHARDING + UserReuseUtil.hashShardingIdx(username), username);
        } finally {
            lock.unlock();
        }
    }
}
