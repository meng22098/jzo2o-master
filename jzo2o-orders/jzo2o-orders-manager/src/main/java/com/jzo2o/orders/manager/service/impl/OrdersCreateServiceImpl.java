package com.jzo2o.orders.manager.service.impl;

import cn.hutool.db.DbRuntimeException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.common.utils.NumberUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.config.CustomerClient;
import com.jzo2o.orders.manager.config.FoundationsClient;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.jzo2o.orders.base.constants.RedisConstants.Lock.ORDERS_SHARD_KEY_ID_GENERATOR;

/**
 * <p>
 * 下单服务类
 * </p>
 *
 * @author ithyfjs
 * @since 2024-07-10
 */
@Slf4j
@Service
public class OrdersCreateServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersCreateService {
    @Resource
    private CustomerClient customerClient;
    @Resource
    private FoundationsClient foundationsClient;
    @Resource
    private RedisTemplate<String, Long> redisTemplate;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaceOrderResDTO placeOrder(PlaceOrderReqDTO placeOrderReqDTO) {
        //1.生成订单
        //1.1 订单基本信息
        //1.2 下单人信息-远程调用jzo2o-customer服务
        AddressBookResDTO customerDetail = customerClient.getDetail(placeOrderReqDTO.getAddressBookId());
        //1.3 订单服务信息-远程调用jzo2o-foundations服务
        ServeAggregationResDTO foundationsDetail = foundationsClient.getDetail(placeOrderReqDTO.getServeId());
        //服务下架不可下单
        if (foundationsDetail == null || foundationsDetail.getSaleStatus() != 2) {
            throw new BadRequestException("服务不可用");
        }
        //1.4 生成订单id
        Long orderId = generateOrderId();
        //1.5 订单基础信息
        Orders orders = new Orders();
        //1.5.0 下单人id
        orders.setUserId(UserContext.currentUserId());
        //1.5.1 设置订单id
        orders.setId(orderId);
        //1.5.2 设置服务类型id
        orders.setServeTypeId(foundationsDetail.getServeTypeId());
        //1.5.3 设置服务类型名称
        orders.setServeTypeName(foundationsDetail.getServeTypeName());
        //1.5.4 设置服务项id
        orders.setServeItemId(foundationsDetail.getServeItemId());
        //1.5.5 设置服务项名称
        orders.setServeItemName(foundationsDetail.getServeItemName());
        //1.5.6 设置服务项图片
        orders.setServeItemImg(foundationsDetail.getServeItemImg());
        //1.5.7 设置服务单位
        orders.setUnit(foundationsDetail.getUnit());
        //1.5.8 设置服务id
        orders.setServeId(placeOrderReqDTO.getServeId());
        //1.5.9 设置订单状态
        orders.setOrdersStatus(OrderStatusEnum.NO_PAY.getStatus());
        //1.5.10 设置支付状态
        orders.setPayStatus(OrderPayStatusEnum.NO_PAY.getStatus());
        //1.6 订单价格信息
        //1.6.1 单价
        orders.setPrice(foundationsDetail.getPrice());
        //1.6.1 购买数量
        orders.setPurNum(placeOrderReqDTO.getPurNum());
        //1.6.2 总价
        BigDecimal totalAmount = foundationsDetail.getPrice().multiply(new BigDecimal(placeOrderReqDTO.getPurNum()));
        orders.setTotalAmount(totalAmount);
        //1.6.3 优惠金额
        orders.setDiscountAmount(BigDecimal.ZERO);
        //1.6.4 实付金额
        orders.setRealPayAmount(NumberUtils.sub(orders.getTotalAmount(), orders.getDiscountAmount()));
        //1.7 服务地址信息
        //1.7.1 设置服务cityCode
        orders.setCityCode(foundationsDetail.getCityCode());
        //1.7.2 设置服务地址
        String serveAddr = new StringBuffer()
                .append(customerDetail.getProvince())
                .append(customerDetail.getCity())
                .append(customerDetail.getCounty())
                .append(customerDetail.getAddress())
                .toString();
        orders.setServeAddress(serveAddr);
        //1.7.3 设置服务人电话
        orders.setContactsPhone(customerDetail.getPhone());
        //1.7.4 设置服务人姓名
        orders.setContactsName(customerDetail.getName());
        //1.8 服务其他信息
        //1.8.1 设置服务开始时间
        orders.setServeStartTime(placeOrderReqDTO.getServeStartTime());
        //1.8.2 经纬度
        orders.setLon(customerDetail.getLon());
        orders.setLat(customerDetail.getLat());
        //1.8.3 排序字段
        long sortBy = DateUtils.toEpochMilli(orders.getServeStartTime()) + orders.getId() % 100000;
        orders.setSortBy(sortBy);

        //2 插入数据库
        boolean save = this.save(orders);
        if (!save) {
            throw new DbRuntimeException("下单失败");
        }
        return new PlaceOrderResDTO(orders.getId());
    }

    /**
     * 生成订单id 格式：{yyMMdd}{13位id}
     *
     * @return
     */
    private Long generateOrderId() {
        //通过Redis自增序列得到序号
        Long id = redisTemplate.opsForValue().increment(ORDERS_SHARD_KEY_ID_GENERATOR, 1);
        //生成订单号   2位年+2位月+2位日+13位序号
        long orderId = DateUtils.getFormatDate(LocalDateTime.now(), "yyMMdd") * 10000000000000L + id;
        return orderId;
    }
}
