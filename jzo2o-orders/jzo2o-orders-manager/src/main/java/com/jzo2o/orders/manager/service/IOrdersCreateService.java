package com.jzo2o.orders.manager.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.model.dto.request.OrderPageQueryReqDTO;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OperationOrdersDetailResDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 下单服务类
 * </p>
 *
 * @author ithyfjs
 * @since 2024-07-10
 */
public interface IOrdersCreateService extends IService<Orders> {
    void add(Orders orders);

    PlaceOrderResDTO placeOrder(PlaceOrderReqDTO placeOrderReqDTO);

    OrdersPayResDTO pay(Long id, OrdersPayReqDTO ordersPayReqDTO);

    OrdersPayResDTO getPayResultFromTradServer(Long id);
    void paySuccess(TradeStatusMsg tradeStatusMsg);

    List<Orders> queryOverTimePayOrdersListByCount(int i);
}
