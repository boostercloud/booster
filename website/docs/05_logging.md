# Logging in Booster

If no configuration is provided, Booster uses the default JavaScript logging capabilities. Depending on the log level, it will call to `console.debug`, `console.info`, `console.warn` or `console.error`. In this regard, there's no distinction from any other node process and you'll find the logs in your cloud provider's default log aggregator (i.e. Cloudwatch if you use AWS).

## Advanced logging

If you need advanced logging capabilities such as redirecting your logs to a log aggregator, Booster also supports overriding the default behavior by providing custom loggers. The only thing you need to do is to provide an object that implements the `Logger` interface at config time:

_The Logger interface (In package `@boostercloud/framework-types`):_

```typescript
interface Logger {
  debug(message?: any, ...optionalParams: any[]): void
  info(message?: any, ...optionalParams: any[]): void
  warn(message?: any, ...optionalParams: any[]): void
  error(message?: any, ...optionalParams: any[]): void
}
```

You can set your logger, as well as the log level and your preferred log prefix (Defaults to the string `'Booster'`) in your `config.ts` file for each of your environments:

_In your project's config.ts file:_

```typescript
Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.providerPackage = '@boostercloud/framework-provider-aws'
  
  config.logger = new MyCustomLogger() // Overrides the default logger object
  config.logLevel = Level.debug        // Sets the log level at 'debug'     
  config.logPrefix = 'my-store-dev'    // Sets the default prefix
})
```

## Using the Booster's logger

All framework's components will use this logger by default and will generate logs that match the following pattern:

```text
[<logPrefix>]|moduleName: <message>
```

You can get a custom logger instance that extends the configured logger by adding your moduleName and optionally overriding the configured prefix with the `getLogger` helper function. It's a good practice to build and use a separate logger instance built with this method for each context, as this will make it easier to filter your logs when you need to investigate a problem.

_Example: Obtaining a logger for your command:_

```typescript
@Command({
  authorize: [User],
})
export class UpdateShippingAddress {
  public constructor(readonly cartId: UUID, readonly address: Address) {}

  public static async handle(command: UpdateShippingAddress, register: Register): Promise<void> {
    const logger = getLogger(Booster.config, 'UpdateShippingCommand#handler', 'MyApp')
    logger.debug(`User ${register.currentUser?.username} changed shipping address for cart ${command.cartId}: ${JSON.stringify(command.address}`)
    register.events(new ShippingAddressUpdated(command.cartId, command.address))
  }
}

```

When a `UpdateShippingAddress` command is handled, it wil log messages that look like the following:

```text
[MyApp]|UpdateShippingCommand#handler: User buyer42 changed shipping address for cart 314: { street: '13th rue del percebe', number: 6, ... }
```

Using the configured Booster logger is not mandatory for your application, but it might be convenient to centralize your logs and this is a standard way to do it.