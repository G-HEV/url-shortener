local key      = KEYS[1]
local capacity = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])
local ttl      = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(bucket[1])
local ts     = tonumber(bucket[2])

if tokens == nil or ts == nil then

  tokens = capacity
  ts     = now
else

  local elapsedMs = now - ts

  local refill    = elapsedMs * capacity / windowMs

  tokens          = math.min(capacity, tokens + refill)
  ts              = now
end

local allowed = 0
if tokens >= 1 then
  tokens  = tokens - 1
  allowed = 1
end

local retryAfter = 0
if allowed == 0 then
  retryAfter = math.ceil((1- tokens) * windowMs/ capacity / 1000);
end

redis.call('HSET', key, 'tokens', tokens, 'ts', ts)

redis.call('EXPIRE', key, ttl)

return retryAfter
