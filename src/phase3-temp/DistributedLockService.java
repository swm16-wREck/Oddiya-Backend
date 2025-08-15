package com.oddiya.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Distributed Lock Service using Redisson
 * Agent 5 - Cache Patterns Developer
 * 
 * Provides distributed locking capabilities for cache operations and
 * critical sections that need coordination across multiple application instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.cache.distributed-locks.enabled", havingValue = "true", matchIfMissing = true)
public class DistributedLockService {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "oddiya:lock:";
    private static final String SEMAPHORE_PREFIX = "oddiya:semaphore:";
    private static final String COUNTDOWN_LATCH_PREFIX = "oddiya:latch:";

    /**
     * Execute operation with distributed lock using default timeout
     */
    public <T> T withLock(String lockName, Callable<T> operation) {
        return withLock(lockName, operation, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

    /**
     * Execute operation with distributed lock using custom timeout
     */
    public <T> T withLock(String lockName, Callable<T> operation, Duration leaseTime, Duration waitTime) {
        String fullLockName = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(fullLockName);
        
        try {
            log.debug("Attempting to acquire lock: {}", fullLockName);
            
            if (lock.tryLock(waitTime.toSeconds(), leaseTime.toSeconds(), TimeUnit.SECONDS)) {
                try {
                    log.debug("Lock acquired successfully: {}", fullLockName);
                    T result = operation.call();
                    log.debug("Operation completed successfully with lock: {}", fullLockName);
                    return result;
                    
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Lock released: {}", fullLockName);
                    }
                }
            } else {
                log.warn("Failed to acquire lock within timeout: {} (waitTime={})", fullLockName, waitTime);
                throw new DistributedLockException("Could not acquire lock: " + lockName + " within " + waitTime);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while waiting for lock: {}", fullLockName, e);
            throw new DistributedLockException("Thread interrupted while waiting for lock: " + lockName, e);
            
        } catch (Exception e) {
            log.error("Error executing operation with lock: {}", fullLockName, e);
            if (e instanceof DistributedLockException) {
                throw e;
            }
            throw new DistributedLockException("Error executing operation with lock: " + lockName, e);
        }
    }

    /**
     * Try to execute operation with lock, return empty result if lock not available
     */
    public <T> java.util.Optional<T> tryWithLock(String lockName, Callable<T> operation) {
        return tryWithLock(lockName, operation, Duration.ofSeconds(30));
    }

    /**
     * Try to execute operation with lock and custom lease time
     */
    public <T> java.util.Optional<T> tryWithLock(String lockName, Callable<T> operation, Duration leaseTime) {
        String fullLockName = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(fullLockName);
        
        try {
            if (lock.tryLock(0, leaseTime.toSeconds(), TimeUnit.SECONDS)) {
                try {
                    log.debug("Lock acquired immediately: {}", fullLockName);
                    T result = operation.call();
                    return java.util.Optional.of(result);
                    
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Lock released: {}", fullLockName);
                    }
                }
            } else {
                log.debug("Lock not available immediately: {}", fullLockName);
                return java.util.Optional.empty();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while trying lock: {}", fullLockName, e);
            return java.util.Optional.empty();
            
        } catch (Exception e) {
            log.error("Error trying operation with lock: {}", fullLockName, e);
            return java.util.Optional.empty();
        }
    }

    /**
     * Create a fair lock that ensures FIFO ordering
     */
    public <T> T withFairLock(String lockName, Callable<T> operation, Duration leaseTime, Duration waitTime) {
        String fullLockName = LOCK_PREFIX + "fair:" + lockName;
        RLock lock = redissonClient.getFairLock(fullLockName);
        
        try {
            if (lock.tryLock(waitTime.toSeconds(), leaseTime.toSeconds(), TimeUnit.SECONDS)) {
                try {
                    log.debug("Fair lock acquired: {}", fullLockName);
                    return operation.call();
                    
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Fair lock released: {}", fullLockName);
                    }
                }
            } else {
                throw new DistributedLockException("Could not acquire fair lock: " + lockName + " within " + waitTime);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("Thread interrupted while waiting for fair lock: " + lockName, e);
            
        } catch (Exception e) {
            if (e instanceof DistributedLockException) {
                throw e;
            }
            throw new DistributedLockException("Error executing operation with fair lock: " + lockName, e);
        }
    }

    /**
     * Create a reentrant read-write lock
     */
    public <T> T withReadLock(String lockName, Callable<T> operation, Duration leaseTime, Duration waitTime) {
        String fullLockName = LOCK_PREFIX + "rw:" + lockName;
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(fullLockName);
        RLock readLock = rwLock.readLock();
        
        try {
            if (readLock.tryLock(waitTime.toSeconds(), leaseTime.toSeconds(), TimeUnit.SECONDS)) {
                try {
                    log.debug("Read lock acquired: {}", fullLockName);
                    return operation.call();
                    
                } finally {
                    if (readLock.isLocked() && readLock.isHeldByCurrentThread()) {
                        readLock.unlock();
                        log.debug("Read lock released: {}", fullLockName);
                    }
                }
            } else {
                throw new DistributedLockException("Could not acquire read lock: " + lockName + " within " + waitTime);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("Thread interrupted while waiting for read lock: " + lockName, e);
            
        } catch (Exception e) {
            if (e instanceof DistributedLockException) {
                throw e;
            }
            throw new DistributedLockException("Error executing operation with read lock: " + lockName, e);
        }
    }

    /**
     * Create a write lock for exclusive access
     */
    public <T> T withWriteLock(String lockName, Callable<T> operation, Duration leaseTime, Duration waitTime) {
        String fullLockName = LOCK_PREFIX + "rw:" + lockName;
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(fullLockName);
        RLock writeLock = rwLock.writeLock();
        
        try {
            if (writeLock.tryLock(waitTime.toSeconds(), leaseTime.toSeconds(), TimeUnit.SECONDS)) {
                try {
                    log.debug("Write lock acquired: {}", fullLockName);
                    return operation.call();
                    
                } finally {
                    if (writeLock.isLocked() && writeLock.isHeldByCurrentThread()) {
                        writeLock.unlock();
                        log.debug("Write lock released: {}", fullLockName);
                    }
                }
            } else {
                throw new DistributedLockException("Could not acquire write lock: " + lockName + " within " + waitTime);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("Thread interrupted while waiting for write lock: " + lockName, e);
            
        } catch (Exception e) {
            if (e instanceof DistributedLockException) {
                throw e;
            }
            throw new DistributedLockException("Error executing operation with write lock: " + lockName, e);
        }
    }

    /**
     * Use a semaphore to limit concurrent access
     */
    public <T> T withSemaphore(String semaphoreName, int permits, Callable<T> operation, Duration waitTime) {
        String fullSemaphoreName = SEMAPHORE_PREFIX + semaphoreName;
        RSemaphore semaphore = redissonClient.getSemaphore(fullSemaphoreName);
        
        // Initialize semaphore if it doesn't exist
        if (!semaphore.isExists()) {
            semaphore.trySetPermits(permits);
        }
        
        try {
            if (semaphore.tryAcquire(waitTime.toSeconds(), TimeUnit.SECONDS)) {
                try {
                    log.debug("Semaphore acquired: {} (available: {})", fullSemaphoreName, semaphore.availablePermits());
                    return operation.call();
                    
                } finally {
                    semaphore.release();
                    log.debug("Semaphore released: {} (available: {})", fullSemaphoreName, semaphore.availablePermits());
                }
            } else {
                throw new DistributedLockException("Could not acquire semaphore: " + semaphoreName + " within " + waitTime);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("Thread interrupted while waiting for semaphore: " + semaphoreName, e);
            
        } catch (Exception e) {
            if (e instanceof DistributedLockException) {
                throw e;
            }
            throw new DistributedLockException("Error executing operation with semaphore: " + semaphoreName, e);
        }
    }

    /**
     * Use a countdown latch to coordinate multiple threads
     */
    public void waitForCountdown(String latchName, Duration timeout) {
        String fullLatchName = COUNTDOWN_LATCH_PREFIX + latchName;
        RCountDownLatch latch = redissonClient.getCountDownLatch(fullLatchName);
        
        try {
            log.debug("Waiting for countdown latch: {} (count: {})", fullLatchName, latch.getCount());
            
            if (!latch.await(timeout.toSeconds(), TimeUnit.SECONDS)) {
                log.warn("Timeout waiting for countdown latch: {} after {}", fullLatchName, timeout);
                throw new DistributedLockException("Timeout waiting for countdown latch: " + latchName);
            }
            
            log.debug("Countdown latch completed: {}", fullLatchName);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("Thread interrupted while waiting for countdown latch: " + latchName, e);
        }
    }

    /**
     * Countdown a latch
     */
    public void countDown(String latchName) {
        String fullLatchName = COUNTDOWN_LATCH_PREFIX + latchName;
        RCountDownLatch latch = redissonClient.getCountDownLatch(fullLatchName);
        
        latch.countDown();
        log.debug("Counted down latch: {} (remaining: {})", fullLatchName, latch.getCount());
    }

    /**
     * Initialize a countdown latch with a specific count
     */
    public void initializeCountdownLatch(String latchName, long count) {
        String fullLatchName = COUNTDOWN_LATCH_PREFIX + latchName;
        RCountDownLatch latch = redissonClient.getCountDownLatch(fullLatchName);
        
        latch.trySetCount(count);
        log.debug("Initialized countdown latch: {} with count: {}", fullLatchName, count);
    }

    /**
     * Check if a lock is currently held
     */
    public boolean isLocked(String lockName) {
        String fullLockName = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(fullLockName);
        return lock.isLocked();
    }

    /**
     * Check if a lock is held by current thread
     */
    public boolean isHeldByCurrentThread(String lockName) {
        String fullLockName = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(fullLockName);
        return lock.isHeldByCurrentThread();
    }

    /**
     * Force unlock a lock (use with caution)
     */
    public boolean forceUnlock(String lockName) {
        String fullLockName = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(fullLockName);
        
        try {
            if (lock.isLocked()) {
                lock.forceUnlock();
                log.warn("Force unlocked lock: {}", fullLockName);
                return true;
            }
            return false;
            
        } catch (Exception e) {
            log.error("Error force unlocking: {}", fullLockName, e);
            return false;
        }
    }

    /**
     * Get lock information for monitoring
     */
    public LockInfo getLockInfo(String lockName) {
        String fullLockName = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(fullLockName);
        
        return LockInfo.builder()
                .name(lockName)
                .fullName(fullLockName)
                .locked(lock.isLocked())
                .heldByCurrentThread(lock.isHeldByCurrentThread())
                .holdCount(lock.getHoldCount())
                .remainTimeToLive(lock.remainTimeToLive())
                .build();
    }

    /**
     * Get semaphore information for monitoring
     */
    public SemaphoreInfo getSemaphoreInfo(String semaphoreName) {
        String fullSemaphoreName = SEMAPHORE_PREFIX + semaphoreName;
        RSemaphore semaphore = redissonClient.getSemaphore(fullSemaphoreName);
        
        return SemaphoreInfo.builder()
                .name(semaphoreName)
                .fullName(fullSemaphoreName)
                .exists(semaphore.isExists())
                .availablePermits(semaphore.availablePermits())
                .build();
    }

    // Inner classes for information holders

    @lombok.Data
    @lombok.Builder
    public static class LockInfo {
        private String name;
        private String fullName;
        private boolean locked;
        private boolean heldByCurrentThread;
        private int holdCount;
        private long remainTimeToLive;
    }

    @lombok.Data
    @lombok.Builder
    public static class SemaphoreInfo {
        private String name;
        private String fullName;
        private boolean exists;
        private int availablePermits;
    }

    /**
     * Custom exception for distributed lock operations
     */
    public static class DistributedLockException extends RuntimeException {
        public DistributedLockException(String message) {
            super(message);
        }

        public DistributedLockException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}