package com.jzo2o.customer.controller.worker;

import com.jzo2o.customer.model.dto.request.WorkerCertificationAuditAddReqDTO;
import com.jzo2o.customer.service.IWorkerCertificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController("workerCertificationAuditController")
@RequestMapping("/worker/worker-certification-audit")
@Api(tags = "服务端 - 认证审核相关接口")
public class CertificationAuditController {
    @Resource
    private IWorkerCertificationService workerCertificationService;

    /**
     * 服务端提交认证申请
     *
     * @param workerCertificationAuditAddReqDTO
     */
    @PostMapping
    @ApiOperation("服务端提交认证申请")
    public void submitAuth(@RequestBody WorkerCertificationAuditAddReqDTO workerCertificationAuditAddReqDTO) {
        workerCertificationService.submitAuth(workerCertificationAuditAddReqDTO);
    }
}
