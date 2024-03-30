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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.abincaps.shortlink.common.constant.RedisKeyConstant;
import com.abincaps.shortlink.project.common.constant.RedisConstant;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.abincaps.shortlink.project.common.convention.exception.ClientException;
import com.abincaps.shortlink.project.common.convention.exception.ServiceException;
import com.abincaps.shortlink.project.dao.entity.ShortLinkDO;
import com.abincaps.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.abincaps.shortlink.project.dao.mapper.LinkAccessLogsMapper;
import com.abincaps.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.abincaps.shortlink.project.dao.mapper.LinkBrowserStatsMapper;
import com.abincaps.shortlink.project.dao.mapper.LinkDeviceStatsMapper;
import com.abincaps.shortlink.project.dao.mapper.LinkLocaleStatsMapper;
import com.abincaps.shortlink.project.dao.mapper.LinkNetworkStatsMapper;
import com.abincaps.shortlink.project.dao.mapper.LinkOsStatsMapper;
import com.abincaps.shortlink.project.dao.mapper.LinkStatsTodayMapper;
import com.abincaps.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.abincaps.shortlink.project.dao.mapper.ShortLinkMapper;
import com.abincaps.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.abincaps.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.abincaps.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.abincaps.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.abincaps.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.abincaps.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.abincaps.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.abincaps.shortlink.project.service.LinkStatsTodayService;
import com.abincaps.shortlink.project.service.ShortLinkService;
import com.abincaps.shortlink.project.toolkit.HashUtil;
import com.abincaps.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 短链接接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final LinkStatsTodayService linkStatsTodayService;

    @Value("${short-link.domain}")
    private String defaultDomain;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {

        String shortUri = generateShortLink(requestParam);

        String fullShortUrl = defaultDomain + "/" + shortUri;

        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(defaultDomain)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .describe(requestParam.getDescribe())
                .shortUri(shortUri)
                .enableStatus(0)
                .totalPv(0)
                .totalUv(0)
                .totalUip(0)
                .delTime(0L)
                .build();

        ShortLinkGotoDO linkGotoDO = ShortLinkGotoDO.builder()
                .fullShortUrl(fullShortUrl)
                .gid(requestParam.getGid())
                .build();

        try {

            baseMapper.insert(shortLinkDO);

        } catch (DuplicateKeyException ex) {
            throw new ServiceException(String.format("短链接：%s 生成重复", fullShortUrl));
        }

        // 设置缓存
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.GOTO + shortUri,
                requestParam.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(requestParam.getValidDate()), TimeUnit.MILLISECONDS
        );

        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + fullShortUrl)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO shortLinkUpdateReqDTO, String userId) {

        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getUserId, userId)
                .eq(ShortLinkDO::getShortUri, shortLinkUpdateReqDTO.getShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);

        ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);

        if (shortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }

        // 更新条件
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getUserId, userId)
                .eq(ShortLinkDO::getShortUri, shortLinkUpdateReqDTO.getShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);

        shortLinkDO.setOriginUrl(shortLinkUpdateReqDTO.getOriginUrl());
        shortLinkDO.setGid(shortLinkUpdateReqDTO.getGid());
        shortLinkDO.setDescribe(shortLinkUpdateReqDTO.getDescribe());
        shortLinkDO.setValidDateType(shortLinkUpdateReqDTO.getValidDateType());
        shortLinkDO.setValidDate(shortLinkUpdateReqDTO.getValidDate());

        baseMapper.update(shortLinkDO, updateWrapper);

        // TODO 修改 Redis 中的有效期
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        IPage<ShortLinkDO> resultPage = baseMapper.pageLink(requestParam);
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://" + result.getDomain());
            return result;
        });
    }

    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        QueryWrapper<ShortLinkDO> queryWrapper = Wrappers.query(new ShortLinkDO())
                .select("gid as gid, count(*) as shortLinkCount")
                .in("gid", requestParam)
                .eq("enable_status", 0)
                .eq("del_flag", 0)
                .eq("del_time", 0L)
                .groupBy("gid");
        List<Map<String, Object>> shortLinkDOList = baseMapper.selectMaps(queryWrapper);
        return BeanUtil.copyToList(shortLinkDOList, ShortLinkGroupCountQueryRespDTO.class);
    }

    @SneakyThrows
    @Override
    public void redirectUrl(String shortUri, HttpServletRequest request, HttpServletResponse response) {

        // 检查缓存中是否标记 shortUri 失效
        Boolean result = stringRedisTemplate.hasKey(RedisKeyConstant.IS_NULL + shortUri);

        if (ObjectUtils.nullSafeEquals(result, Boolean.TRUE)) {
            response.sendRedirect("/notfound");
            return;
        }

        // 检查缓存中是否有 shortUri 的跳转链接
        String originalUrl = stringRedisTemplate.opsForValue().get(RedisKeyConstant.GOTO + shortUri);

        if (!StringUtils.hasLength(originalUrl)) {
            response.sendRedirect("/notfound");
            return;
        }

        // TODO 暂时下线统计功能
//        if (StrUtil.isNotBlank(originalUrl)) {
//            ShortLinkStatsRecordDTO statsRecord = buildLinkStatsRecordAndSetUser(fullShortUrl, request, response);
//            shortLinkStats(fullShortUrl, null, statsRecord);
//            ((HttpServletResponse) response).sendRedirect(originalUrl);
//            return;
//        }

        response.sendRedirect(originalUrl);
    }

    private ShortLinkStatsRecordDTO buildLinkStatsRecordAndSetUser(String fullShortUrl, ServletRequest request, ServletResponse response) {
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        AtomicReference<String> uv = new AtomicReference<>();
        Runnable addResponseCookieTask = () -> {
            uv.set(UUID.fastUUID().toString());
            Cookie uvCookie = new Cookie("uv", uv.get());
            uvCookie.setMaxAge(60 * 60 * 24 * 30);
            uvCookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
            ((HttpServletResponse) response).addCookie(uvCookie);
            uvFirstFlag.set(Boolean.TRUE);
            stringRedisTemplate.opsForSet().add(RedisConstant.SHORT_LINK_STATS_UV_KEY + fullShortUrl, uv.get());
        };
        if (ArrayUtil.isNotEmpty(cookies)) {
            Arrays.stream(cookies)
                    .filter(each -> Objects.equals(each.getName(), "uv"))
                    .findFirst()
                    .map(Cookie::getValue)
                    .ifPresentOrElse(each -> {
                        uv.set(each);
                        Long uvAdded = stringRedisTemplate.opsForSet().add(RedisConstant.SHORT_LINK_STATS_UV_KEY + fullShortUrl, each);
                        uvFirstFlag.set(uvAdded != null && uvAdded > 0L);
                    }, addResponseCookieTask);
        } else {
            addResponseCookieTask.run();
        }
        String remoteAddr = LinkUtil.getActualIp(((HttpServletRequest) request));
        String os = LinkUtil.getOs(((HttpServletRequest) request));
        String browser = LinkUtil.getBrowser(((HttpServletRequest) request));
        String device = LinkUtil.getDevice(((HttpServletRequest) request));
        String network = LinkUtil.getNetwork(((HttpServletRequest) request));
        Long uipAdded = stringRedisTemplate.opsForSet().add(RedisConstant.SHORT_LINK_STATS_UIP_KEY + fullShortUrl, remoteAddr);
        boolean uipFirstFlag = uipAdded != null && uipAdded > 0L;
        return ShortLinkStatsRecordDTO.builder()
                .fullShortUrl(fullShortUrl)
                .uv(uv.get())
                .uvFirstFlag(uvFirstFlag.get())
                .uipFirstFlag(uipFirstFlag)
                .remoteAddr(remoteAddr)
                .os(os)
                .browser(browser)
                .device(device)
                .network(network)
                .build();
    }

    @Override
    public void shortLinkStats(String fullShortUrl, String gid, ShortLinkStatsRecordDTO statsRecord) {
        Map<String, String> producerMap = new HashMap<>();
        producerMap.put("fullShortUrl", fullShortUrl);
        producerMap.put("gid", gid);
        producerMap.put("statsRecord", JSON.toJSONString(statsRecord));
    }

    /**
     * 生成短链接
     * @param requestParam
     * @return
     */

    private String generateShortLink(ShortLinkCreateReqDTO requestParam) {

        for (int i = 0; i < 3; i++) {
            String shorUri = HashUtil.hashToBase62(requestParam.getOriginUrl() + java.util.UUID.randomUUID());

            // 判断是否唯一
            if (!shortUriCreateCachePenetrationBloomFilter.contains(defaultDomain + "/" + shorUri)) {
                return shorUri;
            }

        }

        throw new ServiceException("生成短链接频繁");
    }
}
