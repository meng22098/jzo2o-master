package com.jzo2o.orders.manager.config;

import com.jzo2o.api.trade.NativePayApi;
import com.jzo2o.api.trade.dto.request.NativePayReqDTO;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class TradeClient {
    @Resource
    private NativePayApi nativePayApi;

    //@SentinelResource(value = "createDownLineTrading", fallback = "createDownLineTradingFallback", blockHandler = "createDownLineTradingBlockHandler")
    public NativePayResDTO createDownLineTrading(NativePayReqDTO nativePayDTO) {
        log.error("扫码支付，收银员通过收银台或商户后台调用此接口，生成二维码后，展示给用户，由用户扫描二维码完成订单支付。");
        // 调用其他微服务方法
        NativePayResDTO nativePayResDTO = nativePayApi.createDownLineTrading(nativePayDTO);
        return nativePayResDTO;
    }

    //执行异常走
    public NativePayResDTO createDownLineTradingFallback(Long id, Throwable throwable) {
        log.error("非限流、熔断等导致的异常执行的降级方法，id:{},throwable:", id, throwable);
        return null;
    }

    //熔断后的降级逻辑
//    public NativePayResDTO createDownLineTradingBlockHandler(Long id, BlockException blockException) {
//        log.error("触发限流、熔断时执行的降级方法，id:{},blockException:", id, blockException);
//        return null;
//    }
}
