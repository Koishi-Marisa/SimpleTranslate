package com.yourname.simpletranslate.api;

/**
 * Snapshot of token usage for a single completed translation request.
 * Recorded by {@link com.yourname.simpletranslate.transport.TokenUsageMonitor}
 * and displayed on the token monitor screen.
 *
 * <p>{@code cacheHitTokens}/{@code cacheMissTokens} mirror the provider's
 * prompt-cache accounting (DeepSeek reports them as
 * {@code prompt_cache_hit_tokens}/{@code prompt_cache_miss_tokens}). They stay
 * {@code 0} for providers/endpoints that do not report cache usage, so the
 * monitor can treat {@code promptTokens} as the single source of truth for the
 * total prompt size.</p>
 */
public record TokenUsage(
        String apiFormat,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long elapsedMs,
        long timestampMillis,
        String surface,
        int cacheHitTokens,
        int cacheMissTokens) {

    /**
     * Share of the prompt served from the provider's cache, in {@code [0, 1]},
     * or {@code -1} when the response carried no cache accounting at all.
     */
    public double cacheHitRatio() {
        int accounted = cacheHitTokens + cacheMissTokens;
        if (accounted <= 0) {
            return -1.0D;
        }
        return Math.min(1.0D, (double) cacheHitTokens / (double) accounted);
    }

    public boolean hasCacheAccounting() {
        return cacheHitTokens + cacheMissTokens > 0;
    }
}
