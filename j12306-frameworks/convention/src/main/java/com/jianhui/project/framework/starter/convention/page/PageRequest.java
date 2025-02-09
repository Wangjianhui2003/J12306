package com.jianhui.project.framework.starter.convention.page;

import lombok.Data;

/**
 * 分页请求
 */
@Data
public class PageRequest {

    /**
     * 当前页
     */
    private Long current = 1L;

    /**
     * 每页大小
     */
    private Long size = 10L;
}
