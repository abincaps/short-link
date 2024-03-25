package com.abincaps.shortlink.common.constant;

/**
 * @Author abincaps
 * @Date 3/18/2024 1:44 PM
 * @Description
 */
public interface RedisKeyConstant {
    String BIZ_ID = "short_link";

    String GOTO_SHORT_LINK_KEY = BIZ_ID + ":" + "goto:";
}
