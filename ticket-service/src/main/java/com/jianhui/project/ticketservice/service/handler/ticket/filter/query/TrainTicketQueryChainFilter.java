package com.jianhui.project.ticketservice.service.handler.ticket.filter.query;

import com.jianhui.project.frameworks.starter.designpattern.chain.AbstractChainHandler;
import com.jianhui.project.ticketservice.common.enums.TicketChainMarkEnum;
import com.jianhui.project.ticketservice.dto.req.TicketPageQueryReqDTO;

/**
 * 列车车票查询过滤器
 */
public interface TrainTicketQueryChainFilter<T extends TicketPageQueryReqDTO> extends AbstractChainHandler<TicketPageQueryReqDTO> {

    @Override
    default String mark() {
        return TicketChainMarkEnum.TRAIN_QUERY_FILTER.name();
    }
}
