package com.hmdp.service;

import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 商铺分类业务层接口（实现在 ShopTypeServiceImpl）。
 *
 * 本接口没有额外自定义方法，只继承 MP 的 IService<ShopType>，
 * 因此实现类直接拥有 list() 等通用方法（前端 /shop-type/list 就用这个）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

}
