package com.jzo2o.orders.manager.controller.operation;

import com.jzo2o.common.model.PageResult;
import com.jzo2o.orders.manager.model.dto.request.OrdersServePageQueryByServeProviderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.*;
import com.jzo2o.orders.manager.service.IOrdersServeManagerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author ithyfjs
 */
@RestController("orders-operation")
@Api(tags = "运营端-服务单相关接口")
@RequestMapping("/operation/ordersServe")
public class OperationOrdersServeController {


}
