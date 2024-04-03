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

package com.abincaps.shortlink.admin.controller;

import com.abincaps.shortlink.admin.common.result.Result;
import com.abincaps.shortlink.admin.common.result.Results;
import com.abincaps.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
import com.abincaps.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.abincaps.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.abincaps.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.abincaps.shortlink.admin.service.GroupService;
import com.abincaps.shortlink.common.constant.UserConstant;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 短链接分组控制层
 */
@RestController
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * 新增短链接分组
     */
    @PostMapping("/api/short-link/admin/v1/group")
    public Result<Void> save(@RequestBody ShortLinkGroupSaveReqDTO requestParam, @RequestHeader(UserConstant.USER_ID) String userId) {
        groupService.saveGroup(requestParam, userId);
        return Results.success();
    }

    /**
     * 查询短链接分组集合
     */
    @GetMapping("/api/short-link/admin/v1/group")
    public Result<List<ShortLinkGroupRespDTO>> listGroup(@RequestHeader(UserConstant.USER_ID) String userId) {
        return Results.success(groupService.listGroup(userId));
    }

    /**
     * 修改短链接分组名称
     */
    @PutMapping("/api/short-link/admin/v1/group")
    public Result<Void> updateGroup(@RequestBody ShortLinkGroupUpdateReqDTO requestParam, @RequestHeader(UserConstant.USER_ID) String userId) {
        groupService.updateGroup(requestParam, userId);
        return Results.success();
    }

    /**
     * 删除短链接分组
     */
    @DeleteMapping("/api/short-link/admin/v1/group")
    public Result<Void> deleteGroup(@RequestParam String groupId, @RequestHeader(UserConstant.USER_ID)String userId) {
        groupService.deleteGroup(groupId, userId);
        return Results.success();
    }
}
