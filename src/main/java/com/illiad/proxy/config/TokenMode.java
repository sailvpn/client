package com.illiad.proxy.config;

/**
 * Mode for JWT token management.
 * <ul>
 *   <li>MANUAL - user provides a long-lived token (e.g. up to 6 months) which is used as-is and not auto-renewed.</li>
 *   <li>AUTO   - client will try to obtain a token at startup (prefer username/password, fall back to existing token)
 *                and will periodically renew the token while running.</li>
 * </ul>
 */
public enum TokenMode {
    MANUAL,
    AUTO;

    /**
     * Recommended long-term token duration in months for MANUAL mode (informational).
     */
    public static final int LONG_TERM_TOKEN_MONTHS = 6;
}

