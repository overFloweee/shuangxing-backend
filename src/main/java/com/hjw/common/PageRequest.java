package com.hjw.common;


import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页请求参数
 */
@Data
public class PageRequest implements Serializable
{


    private static final long serialVersionUID = 7135761401918799855L;
    /**
     * 页面大小
     */
    protected Integer pageSize = 10;

    /**
     * 当前页面
     */
    protected Integer pageNum = 1;

}
