package com.jzo2o.orders.manager.config;

import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class FoundationsClient {

    @Resource
    private ServeApi serveApi;

//    @SentinelResource(value = "getServeDetail", fallback = "detailFallback", blockHandler = "detailBlockHandler")
    public ServeAggregationResDTO getDetail(Long id) {
        log.error("根据id查询服务详情，id:{}", id);
        // 调用其他微服务方法
        ServeAggregationResDTO serveDetail = serveApi.findById(id);
        return serveDetail;
    }

    //执行异常走
    public AddressBookResDTO detailFallback(Long id, Throwable throwable) {
        log.error("非限流、熔断等导致的异常执行的降级方法，id:{},throwable:", id, throwable);
        return null;
    }

    //熔断后的降级逻辑
//    public AddressBookResDTO detailBlockHandler(Long id, BlockException blockException) {
//        log.error("触发限流、熔断时执行的降级方法，id:{},blockException:", id, blockException);
//        return null;
//    }
}
