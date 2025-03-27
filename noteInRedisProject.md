

### Implements one ticket for each user

#### Situation 1
1. Use toString().intern instead toString(), since it would return a new string object each time, we cannot put a lock with it.
2. But what if a thread comes before the data is committed to database and after the method is done? It would still have thread safety problem.

```Java
@Override
    public Result seckillVoucher(Long voucherId) {
        //1. query voucher
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2. determent begin time and end time
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("the event hasn't begin yet");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("the event has ended");
        }
        //3. determent whether the number is enough or not
        if (voucher.getStock() < 1) {
            return Result.fail("stock is not enough");
        }
        //6. return id
        return createVoucherOrder(voucherId);
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //4.1 one for each only
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("you've already bought it");
            }

            //4. deduct inventory
            boolean success = seckillVoucherService.update()
                    // set stock = stock -1
                    .setSql("stock = stock - 1")
                    //where id = ? and stock > 0
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("stock is not enough");
            }
            //5. create order
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return  Result.ok(orderId);
        }
    }
```

#### Situation 1 improved
1. Sovled the problem of Situation 1.2 about thread safety which a new request is sent to backend before data is committed to db and after the is lock is released.
2. ##### Notice: 
   1. if we this.createVoucherOrder(), the Transactional annotation would not work since implement class is not managed by Springboot, so we need to get the proxy of transaction
3. ##### However, 
   1. synchronize() method can only work at monolithic project. It would fail when if there are 2 JVM(more than one) run this code.
   2. So we need to use redis to implement distributed lock(Situation 2).
```Java
public Result seckillVoucher(Long voucherId) {
        //1. query voucher
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //6. return id
        synchronized (userId.toString().intern()) {
            //get proxy about transaction
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId,userId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId){
        //4.1 one for each only
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        
        the rest code is same with situation 1
    }
```

#### Situation 2: user Redis to implements distributed lock
```Java
public Result seckillVoucher(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        boolean isLock = lock.tryLock(5);
        if (!isLock) {
            //failed to get lock
            //return failed
            return Result.fail("repeated order is not allowed!");
        }
        //get proxy about transaction
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId){
        //4.1 one for each only
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("you've already bought it");
            }

            //4. deduct inventory
            boolean success = seckillVoucherService.update()
                    // set stock = stock -1
                    .setSql("stock = stock - 1")
                    //where id = ? and stock > 0
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("stock is not enough");
            }
            //5. create order
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return  Result.ok(orderId);
    }
```

##### SimpleRedisLock class ↓
```Java
public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;

    // name of lock
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) +  "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(id)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
```

#### Though 
- now the problem of distribution problem has been solved, there is still a problem
- What if JVM triggers garbage collection mechanism causing thread blocking after the thread has determined the lock is belongs to itself and before deleting the lock?
- Then thread 2 would get lock  since the lock of thread 1 was expired. After JVM of thread 1 has done it job, it still thinks the lock belongs to itself and delete it.
- Then thread 3 would get the lock.
#### So
- we need to use script to ensure atomicity.

#### Situation 2 Improved
- use lua script to ensure atomicity
```Java
public void unlock() {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
---
lua script
if(redis.call('get', KEYS[1]) ==  ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0
```