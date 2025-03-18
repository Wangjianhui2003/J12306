package com.jianhui.project.orderservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jianhui.project.orderservice.dao.entity.OrderItemPassengerDO;
import com.jianhui.project.orderservice.dao.mapper.OrderItemPassengerMapper;
import com.jianhui.project.orderservice.service.OrderPassengerRelationService;
import org.springframework.stereotype.Service;

/**
 * 乘车人订单关系接口层实现
 */
@Service
public class OrderPassengerRelationServiceImpl extends ServiceImpl<OrderItemPassengerMapper, OrderItemPassengerDO> implements OrderPassengerRelationService {
}
