-- Compare whether this threadId equals to the threadId in redis
if(redis.call('get', KEYS[1]) ==  ARGV[1]) then
    --  del key
    return redis.call('del', KEYS[1])
end
return 0