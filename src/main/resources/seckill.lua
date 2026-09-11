-- ============================================================================
-- 秒杀下单 Lua 脚本：在 Redis 服务端原子地完成"校验 + 扣库存 + 发消息"。
-- 为什么用 Lua？"判断库存 → 判断重复 → 扣库存"是多步操作，Lua 在 Redis 单线程里
-- 一次性执行，保证不被并发请求拆开（杜绝超卖/重复下单）。
-- 参数：ARGV[1]=券id, ARGV[2]=用户id, ARGV[3]=订单id
-- 返回值：0=成功抢到；1=库存不足；2=重复下单
-- ============================================================================

local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 库存不足直接拒绝
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
-- SISMEMBER 判断该用户是否已下过单（Set 记录已购用户）
if(redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
-- 原子扣库存 + 记录已购用户
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
-- XADD 发消息到 Stream，交给异步线程真正建单
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0
