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

import cn.hutool.core.bean.BeanUtil;
import com.abincaps.shortlink.common.constant.RedisConstant;
import com.abincaps.shortlink.common.constant.TokenConstant;
import com.abincaps.shortlink.common.utils.JwtUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.abincaps.shortlink.admin.common.convention.exception.ClientException;
import com.abincaps.shortlink.admin.common.convention.exception.ServiceException;
import com.abincaps.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.abincaps.shortlink.common.constant.UserConstant;
import com.abincaps.shortlink.admin.dao.entity.UserDO;
import com.abincaps.shortlink.admin.dao.mapper.UserMapper;
import com.abincaps.shortlink.admin.dto.req.UserLoginReqDTO;
import com.abincaps.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.abincaps.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.abincaps.shortlink.admin.dto.resp.UserLoginVO;
import com.abincaps.shortlink.admin.dto.resp.UserRespDTO;
import com.abincaps.shortlink.admin.properties.TokenProperties;
import com.abincaps.shortlink.admin.service.GroupService;
import com.abincaps.shortlink.admin.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.HashMap;

import static com.abincaps.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.abincaps.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;
import static com.abincaps.shortlink.admin.common.enums.UserErrorCodeEnum.*;

/**
 * 用户接口实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final GroupService groupService;
    private final TokenProperties tokenProperties;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    @Override
    public Boolean hasUsername(String username) {
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {

        if (!hasUsername(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }

        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        try {
            if (lock.tryLock()) {
                try {
                    int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
                    if (inserted < 1) {
                        throw new ClientException(USER_SAVE_ERROR);
                    }
                } catch (DuplicateKeyException ex) {
                    throw new ClientException(USER_EXIST);
                }

                userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
                groupService.saveGroup(requestParam.getUsername(), "默认分组");
                return;
            }
            throw new ClientException(USER_NAME_EXIST);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        // TODO 验证当前用户名是否为登录用户
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), updateWrapper);
    }

    @Override
    public UserLoginVO login(UserLoginReqDTO requestParam) {

        // 构造查询条件
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword())
                .eq(UserDO::getDelFlag, 0);

        // 查询 MySQL
        UserDO userDO = baseMapper.selectOne(queryWrapper);

        if (userDO == null) {
            // TODO 修改枚举 Exception
            throw new ClientException("登录失败");
        }

        String refreshTokenKey = getRefreshTokenKey(userDO.getId().toString());

        // 清空存在 Redis 中的 RefreshToken
        stringRedisTemplate.delete(refreshTokenKey);

        HashMap<String, Object> claims = new HashMap<>();
        claims.put(UserConstant.USER_ID, userDO.getId().toString());

        // 生成双 token
        String accessToken = JwtUtils.createToken(claims, tokenProperties.getSecretKey(), tokenProperties.getAccessTokenTtl());
        String refreshToken = JwtUtils.createToken(claims, tokenProperties.getSecretKey(), tokenProperties.getRefreshTokenTtl());
        stringRedisTemplate.opsForValue().set(refreshTokenKey, refreshToken, Duration.ofSeconds(tokenProperties.getRefreshTokenTtl()));

        return UserLoginVO
                .builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        return stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token) != null;
    }

    @Override
    public void logout(String refreshToken) {
        if (ObjectUtils.isEmpty(refreshToken)) {
            return;
        }

        Claims claims = JwtUtils.checkToken(refreshToken, tokenProperties.getSecretKey());

        if (ObjectUtils.isEmpty(claims)) {
            return;
        }

        log.error(claims.get(UserConstant.USER_ID, String.class));

        stringRedisTemplate.delete(getRefreshTokenKey(claims.get(UserConstant.USER_ID, String.class)));
    }

    @Override
    public UserLoginVO refresh(String refreshToken) {
        if (ObjectUtils.isEmpty(refreshToken)) {
            return null;
        }

        Claims claims = JwtUtils.checkToken(refreshToken, tokenProperties.getSecretKey());

        if (ObjectUtils.isEmpty(claims)) {
            return null;
        }

        String userId = claims.get(UserConstant.USER_ID, String.class);

        // 通过 Redis 校验 token 是否使用过，确保只使用一次
        String refreshTokenInRedis = stringRedisTemplate.opsForValue().get(getRefreshTokenKey(userId));


        if (!ObjectUtils.nullSafeEquals(refreshTokenInRedis, refreshToken)) {
            return null;
        }

        Boolean delete = stringRedisTemplate.delete(getRefreshTokenKey(userId));


        // 重新生成 refresh token
        String newAccessToken = JwtUtils.createToken(claims, tokenProperties.getSecretKey(), tokenProperties.getAccessTokenTtl());
        String newRefreshToken = JwtUtils.createToken(claims, tokenProperties.getSecretKey(), tokenProperties.getRefreshTokenTtl());

        // 后放入 Redis 过期时间比 refresh token 过期时间晚
        stringRedisTemplate.opsForValue().set(getRefreshTokenKey(userId), newRefreshToken, Duration.ofSeconds(tokenProperties.getRefreshTokenTtl()));

        return UserLoginVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    /**
     * 获取 RefreshToken 在 Redis 中的 规范 KEY
     *
     * @param userId 长令牌
     * @return RefreshToken 在 Redis 中的 规范 KEY
     */
    private String getRefreshTokenKey(String userId) {
        return RedisConstant.BIZ_ID + ":" + TokenConstant.REFRESH_TOKEN + ":" + userId;
    }
}
