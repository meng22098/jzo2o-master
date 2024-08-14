package com.jzo2o.foundations.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.foundations.dto.response.ConfigRegionInnerResDTO;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.foundations.constants.RedisConstants;
import com.jzo2o.foundations.enums.FoundationStatusEnum;
import com.jzo2o.foundations.mapper.*;
import com.jzo2o.foundations.model.domain.*;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.ServeAggregationSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeAggregationTypeSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.service.IServeService;
import com.jzo2o.mysql.utils.PageHelperUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-07-03
 */
@Service
public class ServeServiceImpl extends ServiceImpl<ServeMapper, Serve> implements IServeService {

    @Resource
    private ServeItemMapper serveItemMapper;

    @Resource
    private RegionMapper regionMapper;

    @Resource
    ServeTypeMapper serveTypeMapper;

    @Resource
    ServeSyncMapper serveSyncMapper;

    /**
     * 分页查询
     *
     * @param servePageQueryReqDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<ServeResDTO> page(ServePageQueryReqDTO servePageQueryReqDTO) {
        //调用mapper查询数据，这里由于继承了ServiceImpl<ServeMapper, Serve>，使用baseMapper相当于使用ServeMapper
        PageResult<ServeResDTO> serveResDTOPageResult = PageHelperUtils.selectPage(servePageQueryReqDTO, () -> baseMapper.queryServeListByRegionId(servePageQueryReqDTO.getRegionId()));
        return serveResDTOPageResult;
    }

    /**
     * 批量添加
     * @param serveUpsertReqDTOList 批量新增数据
     */
    @Override
    @Transactional
    public void batchAdd(List<ServeUpsertReqDTO> serveUpsertReqDTOList) {
        for (ServeUpsertReqDTO serveUpsertReqDTO : serveUpsertReqDTOList) {
            //1.校验服务项是否为启用状态，不是启用状态不能新增
            ServeItem serveItem = serveItemMapper.selectById(serveUpsertReqDTO.getServeItemId());
            if(!(serveItem.getActiveStatus() == FoundationStatusEnum.ENABLE.getStatus())){
                throw new ForbiddenOperationException("该服务未启用无法添加到区域下使用");
            }

            //2.校验是否重复新增
            LambdaQueryWrapper<Serve> queryWrapper = Wrappers.<Serve>lambdaQuery()
                    .eq(Serve::getRegionId, serveUpsertReqDTO.getRegionId())
                    .eq(Serve::getServeItemId, serveUpsertReqDTO.getServeItemId());
            Integer count = baseMapper.selectCount(queryWrapper);
            if(count>0){
                throw new ForbiddenOperationException(serveItem.getName()+"服务已存在");
            }

            //3.新增服务
            Serve serve = BeanUtil.toBean(serveUpsertReqDTO, Serve.class);
            Region region = regionMapper.selectById(serveUpsertReqDTO.getRegionId());
            serve.setCityCode(region.getCityCode());
            baseMapper.insert(serve);
        }
    }

    /**
     * 修改服务价格
     * @param id    服务id
     * @param price 价格
     * @return
     */
    @Override
    @Transactional
    @CacheEvict(value = RedisConstants.CacheName.SERVE, key = "#id")
    public Serve update(Long id, BigDecimal price) {
        //1.更新服务价格
        boolean update = lambdaUpdate()
                .eq(Serve::getId, id)
                .set(Serve::getPrice, price)
                .update();
        if(!update){
            throw new CommonException("修改服务价格失败");
        }
        return baseMapper.selectById(id);
    }

