package com.jm.inventory.infrastructure.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;

class FailOpenCacheTest {

    private static final UUID KEY = UUID.randomUUID();

    private final Cache redisCache = mock(Cache.class);
    private final FailOpenCache failOpenCache = new FailOpenCache(redisCache);
    private final Logger failOpenCacheLogger = (Logger) LoggerFactory.getLogger(FailOpenCache.class);
    private final ListAppender<ILoggingEvent> capturedLogs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        when(redisCache.getName()).thenReturn(CacheNames.PRODUCTS_BY_ID);
        capturedLogs.start();
        failOpenCacheLogger.addAppender(capturedLogs);
    }

    @AfterEach
    void releaseLogs() {
        failOpenCacheLogger.detachAppender(capturedLogs);
    }

    @Test
    void given_redisIsDown_when_reading_then_itIsAMissLoggedAsWarn() {
        // Given
        when(redisCache.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        // When
        Cache.ValueWrapper cached = failOpenCache.get(KEY);

        // Then
        assertThat(cached).isNull();
        assertThat(onlyLoggedLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void given_aStoredValueNoLongerMatchesItsClass_when_reading_then_itIsAMissLoggedAsWarn() {
        // Given
        when(redisCache.get(KEY)).thenThrow(new SerializationException("shape changed"));

        // When
        Cache.ValueWrapper cached = failOpenCache.get(KEY);

        // Then
        assertThat(cached).isNull();
        assertThat(onlyLoggedLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void given_redisIsDown_when_readingWithALoader_then_theLoaderAnswers() {
        // Given
        when(redisCache.get(any(), any(Callable.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        // When
        String loaded = failOpenCache.get(KEY, () -> "from postgres");

        // Then
        assertThat(loaded).isEqualTo("from postgres");
        assertThat(onlyLoggedLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void given_redisIsDown_when_storing_then_nothingIsThrownAndAWarnIsLogged() {
        // Given
        doThrow(new RedisConnectionFailureException("down")).when(redisCache).put(KEY, "value");

        // When / Then
        assertThatCode(() -> failOpenCache.put(KEY, "value")).doesNotThrowAnyException();
        assertThat(onlyLoggedLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void given_redisIsDown_when_evicting_then_nothingIsThrownAndAnErrorIsLogged() {
        // Given
        doThrow(new RedisConnectionFailureException("down")).when(redisCache).evict(KEY);

        // When / Then
        assertThatCode(() -> failOpenCache.evict(KEY)).doesNotThrowAnyException();
        assertThat(onlyLoggedLevel()).isEqualTo(Level.ERROR);
    }

    private Level onlyLoggedLevel() {
        assertThat(capturedLogs.list).hasSize(1);
        return capturedLogs.list.get(0).getLevel();
    }
}
