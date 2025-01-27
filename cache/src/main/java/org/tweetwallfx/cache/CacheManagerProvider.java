/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2022 TweetWallFX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.tweetwallfx.cache;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ehcache.config.Builder;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.event.EventFiring;
import org.ehcache.event.EventOrdering;
import org.ehcache.event.EventType;
import org.ehcache.expiry.ExpiryPolicy;
import org.ehcache.impl.config.persistence.CacheManagerPersistenceConfiguration;
import org.tweetwallfx.config.Configuration;
import org.tweetwallfx.cache.CacheSettings.CacheResource;

public final class CacheManagerProvider {

    /**
     * Configuration key under which the data for this Settings object is stored
     * in the configuration data map.
     */
    private static final Collection<String> LISTENERS_ADDED_TO_CACHES = new HashSet<>(4);
    private static final Logger LOG = LogManager.getLogger(CacheManagerProvider.class);
    private static final org.ehcache.CacheManager CACHE_MANAGER = createCacheManager();

    private CacheManagerProvider() {
        // prevent instantiation
    }

    public static <K, V> Cache<K, V> getCache(final String alias, final Class<K> keyClass, final Class<V> valueClass) {
        final org.ehcache.Cache<K, V> cache = CACHE_MANAGER.getCache(alias, keyClass, valueClass);

        if (null == cache) {
            throw new IllegalArgumentException("No cache named '" + alias + "' exists!");
        } else if (LISTENERS_ADDED_TO_CACHES.add(alias)) {
            cache.getRuntimeConfiguration().registerCacheEventListener(
                    event -> LOG.debug("Cache({}) @ Key '{}'- {}", alias, event.getKey(), event.getType()),
                    EventOrdering.UNORDERED,
                    EventFiring.ASYNCHRONOUS,
                    EnumSet.allOf(EventType.class)
            );
        }

        return new Cache<>(cache);
    }

    private static org.ehcache.CacheManager createCacheManager() {
        final CacheSettings cacheSettings = Configuration.getInstance().getConfigTyped(CacheSettings.CONFIG_KEY, CacheSettings.class);
        CacheManagerBuilder<? extends org.ehcache.CacheManager> cacheManagerBuilder = CacheManagerBuilder
                .newCacheManagerBuilder()
                .with(new CacheManagerPersistenceConfiguration(new File(
                        System.getProperty("user.home"),
                        cacheSettings.persistenceDirectoryName())));

        for (final Map.Entry<String, CacheSettings.CacheSetting> entry : cacheSettings.caches().entrySet()) {
            final String alias = entry.getKey();
            final CacheSettings.CacheSetting cacheSetting = entry.getValue();

            CacheConfigurationBuilder<?, ?> builder = CacheConfigurationBuilder
                    .newCacheConfigurationBuilder(
                            loadClass(cacheSetting.keyType()),
                            loadClass(cacheSetting.valueType()),
                            createResourcePoolsBuilder(cacheSetting.cacheResources()));

            if (null != cacheSetting.expiry()) {
                builder = builder.withExpiry(createExpiryPolicy(cacheSetting.expiry()));
            }

            cacheManagerBuilder = cacheManagerBuilder.withCache(alias, builder);
        }

        final org.ehcache.CacheManager cacheManager = cacheManagerBuilder.build(true);

        LOG.info("EHCaches: " + cacheManager.getRuntimeConfiguration().getCacheConfigurations().keySet());
        cacheManager.getRuntimeConfiguration().getCacheConfigurations().keySet().forEach(s -> LOG.info("EHCache: " + s));
        Runtime.getRuntime().addShutdownHook(new Thread(cacheManager::close, "cache-shutdown"));

        return cacheManager;
    }

    private static Class<?> loadClass(final String className) {
        Objects.requireNonNull(className, "className must not be null!");

        try {
            return ClassLoader.getSystemClassLoader().loadClass(className);
        } catch (final ClassNotFoundException ex) {
            throw new IllegalStateException("Unable to load class '" + className + "'");
        }
    }

    private static Builder<ResourcePools> createResourcePoolsBuilder(final Collection<CacheResource> cacheResources) {
        ResourcePoolsBuilder builder = ResourcePoolsBuilder.newResourcePoolsBuilder();

        for (CacheResource cacheResource : cacheResources) {
            builder = addResource(builder, cacheResource);
        }

        return builder;
    }

    private static ResourcePoolsBuilder addResource(final ResourcePoolsBuilder builder, final CacheResource cacheResource) {
        return switch (cacheResource.type()) {
            case DISK -> builder.disk(cacheResource.amount(), convert(cacheResource.unit()), true);
            case HEAP -> builder.heap(cacheResource.amount(), EntryUnit.ENTRIES);
            case OFFHEAP -> builder.offheap(cacheResource.amount(), convert(cacheResource.unit()));
        };
    }

    private static ExpiryPolicy<Object, Object> createExpiryPolicy(final CacheSettings.CacheExpiry cacheExpiry) {
        return switch (cacheExpiry.type()) {
            case NONE -> ExpiryPolicyBuilder.noExpiration();
            case TIME_TO_IDLE -> ExpiryPolicyBuilder.timeToIdleExpiration(cacheExpiry.produceDuration());
            case TIME_TO_LIVE -> ExpiryPolicyBuilder.timeToLiveExpiration(cacheExpiry.produceDuration());
        };
        }

    private static MemoryUnit convert(final CacheSettings.MemUnit memUnit) {
        return switch(memUnit) {
            case B -> MemoryUnit.B;
            case KB -> MemoryUnit.KB;
            case MB -> MemoryUnit.MB;
            case GB -> MemoryUnit.GB;
            case TB -> MemoryUnit.TB;
            case PB -> MemoryUnit.PB;
        };
    }
}
