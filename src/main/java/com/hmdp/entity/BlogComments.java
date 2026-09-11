package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 笔记评论表（tb_blog_comments）。支持多级评论：parentId 指向所属 1 级评论(1 级评论则=0)，
 * answerId 指向回复的那条评论。status 审核状态(0正常/1被举报/2禁止查看)。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long blogId;
    private Long parentId;
    private Long answerId;
    private String content;
    private Integer liked;
    private Boolean status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
