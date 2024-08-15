package com.jzo2o.orders.manager.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.db.DbRuntimeException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.api.trade.TradingApi;
import com.jzo2o.api.trade.dto.request.NativePayReqDTO;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import com.jzo2o.api.trade.dto.response.TradingResDTO;
import com.jzo2o.api.trade.enums.PayChannelEnum;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.common.utils.NumberUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderRefundStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.base.model.dto.OrderUpdateStatusDTO;
import com.jzo2o.orders.base.service.IOrdersCommonService;
import com.jzo2o.orders.manager.config.CustomerClient;
import com.jzo2o.orders.manager.config.FoundationsClient;
import com.jzo2o.orders.manager.config.TradeClient;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.porperties.TradeProperties;
import com.jzo2o.orders.manager.service.IOrdersCanceledService;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.IOrdersRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static com.jzo2o.common.constants.ErrorInfo.Code.TRADE_FAILED;
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
    @Resource
    private TradeClient tradeClient;
    @Resource
    private TradeProperties tradeProperties;
    @Resource
    private TradingApi tradingApi;
    @Resource
    IOrdersCreateService owner;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Orders orders) {
        boolean save = this.save(orders);
        if (!save) {
            throw new DbRuntimeException("下单失败");
        }
    }

    @Override
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
        System.out.println(customerDetail);
        System.out.println(foundationsDetail);
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
        //保存订单
        this.add(orders);
        return new PlaceOrderResDTO(orders.getId());
    }

    @Override
    public OrdersPayResDTO pay(Long id, OrdersPayReqDTO ordersPayReqDTO) {
        Orders orders =  baseMapper.selectById(id);
        if (ObjectUtil.isNull(orders)) {
            throw new CommonException(TRADE_FAILED, "订单不存在");
        }
        //订单的支付状态为成功直接返回
        if (OrderPayStatusEnum.PAY_SUCCESS.getStatus() == orders.getPayStatus()
                && ObjectUtil.isNotEmpty(orders.getTradingOrderNo())) {
            OrdersPayResDTO ordersPayResDTO = new OrdersPayResDTO();
            BeanUtil.copyProperties(orders, ordersPayResDTO);
            ordersPayResDTO.setProductOrderNo(orders.getId());
            return ordersPayResDTO;
        } else {
            //生成二维码
            NativePayResDTO nativePayResDTO = generateQrCode(orders, ordersPayReqDTO.getTradingChannel());
            OrdersPayResDTO ordersPayResDTO = BeanUtil.toBean(nativePayResDTO, OrdersPayResDTO.class);
            return ordersPayResDTO;
        }

    }

    //生成二维码
    private NativePayResDTO generateQrCode(Orders orders, PayChannelEnum tradingChannel) {

        //判断支付渠道
        Long enterpriseId = ObjectUtil.equal(PayChannelEnum.ALI_PAY, tradingChannel) ?
                tradeProperties.getAliEnterpriseId() : tradeProperties.getWechatEnterpriseId();

        //构建支付请求参数
        NativePayReqDTO nativePayReqDTO = new NativePayReqDTO();
        //商户号
        nativePayReqDTO.setEnterpriseId(enterpriseId);
        //业务系统标识
        nativePayReqDTO.setProductAppId("jzo2o.orders");
        //家政订单号
        nativePayReqDTO.setProductOrderNo(orders.getId());
        //支付渠道
        nativePayReqDTO.setTradingChannel(tradingChannel);
        //支付金额
        nativePayReqDTO.setTradingAmount(orders.getRealPayAmount());
        //备注信息
        nativePayReqDTO.setMemo(orders.getServeItemName());
        //判断是否切换支付渠道
        if (ObjectUtil.isNotEmpty(orders.getTradingChannel())
                && ObjectUtil.notEqual(orders.getTradingChannel(), tradingChannel.toString())) {
            nativePayReqDTO.setChangeChannel(true);
        }
        //生成支付二维码
        NativePayResDTO downLineTrading = tradeClient.createDownLineTrading(nativePayReqDTO);
        if(ObjectUtils.isNotNull(downLineTrading)){
            log.info("订单:{}请求支付,生成二维码:{}",orders.getId(),downLineTrading.toString());
            //将二维码更新到交易订单中
            boolean update = lambdaUpdate()
                    .eq(Orders::getId, downLineTrading.getProductOrderNo())
                    .set(Orders::getTradingOrderNo, downLineTrading.getTradingOrderNo())
                    .set(Orders::getTradingChannel, downLineTrading.getTradingChannel())
                    .update();
            if(!update){
                throw new CommonException("订单:"+orders.getId()+"请求支付更新交易单号失败");
            }
        }
        return downLineTrading;
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
    @Override
    public OrdersPayResDTO getPayResultFromTradServer(Long id) {
        //查询订单表
        Orders orders = baseMapper.selectById(id);
        if (ObjectUtil.isNull(orders)) {
            throw new CommonException(TRADE_FAILED, "订单不存在");
        }
        //支付结果
        Integer payStatus = orders.getPayStatus();
        //未支付且已存在支付服务的交易单号此时远程调用支付服务查询支付结果
        if (OrderPayStatusEnum.NO_PAY.getStatus() == payStatus
                && ObjectUtil.isNotEmpty(orders.getTradingOrderNo())) {
            //远程调用支付服务查询支付结果
            TradingResDTO tradingResDTO =
                    tradingApi.findTradResultByTradingOrderNo(orders.getTradingOrderNo());
            //如果支付成功这里更新订单状态
            if (ObjectUtil.isNotNull(tradingResDTO) && ObjectUtil.equals(tradingResDTO.getTradingState(),TradingStateEnum.YJS)) {
                //设置订单的支付状态成功
                TradeStatusMsg msg = TradeStatusMsg.builder()
                        .productOrderNo(orders.getId())
                        .tradingChannel(tradingResDTO.getTradingChannel())
                        .statusCode(TradingStateEnum.YJS.getCode())
                        .tradingOrderNo(tradingResDTO.getTradingOrderNo())
                        .transactionId(tradingResDTO.getTransactionId())
                        .build();
                owner.paySuccess(msg);
                //构造返回数据
                OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(msg, OrdersPayResDTO.class);
                ordersPayResDTO.setPayStatus(OrderPayStatusEnum.PAY_SUCCESS.getStatus());
                return ordersPayResDTO;
            }
        }
        OrdersPayResDTO ordersPayResDTO = new OrdersPayResDTO();
        ordersPayResDTO.setPayStatus(payStatus);
        ordersPayResDTO.setProductOrderNo(orders.getId());
        ordersPayResDTO.setTradingOrderNo(orders.getTradingOrderNo());
        ordersPayResDTO.setTradingChannel(orders.getTradingChannel());
        return ordersPayResDTO;
    }
    /**
     * 支付成功， 其他信息暂且不填
     *
     * @param tradeStatusMsg 交易状态消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(TradeStatusMsg tradeStatusMsg) {
        //查询订单
        Orders orders = baseMapper.selectById(tradeStatusMsg.getProductOrderNo());
        if (ObjectUtil.isNull(orders)) {
            throw new CommonException(TRADE_FAILED, "订单不存在");
        }
        //校验支付状态如果不是待支付状态则不作处理
        if (ObjectUtil.notEqual(OrderPayStatusEnum.NO_PAY.getStatus(),
                orders.getPayStatus())) {
            log.info("更新订单支付成功，当前订单:{}支付状态不是待支付状态", orders.getId());
            return;
        }
        //校验订单状态如果不是待支付状态则不作处理
        if
        (ObjectUtils.notEqual(OrderStatusEnum.NO_PAY.getStatus(),orders.getOrdersStatus(
        ))) {
            log.info("更新订单支付成功，当前订单:{}状态不是待支付状态", orders.getId());
        }
        //第三方支付单号校验
        if (ObjectUtil.isEmpty(tradeStatusMsg.getTransactionId())) {
            throw new CommonException("支付成功通知缺少第三方支付单号");
        }
        //更新订单的支付状态及第三方交易单号等信息
        boolean update = lambdaUpdate()
                .eq(Orders::getId, orders.getId())
                .set(Orders::getPayTime, LocalDateTime.now())//支付时间
                .set(Orders::getTradingOrderNo, tradeStatusMsg.getTradingOrderNo())//交易单号
                .set(Orders::getTradingChannel, tradeStatusMsg.getTradingChannel())//支付渠道
                .set(Orders::getTransactionId, tradeStatusMsg.getTransactionId())//第 三方支付交易号
                .set(Orders::getPayStatus,
                        OrderPayStatusEnum.PAY_SUCCESS.getStatus())//支付状态
                .set(Orders::getOrdersStatus,
                        OrderStatusEnum.DISPATCHING.getStatus())//订单状态更新为派单中
                .update();
        if(!update){
            log.info("更新订单:{}支付成功失败", orders.getId());
            throw new CommonException("更新订单"+orders.getId()+"支付成功失败");
        }
    }

    @Override
    public List<Orders> queryOverTimePayOrdersListByCount(int count) {
        //根据订单创建时间查询超过15分钟未支付的订单
        List<Orders> list = lambdaQuery()
                //查询待支付状态的订单
                .eq(Orders::getOrdersStatus, OrderStatusEnum.NO_PAY.getStatus())
                .lt(Orders::getCreateTime, LocalDateTime.now().minusMinutes(15))
                .last("limit " + count)
                .list();
        return list;
    }

}
