-- 参数
-- 1. 优惠券id
local voucherId = ARGV[1]
-- 2. 用户Id
local userId = ARGV[2]

-- 数据key
-- 库存key
local stockKey = 'seckill:stock:'.. voucherId
-- 订单key
local orderKey = 'seckill:order:'.. voucherId

-- 业务
-- 1. 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

-- 2. 判断用户是否重复下单
if(redis.call('sismember', orderKey, userId) == 1 ) then
    return 2
end

-- 3. 扣减库存
redis.call('incrby', stockKey, -1)
-- 4. 下单
redis.call('sadd', orderKey, userId)

return 0

