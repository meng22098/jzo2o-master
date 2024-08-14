package com.jzo2o.orders.manager.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.db.DbRuntimeException;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.common.constants.UserType;
import com.jzo2o.common.enums.EnableStatusEnum;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderRefundStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.model.dto.OrderUpdateStatusDTO;
import com.jzo2o.orders.base.service.IOrdersCommonService;
import com.jzo2o.orders.manager.handler.OrdersHandler;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.service.IOrdersCanceledService;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.service.IOrdersRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.jzo2o.orders.base.constants.FieldConstants.SORT_BY;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author ithyfjs
 * @since 2024-07-10
 */
@Slf4j
@Service
public class OrdersManagerServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersManagerService {
    @Resource
    private IOrdersCanceledService ordersCanceledService;
    @Resource
    private IOrdersCommonService ordersCommonService;
    @Resource
    private IOrdersRefundService ordersRefundService;
    @Resource
    private IOrdersCreateService ordersCreateService;
    @Resource
    OrdersHandler ordersHandler;
    @Override
    public List<Orders> batchQuery(List<Long> ids) {
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery().in(Orders::getId, ids).ge(Orders::getUserId, 0);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public Orders queryById(Long id) {
        return baseMapper.selectById(id);
    }

    /**
     * 滚动分页查询
     *
     * @param currentUserId 当前用户id
     * @param ordersStatus  订单状态，0：待支付，100：派单中，200：待服务，300：服务中，400：待评价，500：订单完成，600：已取消，700：已关闭
     * @param sortBy        排序字段
     * @return 订单列表
     */
    @Override
    public List<OrderSimpleResDTO> consumerQueryList(Long currentUserId, Integer ordersStatus, Long sortBy) {
        //1.构件查询条件
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery()
                .eq(ObjectUtils.isNotNull(ordersStatus), Orders::getOrdersStatus, ordersStatus)
                .lt(ObjectUtils.isNotNull(sortBy), Orders::getSortBy, sortBy)
                .eq(Orders::getUserId, currentUserId)
                .eq(Orders::getDisplay, EnableStatusEnum.ENABLE.getStatus());
        Page<Orders> queryPage = new Page<>();
        queryPage.addOrder(OrderItem.desc(SORT_BY));
        queryPage.setSearchCount(false);

        //2.查询订单列表
        Page<Orders> ordersPage = baseMapper.selectPage(queryPage, queryWrapper);
        List<Orders> records = ordersPage.getRecords();
        List<OrderSimpleResDTO> orderSimpleResDTOS = BeanUtil.copyToList(records, OrderSimpleResDTO.class);
        return orderSimpleResDTOS;

    }
    /**
     * 根据订单id查询
     *
     * @param id 订单id
     * @return 订单详情
     */
    @Override
    public OrderResDTO getDetail(Long id) {
        Orders orders = queryById(id);

        //如果支付过期则取消订单【新加的】
        orders = canalIfPayOvertime(orders);
        OrderResDTO orderResDTO = BeanUtil.toBean(orders, OrderResDTO.class);
        return orderResDTO;
    }
    /**
     * 如果支付过期则取消订单
     * @param orders
     */
    private Orders canalIfPayOvertime(Orders orders){
        //创建订单未支付15分钟后自动取消
        if(Objects.equals(orders.getOrdersStatus(),
                OrderStatusEnum.NO_PAY.getStatus())
                && orders.getCreateTime().plusMinutes(15).isBefore(LocalDateTime.now()))
        {
            // 查询支付结果，如果支付最新状态仍是未支付进行取消订单
            // 避免我们点击详情的时候，有好心人帮我们支付好了
            OrdersPayResDTO ordersPayResDTO = ordersCreateService.getPayResultFromTradServer(orders.getId());
            int payResultFromTradServer = ordersPayResDTO.getPayStatus();
            if(payResultFromTradServer !=
                    OrderPayStatusEnum.PAY_SUCCESS.getStatus()){
                //取消订单
                OrderCancelDTO orderCancelDTO = BeanUtil.toBean(orders,
                        OrderCancelDTO.class);
                orderCancelDTO.setCurrentUserType(UserType.SYSTEM);
                orderCancelDTO.setCancelReason("订单超时支付，自动取消");
                // 调用前面写好的方法(里面自动实现已支付-未支付的取消逻辑)
                cancel(orderCancelDTO);
                orders = getById(orders.getId());
            }
        }
        return orders;
    }


    /**
     * 订单评价
     *
     * @param ordersId 订单id
     */
    @Override
    @Transactional
    public void evaluationOrder(Long ordersId) {
//        //查询订单详情
//        Orders orders = queryById(ordersId);
//
//        //构建订单快照
//        OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
//                .evaluationTime(LocalDateTime.now())
//                .build();
//
//        //订单状态变更
////        orderStateMachine.changeStatus(orders.getUserId(), orders.getId().toString(),OrderStatusChangeEventEnum.EVALUATE, orderSnapshotDTO);
    }

    /**
     * 取消订单
     *
     * @param orderCancelDTO 取消订单模型
     */
    /**
     * 取消订单
     *
     * @param orderCancelDTO 取消订单模型
     */
    @Override
    public void cancel(OrderCancelDTO orderCancelDTO) {
        //查询订单信息
        Orders orders = getById(orderCancelDTO.getId());
        BeanUtils.copyProperties(orders,orderCancelDTO);
        if (ObjectUtil.isNull(orders)) {
            throw new DbRuntimeException("找不到要取消的订单,订单号：{}",orderCancelDTO.getId());
        }
        //订单状态
        Integer ordersStatus = orders.getOrdersStatus();
        if(Objects.equals(OrderStatusEnum.NO_PAY.getStatus(), ordersStatus)){
            //订单状态为待支付
            log.info("[未支付订单退款]：" + orderCancelDTO);
            cancelByNoPay(orderCancelDTO);
        }else if(Objects.equals(OrderStatusEnum.DISPATCHING.getStatus(), ordersStatus)){
            //订单状态为派单中
            log.info("[已支付订单退款]取消订单对象：{}， 订单：{}， 订单id：{}", orderCancelDTO, orders, orderCancelDTO.getId());
            cancelByDispatching(orderCancelDTO, orders.getTradingOrderNo(),orders.getRealPayAmount());
            //新启动一个线程请求退款
            ordersHandler.requestRefundNewThread(orders.getId());
        }else{
            throw new CommonException("当前订单状态不支持取消");
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByDispatching(OrderCancelDTO orderCancelDTO, Long tradingOrderNo, BigDecimal realPayAmount) {
        //保存取消订单记录(与未支付的一样)
        OrdersCanceled ordersCanceled = BeanUtil.toBean(orderCancelDTO,OrdersCanceled.class);
        ordersCanceled.setCancellerId(orderCancelDTO.getCurrentUserId());
        ordersCanceled.setCancelerName(orderCancelDTO.getCurrentUserName());
        ordersCanceled.setCancellerType(orderCancelDTO.getCurrentUserType());
        ordersCanceled.setCancelTime(LocalDateTime.now());
        OrdersCanceled ordersCanceled1= ordersCanceledService.getById(ordersCanceled.getId());
        if (Objects.nonNull(ordersCanceled1))
        {
            ordersCanceledService.updateById(ordersCanceled);
        }
        else {
            ordersCanceledService.save(ordersCanceled);
        }
        //更新订单状态为关闭订(状态与未支付的不一样)
        OrderUpdateStatusDTO orderUpdateStatusDTO = OrderUpdateStatusDTO.builder()
                        .id(orderCancelDTO.getId())
                        .targetStatus(OrderStatusEnum.CLOSED.getStatus())//自己加的
                        .originStatus(OrderStatusEnum.DISPATCHING.getStatus())
                        .refundStatus(OrderRefundStatusEnum.REFUNDING.getStatus())//退款状态为退款中
                        .build();
        int result = ordersCommonService.updateStatus(orderUpdateStatusDTO);
        if (result <= 0) {
            throw new DbRuntimeException("待服务订单关闭事件处理失败");
        }
        //添加退款记录(相对未支付逻辑，这里是新增的)
        OrdersRefund ordersRefund = new OrdersRefund();
        ordersRefund.setId(orderCancelDTO.getId());
        ordersRefund.setTradingOrderNo(tradingOrderNo);
        ordersRefund.setRealPayAmount(realPayAmount);
        ordersRefundService.save(ordersRefund);
    }
    /**
     * 查询超时订单id列表
     *
     * @param count 数量
     * @return 订单id列表
     */
    @Override
    public List<Orders> queryOverTimePayOrdersListByCount(Integer count) {
        //根据订单创建时间查询超过15分钟未支付的订单
        List<Orders> list = lambdaQuery()
                //查询待支付状态的订单
                .eq(Orders::getOrdersStatus, OrderStatusEnum.NO_PAY.getStatus())
                //小于当前时间减去15分钟，即待支付状态已过15分钟
                .lt(Orders::getCreateTime, LocalDateTime.now().minusMinutes(15))
                .last("limit " + count)
                .list();
        return list;
    }

    //未支付状态取消订单
    @Transactional(rollbackFor = Exception.class)
    public void cancelByNoPay(OrderCancelDTO orderCancelDTO) {
        //保存取消订单记录
        OrdersCanceled ordersCanceled = BeanUtil.toBean(orderCancelDTO,
                OrdersCanceled.class);
        ordersCanceled.setCancellerId(orderCancelDTO.getCurrentUserId());
        ordersCanceled.setCancelerName(orderCancelDTO.getCurrentUserName());
        ordersCanceled.setCancellerType(orderCancelDTO.getCurrentUserType());
        ordersCanceled.setCancelTime(LocalDateTime.now());
        ordersCanceledService.save(ordersCanceled);
        //更新订单状态为取消订单
        /**
         * 原始：update orders set order_status=600 where id = #{id}
         *  线程1：0->600
         *  线程2：600->600
         *  线程3：线程1执行完，线程2阻塞中开始执行，此时订单状态是600(已取消)，假如说我们后续可以
         让取消的订单重新支付，已取消->待支付->支付成功
         * 乐观锁版本(会产生ABA问题)：update orders set order_status=600 where id = #
         {id} and order_status=0
         */
        OrderUpdateStatusDTO orderUpdateStatusDTO = OrderUpdateStatusDTO.builder()
                .id(orderCancelDTO.getId())
                .originStatus(OrderStatusEnum.NO_PAY.getStatus())
                .targetStatus(OrderStatusEnum.CANCELED.getStatus())
                .build();
        int result = ordersCommonService.updateStatus(orderUpdateStatusDTO);
        if (result <= 0) {
            throw new DbRuntimeException("订单取消事件处理失败");
        }
    }
}
