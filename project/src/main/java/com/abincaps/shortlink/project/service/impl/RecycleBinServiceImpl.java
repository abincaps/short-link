/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.abincaps.shortlink.project.service.impl;

import com.abincaps.shortlink.common.constant.RedisKeyConstant;
import com.abincaps.shortlink.project.dao.entity.ShortLinkDO;
import com.abincaps.shortlink.project.dao.mapper.ShortLinkMapper;
import com.abincaps.shortlink.project.dto.req.RecycleBinReqDTO;
import com.abincaps.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.abincaps.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.abincaps.shortlink.project.service.RecycleBinService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 回收站管理接口实现层

 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements RecycleBinService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void saveRecycleBin(RecycleBinReqDTO recycleBinReqDTO) {

        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getShortUri, recycleBinReqDTO.getShortUri())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0);

        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(1)
                .build();

        baseMapper.update(shortLinkDO, updateWrapper);

        // 删除缓存
        stringRedisTemplate.delete(RedisKeyConstant.GOTO + recycleBinReqDTO.getShortUri());
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {

        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .in(ShortLinkDO::getGid, requestParam.getGidList())
                .eq(ShortLinkDO::getEnableStatus, 1)
                .eq(ShortLinkDO::getDelFlag, 0)
                .orderByDesc(ShortLinkDO::getUpdateTime);

        IPage<ShortLinkDO> resultPage = baseMapper.selectPage(requestParam, queryWrapper);

        // 类型转换
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO shortLinkPageRespDTO = new ShortLinkPageRespDTO();
            BeanUtils.copyProperties(each, shortLinkPageRespDTO);
            return shortLinkPageRespDTO;
        });
    }

    @Override
    public void recoverRecycleBin(RecycleBinReqDTO requestParam) {

        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getShortUri, requestParam.getShortUri())
                .eq(ShortLinkDO::getEnableStatus, 1)
                .eq(ShortLinkDO::getDelFlag, 0);

        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(1)
                .build();

        baseMapper.update(shortLinkDO, updateWrapper);

        // 删除缓存中的失效标记
        stringRedisTemplate.delete(RedisKeyConstant.IS_NULL + requestParam.getShortUri());

    }

    @Override
    public void removeRecycleBin(RecycleBinReqDTO requestParam) {

        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getShortUri, requestParam.getShortUri())
                .eq(ShortLinkDO::getEnableStatus, 1)
                .eq(ShortLinkDO::getDelTime, 0L)
                .eq(ShortLinkDO::getDelFlag, 0);

        ShortLinkDO delShortLinkDO = ShortLinkDO.builder()
                .delTime(System.currentTimeMillis())
                .build();

        delShortLinkDO.setDelFlag(1);

        baseMapper.update(delShortLinkDO, updateWrapper);

        // 删除缓存中的跳转链接
        stringRedisTemplate.delete(RedisKeyConstant.GOTO + requestParam.getShortUri());

        // 删除缓存中的失效标记
        stringRedisTemplate.delete(RedisKeyConstant.IS_NULL + requestParam.getShortUri());
    }
}
