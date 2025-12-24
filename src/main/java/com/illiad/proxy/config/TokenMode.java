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
     * Parse a string to TokenMode. Accepts case-insensitive values "manual" or "auto".
     * Defaults to MANUAL if input is null/empty or unrecognized.
     */
    public static TokenMode fromString(String s) {
        if (s == null) return MANUAL;
        String t = s.trim().toLowerCase();
        switch (t) {
            case "auto":
                return AUTO;
            case "manual":
            default:
                return MANUAL;
        }
    }

    public boolean isAuto() { return this == AUTO; }
    public boolean isManual() { return this == MANUAL; }

    /**
     * Recommended long-term token duration in months for MANUAL mode (informational).
     */
    public static final int LONG_TERM_TOKEN_MONTHS = 6;
}

