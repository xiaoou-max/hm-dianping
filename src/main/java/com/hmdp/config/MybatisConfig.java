package com.hmdp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// MyBatis-Plus配置：注册分页插件
// PaginationInnerInterceptor 让 .page(new Page<>(...)) 自动变成带LIMIT的分页SQL；
// 不注册的话MP分页会失效（变成查全表）
@Configuration
public class MybatisConfig {

    // 创建分页插件并注册到MyBatis-Plus
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 加入分页拦截器，指定数据库类型为MySQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
