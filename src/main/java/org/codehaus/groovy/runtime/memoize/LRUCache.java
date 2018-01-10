/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.codehaus.groovy.runtime.memoize;

import org.apache.groovy.util.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * A cache backed by a ConcurrentLinkedHashMap
 *
 * @author Vaclav Pech
 * @author <a href="mailto:realbluesun@hotmail.com">Daniel.Sun</a>
 */
public final class LRUCache<K, V> implements MemoizeCache<K, V> {
    private final ConcurrentMap<K, V> cache;

    public LRUCache(final int maxCacheSize) {
//        cache = Collections.synchronizedMap(new LRUProtectionStorage(maxCacheSize));
        cache = new ConcurrentLinkedHashMap.Builder<K, V>()
                .maximumWeightedCapacity(maxCacheSize)
                .build();
    }

    @Override
    public V put(final K key, final V value) {
        return cache.put(key, value);
    }

    @Override
    public V get(final K key) {
        return cache.get(key);
    }

    /**
     * Try to get the value from cache.
     * If not found, create the value by {@link ValueProvider} and put it into the cache, at last return the value.
     *
     * The operation is completed atomically.
     *
     * @param key
     * @param valueProvider provide the value if the associated value not found
     * @return
     */
    @Override
    public V getAndPut(K key, ValueProvider<? super K, ? extends V> valueProvider) {
        return cache.computeIfAbsent(key, k -> valueProvider.provide(k));
    }

    /**
     * Remove all entries holding SoftReferences to gc-evicted objects.
     */
    public void cleanUpNullReferences() {
        synchronized (cache) {
            final Iterator<Map.Entry<K, V>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<K, V> entry = iterator.next();
                final Object value = entry.getValue();

                if (!(value instanceof SoftReference)) continue;
                if (((SoftReference) value).get() == null) iterator.remove();
            }
        }
    }
}
