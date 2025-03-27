-- 1.parameters
-- 1.1.voucherId
local voucherId = ARGV[1]
-- 1.2.userId
local userId = ARGV[2]
-- 1.3.orderId
local orderId = ARGV[3]

-- 2.keys
-- 2.1.stock key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.order key
local orderKey = 'seckill:order:' .. voucherId

-- 3.process
-- 3.1.determine if stock is enough: get stockKey
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.2.not enough，return 1
    return 1
end
-- 3.2.user has ordered : SISMEMBER orderKey userId
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 3.3.exists -> true -> return 2
    return 2
end
-- 3.4.reduct stock incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.5.save order: sadd orderKey userId
redis.call('sadd', orderKey, userId)
-- 3.6.send tonumber message queue, XADD stream.orders * k1 v1 k2 v2 ...
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0