-- 比较线程标识与锁中的标识是否一致
if(redis.call("GET",KEYS[1]) == ARGV[1]) then
    -- 一致则删除锁
    return redis.call("DEL",KEYS[1])
end
return 0
