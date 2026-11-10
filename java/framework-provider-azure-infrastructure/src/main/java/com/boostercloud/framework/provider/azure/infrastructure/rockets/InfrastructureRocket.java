package com.boostercloud.framework.provider.azure.infrastructure.rockets;

import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppFunctionsDefinition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;

/**
 * The Azure infrastructure rocket contract ({@code InfrastructureRocket} of
 * {@code rockets/infrastructure-rocket.ts}). An Azure rocket package {@code <pkg>} contains {@code <pkg>.Rocket}, a
 * {@link com.boostercloud.framework.common.RocketBuilder} that receives the descriptor parameters and returns an
 * implementation of this interface ({@code RocketLoader.loadRocket(descriptor, InfrastructureRocket.class)}).
 *
 * <p>Only {@link #mountStack} is mandatory. The TypeScript members marked {@code ?} are default methods here; a rocket
 * "defines" one when its class overrides it ({@link #defines(Hook)}), which is what the infrastructure checks where
 * TypeScript tests {@code if (rocket.mountCode)}.
 *
 * <ul>
 *   <li>{@link #mountStack} adds resources to {@code applicationSynthStack.terraformStack} and/or modifies the bag;
 *       every rocket's is called, in order, before the app settings of the main function app are computed.
 *   <li>{@link #getFunctionAppName} names the rocket's function app ({@code ROCKET_FUNCTION_APP_NAMES},
 *       {@code ROCKET_PACKAGE_MAPPING}, the deprecated {@code mountFunctions} path).
 *   <li>{@link #mountFunctionsV4}, else {@link #mountCode}, else {@link #mountFunctions} provides the packages to
 *       build and upload (only the first defined one is used).
 *   <li>{@link #unmountStack} runs at the start of {@code nuke}.
 * </ul>
 */
public interface InfrastructureRocket {

  /** The optional members of the contract. */
  enum Hook {
    /** {@code unmountStack()}. */
    UNMOUNT_STACK("unmountStack"),
    /** {@code getFunctionAppName(stack)}. */
    GET_FUNCTION_APP_NAME("getFunctionAppName", ApplicationSynthStack.class),
    /** {@code mountFunctions(config, stack, utils)} (deprecated). */
    MOUNT_FUNCTIONS("mountFunctions", BoosterConfig.class, ApplicationSynthStack.class, RocketUtils.class),
    /** {@code mountCode(config, stack, utils)}. */
    MOUNT_CODE("mountCode", BoosterConfig.class, ApplicationSynthStack.class, RocketUtils.class),
    /** {@code mountFunctionsV4(config, stack, utils)}. */
    MOUNT_FUNCTIONS_V4("mountFunctionsV4", BoosterConfig.class, ApplicationSynthStack.class, RocketUtils.class);

    private final String methodName;
    private final Class<?>[] parameterTypes;

    Hook(String methodName, Class<?>... parameterTypes) {
      this.methodName = methodName;
      this.parameterTypes = parameterTypes;
    }

    /**
     * @return the method name
     */
    public String methodName() {
      return methodName;
    }
  }

  /**
   * {@code mountStack(config, applicationSynthStack, utils)}.
   *
   * @param config the configuration
   * @param applicationSynthStack the stack bag (by reference)
   * @param utils the utilities
   * @return the stack bag (ignored by the infrastructure, as in TypeScript)
   */
  ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack applicationSynthStack, RocketUtils utils);

  /** {@code unmountStack?()}: called (not awaited in TypeScript) at the start of {@code nuke}. */
  default void unmountStack() {}

  /**
   * {@code getFunctionAppName?(applicationSynthStack)}.
   *
   * @param applicationSynthStack the stack bag
   * @return the rocket's function app name
   */
  default String getFunctionAppName(ApplicationSynthStack applicationSynthStack) {
    return null;
  }

  /**
   * {@code mountFunctions?(config, applicationSynthStack, utils)}: the functions of the function app named by
   * {@link #getFunctionAppName}.
   *
   * @param config the configuration
   * @param applicationSynthStack the stack bag
   * @param utils the utilities
   * @return the functions
   * @deprecated use {@link #mountCode} or {@link #mountFunctionsV4}
   */
  @Deprecated
  default List<FunctionDefinition> mountFunctions(
      BoosterConfig config, ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    return List.of();
  }

  /**
   * {@code mountCode?(config, applicationSynthStack, utils)}: legacy packages (all the sources plus the definitions).
   *
   * @param config the configuration
   * @param applicationSynthStack the stack bag
   * @param utils the utilities
   * @return one definition per function app
   */
  default List<FunctionAppFunctionsDefinition> mountCode(
      BoosterConfig config, ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    return List.of();
  }

  /**
   * {@code mountFunctionsV4?(config, applicationSynthStack, utils)}: packages with the main package layout.
   *
   * @param config the configuration
   * @param applicationSynthStack the stack bag
   * @param utils the utilities
   * @return one definition per function app
   */
  default List<FunctionAppV4Definition> mountFunctionsV4(
      BoosterConfig config, ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    return List.of();
  }

  /**
   * Whether the rocket defines an optional member (TypeScript: the property is not {@code undefined}): its class
   * overrides the default method.
   *
   * @param hook the member
   * @return whether it is defined
   */
  default boolean defines(Hook hook) {
    try {
      return getClass().getMethod(hook.methodName, hook.parameterTypes).getDeclaringClass()
          != InfrastructureRocket.class;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }
}
