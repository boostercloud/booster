package io.boostercloud.booster.core;
import com.fasterxml.jackson.databind.JsonNode;
/** Trusted ordered request transformation. Receives an isolated JSON value and authenticated context. */
@FunctionalInterface
public interface BeforeHook { JsonNode apply(JsonNode input, RequestContext context); }
