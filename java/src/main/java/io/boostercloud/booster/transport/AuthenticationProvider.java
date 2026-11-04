package io.boostercloud.booster.transport;

import io.boostercloud.booster.core.UserContext;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import io.boostercloud.booster.transport.auth.JwtVerifier;

/** Authentication is separate from the core's per-resource authorization policies. */
@FunctionalInterface
public interface AuthenticationProvider {
    UserContext authenticate(String authorizationHeader);

    /** Anonymous requests only. A supplied credential is never silently ignored. */
    static AuthenticationProvider anonymous() {
        return header -> {
            if (header != null) throw new AuthenticationException();
            return UserContext.anonymous();
        };
    }

    static AuthenticationProvider jwt(JwtVerifier verifier) {
        return header -> {
            if (header == null) return UserContext.anonymous();
            if (!header.startsWith("Bearer ") || header.length() <= 7 || header.indexOf(' ', 7) >= 0)
                throw new AuthenticationException();
            var token = verifier.verify(header.substring(7));
            return new UserContext(token.subject(), token.roles(), token.claims());
        };
    }
}
