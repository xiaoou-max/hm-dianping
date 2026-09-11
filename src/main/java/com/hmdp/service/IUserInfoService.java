package com.hmdp.service;

import com.hmdp.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户资料业务层接口（实现在 UserInfoServiceImpl）。
 *
 * 没有额外自定义方法，仅继承 MP 的 IService<UserInfo> 通用 CRUD
 * （如 Controller 里 /user/info/{id} 查资料就走这里）。
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface IUserInfoService extends IService<UserInfo> {

}
