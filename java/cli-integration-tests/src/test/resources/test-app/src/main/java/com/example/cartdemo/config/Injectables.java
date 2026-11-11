package com.example.cartdemo.config;

import com.boostercloud.framework.types.injectable.Injectable;
import com.boostercloud.framework.types.injectable.InjectableCommand;

/**
 * The commands this application adds to the boost CLI (src/config/injectable.ts of the TypeScript test app, whose
 * only command is {@code test}). {@code echo} is a Java addition that shows how the arguments are forwarded.
 */
public final class Injectables {
  static final InjectableCommand TEST =
      InjectableCommand.of("test", "prints a greeting", (config, arguments) -> System.out.println("Hello, world"));

  static final InjectableCommand ECHO =
      InjectableCommand.of(
          "echo",
          "prints its arguments and the application name",
          (config, arguments) ->
              System.out.println("echo from " + config.appName + ": " + String.join(" ", arguments)));

  static final Injectable INJECTABLE = Injectable.of(TEST, ECHO);

  private Injectables() {}
}
