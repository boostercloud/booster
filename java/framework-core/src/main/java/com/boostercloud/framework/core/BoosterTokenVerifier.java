package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.common.SettledResult;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.services.tokenverifiers.NotBeforeError;
import com.boostercloud.framework.core.services.tokenverifiers.TokenExpiredError;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.TokenVerifier;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.BoosterTokenExpiredError;
import com.boostercloud.framework.types.errors.BoosterTokenNotBeforeError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Port of {@code booster-token-verifier.ts}: runs a token through every configured {@link TokenVerifier}
 * concurrently, waits for all of them, and returns the user of the first one (in configuration order) whose
 * {@code verify} and {@code toUserEnvelope} both succeed. When none succeeds it throws, in this precedence:
 * {@link BoosterTokenExpiredError} (joining only the {@code jwt expired} reasons), {@link BoosterTokenNotBeforeError}
 * (the {@code jwt not active} reasons), else {@link NotAuthorizedError} with every reason; reasons are the JavaScript
 * texts of the errors ({@code TokenExpiredError: jwt expired}) joined with new lines. No verifiers means
 * {@code NotAuthorizedError("")}.
 */
public class BoosterTokenVerifier {

  private final BoosterConfig config;

  /**
   * @param config the configuration ({@code config.tokenVerifiers} is read on every call)
   */
  public BoosterTokenVerifier(BoosterConfig config) {
    this.config = config;
  }

  /**
   * {@code verify(token)}.
   *
   * @param token the token, with or without {@code Bearer }
   * @return the user
   */
  public UserEnvelope verify(String token) {
    List<TokenVerifier> verifiers = config.tokenVerifiers == null ? List.of() : List.copyOf(config.tokenVerifiers);
    List<Callable<UserEnvelope>> verifications = verifiers.stream()
        .<Callable<UserEnvelope>>map(verifier -> () -> verifier.toUserEnvelope(verifier.verify(token)))
        .toList();
    List<SettledResult<UserEnvelope>> userEnvelopes = Promises.runAllSettled(verifications);
    for (SettledResult<UserEnvelope> result : userEnvelopes) {
      if (result instanceof SettledResult.Fulfilled<UserEnvelope> fulfilled) {
        return fulfilled.value();
      }
    }
    throw rejectVerification(userEnvelopes);
  }

  private static RuntimeException rejectVerification(List<SettledResult<UserEnvelope>> results) {
    List<Throwable> reasons = results.stream()
        .filter(result -> result instanceof SettledResult.Rejected<UserEnvelope>)
        .map(result -> ((SettledResult.Rejected<UserEnvelope>) result).reason())
        .toList();
    List<Throwable> tokenExpiredErrors = reasons.stream().filter(TokenExpiredError.class::isInstance).toList();
    if (!tokenExpiredErrors.isEmpty()) {
      return new BoosterTokenExpiredError(joinReasons(tokenExpiredErrors));
    }
    List<Throwable> tokenNotBeforeErrors = reasons.stream().filter(NotBeforeError.class::isInstance).toList();
    if (!tokenNotBeforeErrors.isEmpty()) {
      return new BoosterTokenNotBeforeError(joinReasons(tokenNotBeforeErrors));
    }
    return new NotAuthorizedError(joinReasons(reasons));
  }

  private static String joinReasons(List<Throwable> errors) {
    return String.join("\n", errors.stream().map(Js::errorToString).toList());
  }
}
