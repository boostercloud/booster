package io.boostercloud.booster.core;

/** Named raw provider function. It can return binary/provider responses and invoke authorized actions explicitly. */
@FunctionalInterface
public interface RocketFunction {
  Object invoke(Booster runtime, Object request, RequestContext context) throws Exception;
}
