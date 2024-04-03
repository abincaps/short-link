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

package com.abincaps.shortlink.admin.service.impl;

import com.abincaps.shortlink.admin.common.exception.ClientException;
import com.abincaps.shortlink.admin.dao.entity.GroupDO;
import com.abincaps.shortlink.admin.dao.mapper.GroupMapper;
import com.abincaps.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
import com.abincaps.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.abincaps.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.abincaps.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.abincaps.shortlink.admin.service.GroupService;
import com.abincaps.shortlink.common.constant.RedisKeyConstant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 短链接分组接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final RedissonClient redissonClient;

    @Value("${short-link.group.max-num}")
    private Integer groupMaxNum;

    @Override
    public void saveGroup(ShortLinkGroupSaveReqDTO requestParam, String userId) {

        RLock lock = redissonClient.getLock(RedisKeyConstant.GROUP_CREATE_LOCK + userId);
        lock.lock();

        try {
            LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getUserId, userId)
                    .eq(GroupDO::getDelFlag, 0);

            List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);

            if (!CollectionUtils.isEmpty(groupDOList) && groupDOList.size() == groupMaxNum) {
                throw new ClientException(String.format("已超出最大分组数：%d", groupMaxNum));
            }

            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(0)
                    .userId(userId)
                    .name(requestParam.getName())
                    .build();

            baseMapper.insert(groupDO);

        } finally {
            lock.unlock();
        }
    }


    // TODO 改成分页查询
    @Override
    public List<ShortLinkGroupRespDTO> listGroup(String userId) {

        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getDelFlag, 0)
                .eq(GroupDO::getUserId, userId)
                .orderByDesc(GroupDO::getUpdateTime);

        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);

        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = new ArrayList<>();

        groupDOList.forEach(each -> {
            ShortLinkGroupRespDTO shortLinkGroupRespDTO = new ShortLinkGroupRespDTO();
            BeanUtils.copyProperties(each, shortLinkGroupRespDTO);
            shortLinkGroupRespDTOList.add(shortLinkGroupRespDTO);
        });

        return shortLinkGroupRespDTOList;
    }

    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam, String userId) {

        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUserId, userId)
                .eq(GroupDO::getId, requestParam.getGroupId())
                .eq(GroupDO::getDelFlag, 0);

        GroupDO groupDO = new GroupDO();
        groupDO.setName(requestParam.getName());
        baseMapper.update(groupDO, updateWrapper);
    }

    @Override
    public void deleteGroup(String groupId, String userId) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getId, groupId)
                .eq(GroupDO::getUserId, userId)
                .eq(GroupDO::getDelFlag, 0);

        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);

        baseMapper.update(groupDO, updateWrapper);
    }
}
