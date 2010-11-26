package org.codehaus.groovy.runtime.memoize

import java.lang.ref.SoftReference
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

/**
 * @author Vaclav Pech
 */
public class CacheCleanupTest extends GroovyTestCase {
    private static final Object ANCHOR = "I'm never gonna go"
    
    public void testUnlimitedCache() {
        checkCache(new UnlimitedConcurrentCache())
        checkCache(new LRUCache(10))
    }

    private def checkCache(MemoizeCache cache) {
        assert cache.cache.size() == 0
        cache.put('key1', new SoftReference(ANCHOR))
        cache.put('key2', new SoftReference(ANCHOR))
        assert cache.cache.size() == 2
        cache.put('key3', new SoftReference(null))  //Simulating evicted objects
        cache.put('key4', new SoftReference(null))
        cache.cleanUpNullReferences()
        assert cache.cache.size() == 2
    }

    public void testUnlimitedCacheConcurrently() {
        checkCacheConcurrently(new UnlimitedConcurrentCache())
        checkCacheConcurrently(new LRUCache(2000))
        checkCacheConcurrently(new LRUCache(50))  //testing a cache that removes old elements
    }

    private def checkCacheConcurrently(MemoizeCache cache) {
        assert cache.cache.size() == 0
        cache.put('key1', new SoftReference(ANCHOR))
        cache.put('key2', new SoftReference(ANCHOR))
        assert cache.cache.size() == 2
        for (i in (3..1000)) {
            cache.put("key${i}", new SoftReference(null))  //Simulating evicted objects
            cache.get('key1')  //touch the non-null cache entries to keep them hot to prevent a potential LRU algorithm from evicting them
            cache.get('key2')
        }

        final CyclicBarrier barrier = new CyclicBarrier(11)
        10.times {
            Thread.start {
                barrier.await()
                cache.cleanUpNullReferences()
                barrier.await()
            }
        }
        barrier.await(30, TimeUnit.SECONDS)  //start threads
        barrier.await(30, TimeUnit.SECONDS)  //wait for threads to finish

        assert cache.cache.size() == 2
    }
}
