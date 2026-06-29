package com.atom.infrastructure.adapter.accessibility.oem;

/** Exponential-backoff parameters for resilient capture. */
public record RetryPolicy(int maxAttempts, long baseDelayMs, double multiplier,
                          long maxDelayMs, long totalBudgetMs) {

    /** Delay before the retry that follows {@code attempt} (0-based). */
    public long backoff(int attempt) {
        double delay = baseDelayMs * Math.pow(multiplier, Math.max(0, attempt));
        return (long) Math.min(maxDelayMs, delay);
    }

    /** Stock/known-good skins: settle fast, give up early. */
    public static RetryPolicy lenient() {
        return new RetryPolicy(2, 40L, 2.0, 120L, 300L);
    }

    /** HyperOS/MIUI: more attempts and a larger budget for slow trees. */
    public static RetryPolicy aggressive() {
        return new RetryPolicy(4, 50L, 2.0, 200L, 800L);
    }
}
