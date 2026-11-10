package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Deploy-time operations of a provider. Port of {@code ProviderInfrastructure}, whose four members are optional:
 * the default methods throw {@link UnsupportedOperationException}, and {@link #supports(Operation)} tells whether an
 * implementation overrides one (the CLI checks it before running a command).
 */
public interface ProviderInfrastructure {

  /** The operations. */
  enum Operation {
    /** {@code deploy(config)}. */
    DEPLOY("deploy"),
    /** {@code nuke(config)}. */
    NUKE("nuke"),
    /** {@code start(config, port)}. */
    START("start"),
    /** {@code synth(config)}. */
    SYNTH("synth");

    private final String methodName;

    Operation(String methodName) {
      this.methodName = methodName;
    }

    /**
     * @return the method name
     */
    public String methodName() {
      return methodName;
    }
  }

  /**
   * Deploys the application.
   *
   * @param config the configuration
   */
  default void deploy(BoosterConfig config) {
    throw unsupported(Operation.DEPLOY);
  }

  /**
   * Removes the deployed application.
   *
   * @param config the configuration
   */
  default void nuke(BoosterConfig config) {
    throw unsupported(Operation.NUKE);
  }

  /**
   * Starts the application locally; blocks while it runs.
   *
   * @param config the configuration
   * @param port the HTTP port
   */
  default void start(BoosterConfig config, int port) {
    throw unsupported(Operation.START);
  }

  /**
   * Generates the deployment artifacts without deploying.
   *
   * @param config the configuration
   */
  default void synth(BoosterConfig config) {
    throw unsupported(Operation.SYNTH);
  }

  /**
   * @param operation an operation
   * @return whether this implementation overrides it (TypeScript: the member is defined)
   */
  default boolean supports(Operation operation) {
    try {
      Class<?>[] parameters = operation == Operation.START
          ? new Class<?>[] {BoosterConfig.class, int.class}
          : new Class<?>[] {BoosterConfig.class};
      return getClass().getMethod(operation.methodName(), parameters).getDeclaringClass() != ProviderInfrastructure.class;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private UnsupportedOperationException unsupported(Operation operation) {
    return new UnsupportedOperationException(
        "The provider infrastructure " + getClass().getName() + " does not support the '" + operation.methodName()
            + "' operation");
  }
}