    /**
     * 删除服务
     * @param id 服务id
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        Serve serve = baseMapper.selectById(id);
        if(ObjectUtil.isNull(serve)){
            throw new ForbiddenOperationException("区域服务不存在");
        }
        //草稿状态方可删除
        if (!(serve.getSaleStatus()==FoundationStatusEnum.INIT.getStatus())) {
            throw new ForbiddenOperationException("草稿状态方可删除");
        }

        //删除服务
        baseMapper.deleteById(id);
    }

    /**
     * 服务上架
     * @param id         服务id
     * @return
     */
    @Override
    @Transactional
    @CachePut(value = RedisConstants.CacheName.SERVE, key = "#id",unless = "#result.saleStatus != 2",  cacheManager = RedisConstants.CacheManager.ONE_DAY)
    public Serve onSale(Long id) {
        //1.校验服务是否存在
        Serve serve = baseMapper.selectById(id);
        if(ObjectUtil.isNull(serve)){
            throw new ForbiddenOperationException("区域服务不存在");
        }
        System.out.println(serve.getSaleStatus()!= FoundationStatusEnum.INIT.getStatus());
        System.out.println( serve.getSaleStatus()!= FoundationStatusEnum.DISABLE.getStatus());
        //2.校验服务状态是否为草稿或下架状态
        if(serve.getSaleStatus()!= FoundationStatusEnum.INIT.getStatus() && serve.getSaleStatus()!= FoundationStatusEnum.DISABLE.getStatus()){
            throw new ForbiddenOperationException("服务状态不为草稿或下架状态,无法上架");
        }
        //3.校验服务项是否为启用状态
        ServeItem serveItem = serveItemMapper.selectById(serve.getServeItemId());
        if(serveItem.getActiveStatus()!= FoundationStatusEnum.ENABLE.getStatus()){
            throw new ForbiddenOperationException("本服务所属的服务项未启用,无法上架");
        }
        //4.更新服务状态为上架
        boolean update = lambdaUpdate()
                .eq(Serve::getId, id)
                .set(Serve::getSaleStatus, FoundationStatusEnum.ENABLE.getStatus())
                .update();
        if(!update){
            throw new CommonException("服务上架失败");
        }
        addServeSync(id);
        return baseMapper.selectById(id);
    }

    /**
     * 服务下架
     *
     * @param id 服务id
     * @return
     */
    @Override
    @Transactional
    @CacheEvict(value = RedisConstants.CacheName.SERVE, key = "#id")
    public Serve offSale(Long id) {
        //1.校验服务是否存在
        Serve serve = baseMapper.selectById(id);
        if(ObjectUtil.isNull(serve)){
            throw new ForbiddenOperationException("区域服务不存在");
        }
        //2.校验服务状态是否为上架状态
        if(serve.getSaleStatus()!= FoundationStatusEnum.ENABLE.getStatus()){
            throw new ForbiddenOperationException("服务状态不为上架状态,无法下架");
        }
        //3.更新服务状态为下架
        boolean update = lambdaUpdate()
                .eq(Serve::getId, id)
                .set(Serve::getSaleStatus, FoundationStatusEnum.DISABLE.getStatus())
                .update();
        if(!update){
            throw new CommonException("服务下架失败");
        }
        serveSyncMapper.deleteById(id);
        return serve;
    }

    /**
     * 服务状态为热门
     * @param id
     * @return
     */
    @Override
    @Transactional
    public Serve onHot(Long id) {
        //1.校验服务是否存在
        Serve serve = baseMapper.selectById(id);
        if(ObjectUtil.isNull(serve)){
            throw new ForbiddenOperationException("区域服务不存在");
        }
        //2.校验服务状态是否为上架状态
        if(serve.getSaleStatus()!= FoundationStatusEnum.ENABLE.getStatus()){
            throw new ForbiddenOperationException("服务状态不为上架状态,无法设置热门");
        }
        //3.更新服务状态为热门
        boolean update = lambdaUpdate()
                .eq(Serve::getId, id)
                .set(Serve::getIsHot, FoundationStatusEnum.ONHOT.getStatus())
                .update();
        if(!update){
            throw new CommonException("服务设置热门失败");
        }
        return baseMapper.selectById(id);
    }

