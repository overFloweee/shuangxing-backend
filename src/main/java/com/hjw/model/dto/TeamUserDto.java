package com.hjw.model.dto;


import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;


/**
 * 队伍 和 用户信息封装（安全类）
 */
@Data
public class TeamUserDto implements Serializable
{

    /**
     * id
     */
    private Long id;

    /**
     * 队伍名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 最大人数
     */
    private Integer maxNum;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 0 - 公开，1 - 私有，2 - 加密
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建人信息
     */
    private UserDto createUser;


    /**
     * 已 加入队伍的用户数量
     */
    private Integer hasJoinNum;

    /**
     * 是否已经加入队伍
     */
    private boolean hasJoin = false;

    private static final long serialVersionUID = 5765425566557383501L;

}
