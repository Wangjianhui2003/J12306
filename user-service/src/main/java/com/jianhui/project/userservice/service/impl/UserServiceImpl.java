package com.jianhui.project.userservice.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jianhui.project.framework.starter.common.toolkit.BeanUtil;
import com.jianhui.project.framework.starter.convention.exception.ClientException;
import com.jianhui.project.userservice.dao.entity.UserDO;
import com.jianhui.project.userservice.dao.entity.UserDeletionDO;
import com.jianhui.project.userservice.dao.entity.UserMailDO;
import com.jianhui.project.userservice.dao.mapper.UserDeletionMapper;
import com.jianhui.project.userservice.dao.mapper.UserMailMapper;
import com.jianhui.project.userservice.dao.mapper.UserMapper;
import com.jianhui.project.userservice.dto.req.UserUpdateReqDTO;
import com.jianhui.project.userservice.dto.resp.UserQueryActualRespDTO;
import com.jianhui.project.userservice.dto.resp.UserQueryRespDTO;
import com.jianhui.project.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserDeletionMapper userDeletionMapper;
    private final UserMailMapper userMailMapper;

    @Override
    public UserQueryRespDTO queryUserByUserId(String userId) {
        LambdaQueryWrapper<UserDO> eq = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getId, userId);
        UserDO userDO = userMapper.selectOne(eq);
        if(userDO == null){
            throw new ClientException("用户不存在，请检查用户ID是否正确");
        }
        return BeanUtil.convert(userDO, UserQueryRespDTO.class);
    }

    @Override
    public UserQueryRespDTO queryUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> eq = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = userMapper.selectOne(eq);
        if (userDO == null) {
            throw new ClientException("用户不存在，请检查用户名是否正确");
        }
        return BeanUtil.convert(userDO, UserQueryRespDTO.class);
    }

    @Override
    public UserQueryActualRespDTO queryActualUserByUsername(String username) {
        return BeanUtil.convert(queryUserByUsername(username), UserQueryActualRespDTO.class);
    }

    @Override
    public Integer queryUserDeletionNum(Integer idType, String idCard) {
        LambdaQueryWrapper<UserDeletionDO> eq = Wrappers.lambdaQuery(UserDeletionDO.class)
                .eq(UserDeletionDO::getIdType, idType)
                .eq(UserDeletionDO::getIdCard, idCard);
        Long deletionNum = userDeletionMapper.selectCount(eq);
        return Optional.ofNullable(deletionNum).map(Long::intValue).orElse(0);
    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        UserDO userDO = BeanUtil.convert(requestParam,UserDO.class);
        LambdaUpdateWrapper<UserDO> eq = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        userMapper.update(userDO, eq);

        UserQueryRespDTO userQueryRespDTO = queryUserByUsername(requestParam.getUsername());
        if (StrUtil.isNotBlank(requestParam.getMail()) && !Objects.equals(requestParam.getMail(), userQueryRespDTO.getMail())) {
            LambdaUpdateWrapper<UserMailDO> updateWrapper = Wrappers.lambdaUpdate(UserMailDO.class)
                    .eq(UserMailDO::getMail, userQueryRespDTO.getMail());
            userMailMapper.delete(updateWrapper);
            UserMailDO userMailDO = UserMailDO.builder()
                    .mail(requestParam.getMail())
                    .username(requestParam.getUsername())
                    .build();
            userMailMapper.insert(userMailDO);
        }
    }
}
