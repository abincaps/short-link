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

package com.abincaps.shortlink.admin.service;

import com.abincaps.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.abincaps.shortlink.admin.dao.entity.GroupDO;
import com.abincaps.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.abincaps.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.abincaps.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

/**
 * 短链接分组接口层

 */
public interface GroupService extends IService<GroupDO> {

    /**
     * 新增短链接分组
     * @param requestParam
     * @param userId
     */
    void saveGroup(ShortLinkGroupSaveReqDTO requestParam, String userId);

    /**
     * 查询用户短链接分组集合
     *
     * @return 用户短链接分组集合
     */
    List<ShortLinkGroupRespDTO> listGroup(String userId);

    /**
     * 修改短链接分组
     *
     * @param requestParam 修改链接分组参数
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO requestParam, String userId);

    /**
     * 删除短链接分组
     *
     * @param groupId
     * @param userId
     */
    void deleteGroup(String groupId, String userId);
}
