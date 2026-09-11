package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页（Feed 流）专用响应对象。
 *
 * 传统分页用“第几页+每页几条”，但 Feed 流（关注动态）用“游标分页”更合适：
 *   - list：本次返回的笔记集合
 *   - minTime：本次结果里最小的时间戳（下次请求把它作为起点，取比它更早的数据）
 *   - offset：同一时间戳下的偏移量（防止同一毫秒有多条数据时漏查/重查）
 * 前端下次请求带上上次返回的 minTime 和 offset，就能一直“向下滚”不会重复。
 */
@Data
public class ScrollResult {
    /** 本次返回的数据列表 */
    private List<?> list;
    /** 本次数据中最小的时间戳（下次分页起点） */
    private Long minTime;
    /** 同时间戳下的偏移量 */
    private Integer offset;
}
