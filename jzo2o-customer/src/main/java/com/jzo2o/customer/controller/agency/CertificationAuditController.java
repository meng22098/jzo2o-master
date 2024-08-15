package com.jzo2o.customer.controller.agency;
import com.jzo2o.customer.model.dto.request.AgencyCertificationAuditAddReqDTO;
import com.jzo2o.customer.service.IAgencyCertificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController("agencyCertificationAuditController")
@RequestMapping("/agency/agency-certification-audit")
@Api(tags = "机构端 - 认证审核相关接口")
public class CertificationAuditController {
    @Resource
    private IAgencyCertificationService agencyCertificationService;

    /**
     * 机构端提交认证申请
     *
     * @param agencyCertificationAuditAddReqDTO
     */
    @PostMapping
    @ApiOperation("机构端提交认证申请")
    public void submitAuth(@RequestBody AgencyCertificationAuditAddReqDTO agencyCertificationAuditAddReqDTO) {
        agencyCertificationService.submitAuth(agencyCertificationAuditAddReqDTO);
    }
}
