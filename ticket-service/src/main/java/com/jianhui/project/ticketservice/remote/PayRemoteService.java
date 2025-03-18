package com.jianhui.project.ticketservice.remote;

import com.jianhui.project.framework.starter.convention.result.Result;
import com.jianhui.project.ticketservice.remote.dto.PayInfoRespDTO;
import com.jianhui.project.ticketservice.remote.dto.RefundReqDTO;
import com.jianhui.project.ticketservice.remote.dto.RefundRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 支付单远程调用服务
 */
@FeignClient(value = "j12306-pay${unique-name:}-service",url = "${aggregation.remote-url:}")
public interface PayRemoteService{

    /**
     * 支付单详情查询
     */
    @GetMapping("/api/pay-service/pay/query")
    Result<PayInfoRespDTO> getPayInfo(@RequestParam(value = "orderSn") String orderSn);

    /**
     * 公共退款接口
     */
    @GetMapping("/api/pay-service/common/refund")
    Result<RefundRespDTO> commonRefund(@RequestBody RefundReqDTO requestParam);
}
