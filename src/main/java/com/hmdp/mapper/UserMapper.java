package com.hmdp.mapper;

import com.hmdp.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户表的数据访问层（DAO）接口，对应 tb_user。
 *
 * 在 MyBatis-Plus 里，Mapper 就是“直接操作数据库的那一层”。关键好处：
 *   - 只要写 `extends BaseMapper<User>`，MP 就在运行时自动生成所有 CRUD 实现，
 *     不用写 XML、不用写实现类。
 *   - 之后在 Service 里就可以用 this.baseMapper 或直接注入来调 query()/insert()/update() 等。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface UserMapper extends BaseMapper<User> {

}
