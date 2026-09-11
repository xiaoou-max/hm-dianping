package com.hmdp.service;

import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 笔记评论业务层接口（实现在 BlogCommentsServiceImpl）。
 *
 * 与 BlogCommentsController 一样，这个接口目前在演示代码里没有额外自定义方法，
 * 也没有真正落地评论业务，仅继承 MP 的通用 CRUD 作为占位。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

}
