package com.jzo2o.customer.model.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("地址响应体")
public class AddressResDto {
    /**
     * 详细地址
     */
    @ApiModelProperty("详细地址")
    private String address;

    /**
     * 市
     */
    @ApiModelProperty("市")
    private String city;

    /**
     * 区
     */
    @ApiModelProperty("区")
    private String county;

    /**
     * 经度
     */
    @ApiModelProperty("经度")
    private Double lon;

    /**
     * 纬度
     */
    @ApiModelProperty("纬度")
    private Double lat;

    /**
     * 更新时间
     */
    @ApiModelProperty("更新时间")
    private String updateTime;

    /**
     * 用户id
     */
    @ApiModelProperty("用户id")
    private Long userId;

    /**
     * 是否为默认地址
     */
    @ApiModelProperty("是否为默认地址")
    private Integer isDefault;

    /**
     * 省份
     */
    @ApiModelProperty("省份")
    private String province;

    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    private String createTime;

    /**
     * 手机号
     */
    @ApiModelProperty("手机号")
    private String phone;

    /**
     * 名称
     */
    @ApiModelProperty("名称")
    private String name;

    /**
     * 地址id
     */
    @ApiModelProperty("地址id")
    private Long id;
}