    /**
     * 状态为非热门
     * @param id
     * @return
     */
    @Override
    @Transactional
    public Serve offHot(Long id) {
        //1.校验服务是否存在
        Serve serve = baseMapper.selectById(id);
        if(ObjectUtil.isNull(serve)){
            throw new ForbiddenOperationException("区域服务不存在");
        }
        //2.校验服务状态是否为上架状态
        if(serve.getSaleStatus()!= FoundationStatusEnum.ENABLE.getStatus()){
            throw new ForbiddenOperationException("服务状态不为上架状态,无法取消热门");
        }
        //3.更新服务状态为非热门
        boolean update = lambdaUpdate()
                .eq(Serve::getId, id)
                .set(Serve::getIsHot, FoundationStatusEnum.OFFHOT.getStatus())
                .update();
        if(!update){
            throw new CommonException("服务取消热门失败");
        }
        return baseMapper.selectById(id);
    }

    @Override
    public ServeAggregationResDTO findServeDetailById(Long id) {
      return baseMapper.findServeDetailById(id);
    }

    /**
     * 根据区域id和售卖状态查询关联服务数量
     *
     * @param regionId   区域id
     * @param saleStatus 售卖状态，0：草稿，1下架，2上架。可传null，即查询所有状态
     * @return 服务数量
     */
    @Override
    public int queryServeCountByRegionIdAndSaleStatus(Long regionId, Integer saleStatus) {
        LambdaQueryWrapper<Serve> queryWrapper = Wrappers.<Serve>lambdaQuery()
                .eq(Serve::getRegionId, regionId)
                .eq(ObjectUtil.isNotEmpty(saleStatus), Serve::getSaleStatus, saleStatus);
        return baseMapper.selectCount(queryWrapper);
    }
    /**
     * 根据服务项id和售卖状态查询关联服务数量
     *
     * @param  serveItemId  服务项id
     * @param saleStatus 售卖状态，0：草稿，1下架，2上架。可传null，即查询所有状态
     * @return 服务数量
     */
    @Override
    public int queryServeCountByServeItemIdAndSaleStatus(Long serveItemId, Integer saleStatus) {
        LambdaQueryWrapper<Serve> queryWrapper = Wrappers.<Serve>lambdaQuery()
                .eq(Serve::getServeItemId, serveItemId)
                .eq(ObjectUtil.isNotEmpty(saleStatus), Serve::getSaleStatus, saleStatus);
        return baseMapper.selectCount(queryWrapper);
    }

    @Override
    public List<Serve> queryHotAndOnSaleServeList() {
        //todo
        return null;
    }

    @Override
    public List<ServeAggregationSimpleResDTO> findHotServeListByRegionId(Long regionId) {
        return baseMapper.findHotServeListByRegionId(regionId);
    }

    @Override
    public List<ServeAggregationTypeSimpleResDTO> findServeTypeListByRegionId(Long regionId) {
        return baseMapper.findServeTypeListByRegionId(regionId);
    }

    private void addServeSync(Long serveId) {
        //服务信息
        Serve serve = baseMapper.selectById(serveId);
        //区域信息
        Region region = regionMapper.selectById(serve.getRegionId());
        //服务项信息
        ServeItem serveItem = serveItemMapper.selectById(serve.getServeItemId());
        //服务类型
        ServeType serveType = serveTypeMapper.selectById(serveItem.getServeTypeId());

        ServeSync serveSync = new ServeSync();
        serveSync.setServeTypeId(serveType.getId());
        serveSync.setServeTypeName(serveType.getName());
        serveSync.setServeTypeIcon(serveType.getServeTypeIcon());
        serveSync.setServeTypeImg(serveType.getImg());
        serveSync.setServeTypeSortNum(serveType.getSortNum());

        serveSync.setServeItemId(serveItem.getId());
        serveSync.setServeItemIcon(serveItem.getServeItemIcon());
        serveSync.setServeItemName(serveItem.getName());
        serveSync.setServeItemImg(serveItem.getImg());
        serveSync.setServeItemSortNum(serveItem.getSortNum());
        serveSync.setUnit(serveItem.getUnit());
        serveSync.setDetailImg(serveItem.getDetailImg());
        serveSync.setPrice(serve.getPrice());

        serveSync.setCityCode(region.getCityCode());
        serveSync.setId(serve.getId());
        serveSync.setIsHot(serve.getIsHot());
        serveSyncMapper.insert(serveSync);
    }
}
