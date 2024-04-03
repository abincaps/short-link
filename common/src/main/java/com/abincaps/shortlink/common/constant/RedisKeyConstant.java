package com.abincaps.shortlink.common.constant;

/**
 * @Author abincaps
 * @Date 3/18/2024 1:44 PM
 * @Description
 */
public interface RedisKeyConstant {

    /**
     * 业务标识
     */
    String BIZ = "short_link:";

    /**
     * 跳转链接
     */
    String GOTO = BIZ + "goto:";


    /**
     * 链接是否失效
     */
    String IS_NULL = BIZ + "is_null:";

    String GROUP_CREATE_LOCK = BIZ + "group_create_lock:";
}
