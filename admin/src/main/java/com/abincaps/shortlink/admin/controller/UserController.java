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

import com.abincaps.shortlink.common.constant.TokenConstant;
import com.abincaps.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.abincaps.shortlink.admin.common.convention.result.Result;
import com.abincaps.shortlink.admin.common.convention.result.Results;
import com.abincaps.shortlink.admin.dto.req.UserLoginReqDTO;
import com.abincaps.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.abincaps.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.abincaps.shortlink.admin.dto.resp.UserActualRespDTO;
import com.abincaps.shortlink.admin.dto.resp.UserLoginVO;
import com.abincaps.shortlink.admin.dto.resp.UserRespDTO;
import com.abincaps.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制层
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // TODO BUG 可以查询任意用户信息
    /**
     * 根据用户名查询用户信息
     */
//    @GetMapping("/api/short-link/admin/v1/user/{username}")
//    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username) {
//        return Results.success(userService.getUserByUsername(username));
//    }

    /**
     * 根据用户名查询无脱敏用户信息
     */
//    @GetMapping("/api/short-link/admin/v1/actual/user/{username}")
//    public Result<UserActualRespDTO> getActualUserByUsername(@PathVariable("username") String username) {
//        UserRespDTO useRespDTO = userService.getUserByUsername(username);
//        UserActualRespDTO userActualRespDTO = new UserActualRespDTO();
//        BeanUtils.copyProperties(useRespDTO, userActualRespDTO);
//        return Results.success(userActualRespDTO);
//    }

    /**
     * 查询用户名是否存在
     */
    @GetMapping("/api/short-link/admin/v1/user/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") String username) {
        return Results.success(userService.hasUsername(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("/api/short-link/admin/v1/user/register")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam) {
        userService.register(requestParam);
        return Results.success();
    }

    /**
     * 修改用户
     */
    @PutMapping("/api/short-link/admin/v1/user")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam) {
        userService.update(requestParam);
        return Results.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/api/short-link/admin/v1/user/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginReqDTO requestParam) {
        return Results.success(userService.login(requestParam));
    }

    /**
     * 刷新 token
     * @param refreshToken
     * @return
     */
    @PostMapping("/api/short-link/admin/v1/user/refresh")
    public Result<UserLoginVO> refresh(@RequestHeader(TokenConstant.REFRESH_TOKEN) String refreshToken) {
        UserLoginVO loginVO = userService.refresh(refreshToken);

        if (ObjectUtils.isEmpty(loginVO)) {
            return Results.error(BaseErrorCode.REFRESH_TOKEN_ERROR);
        }

        return Results.success(loginVO);
    }

    /**
     * 检查用户是否登录
     */
    @GetMapping("/api/short-link/admin/v1/user/check-login")
    public Result<Boolean> checkLogin(@RequestParam("username") String username, @RequestParam("token") String token) {
        return Results.success(userService.checkLogin(username, token));
    }

    /**
     * 用户退出登录
     */
    @DeleteMapping("/api/short-link/admin/v1/user/logout")
    public Result<Void> logout(@RequestHeader(TokenConstant.REFRESH_TOKEN) String refreshToken) {
        userService.logout(refreshToken);
        return Results.success();
    }
}
