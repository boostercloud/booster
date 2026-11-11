package com.boostercloud.framework.types;

/**
 * Port of {@code super-kind.ts}: the persisted values of {@code superKind} for which TypeScript defines constants.
 * The full set of values is {@link com.boostercloud.framework.types.envelope.SuperKindType}.
 */
public final class SuperKind {

  /** {@code 'booster'}: framework-internal events (migrations, touches). */
  public static final String BOOSTER_SUPER_KIND = "booster";

  /** {@code 'domain'}: user events. */
  public static final String DOMAIN_SUPER_KIND = "domain";

  private SuperKind() {}
}
