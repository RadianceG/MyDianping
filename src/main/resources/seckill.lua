--优惠券ID
local voucherId=ARGV[1]
--用户ID
local userId=ARGV[2]
--2.数据key
--2.1库存key
local stockKey='seckill:stock:' .. voucherId
--2.2订单key
local orderKey='seckill:order' .. voucherId

if(tonumber(redis.call('get',stockKey))<=0) then
    --库存不足
    return 1
end

if(redis.call('sismember',orderKey,userId)==1)then
    return 2
end

redis.call('incrby',stockKey,-1)
redis.call('sadd',orderKey,userId)
return 0