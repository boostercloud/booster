package com.boostercloud.integration.config;

import com.boostercloud.framework.types.injectable.Injectable;
import com.boostercloud.framework.types.injectable.InjectableCommand;

/**
 * The commands this application injects into the {@code boost} CLI ({@code src/config/injectable.ts}): {@code test},
 * which prints {@code Hello, world}. Run with {@code BOOSTER_CLI_HOOK=true java ... Main test} (what {@code boost test}
 * does).
 */
public final class Injectables {

  /** {@code Injectable.command('test', {}, () => Console.log('Hello, world'))}. */
  public static final InjectableCommand TEST_COMMAND =
      InjectableCommand.of("test", (config, arguments) -> System.out.println("Hello, world"));

  /** {@code { commands: [testCommand] }}. */
  public static final Injectable INJECTABLE = Injectable.of(TEST_COMMAND);

  private Injectables() {}
}
