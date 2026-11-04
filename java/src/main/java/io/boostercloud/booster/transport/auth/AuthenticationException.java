package io.boostercloud.booster.transport.auth;

/** Authentication failed. The message deliberately contains no token or claim data. */
public final class AuthenticationException extends RuntimeException {
    public AuthenticationException() {
        super("Invalid bearer token");
    }
}
