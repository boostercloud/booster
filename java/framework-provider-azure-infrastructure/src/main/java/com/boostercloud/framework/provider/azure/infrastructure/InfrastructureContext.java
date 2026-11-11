package com.boostercloud.framework.provider.azure.infrastructure;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.provider.azure.Constants.OtherEnvironmentVarNames;
import com.boostercloud.framework.provider.azure.infrastructure.azure.ArmClient;
import com.boostercloud.framework.provider.azure.infrastructure.azure.ClientSecretCredential;
import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.azure.JdkHttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.azure.ResourceManagementClient;
import com.boostercloud.framework.provider.azure.infrastructure.azure.TokenCredential;
import com.boostercloud.framework.provider.azure.infrastructure.azure.WebPubSubManagementClient;
import com.boostercloud.framework.provider.azure.infrastructure.azure.WebSiteManagementClient;
import com.boostercloud.framework.provider.azure.infrastructure.helper.DeploySources;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.process.CommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.process.ProcessCommandRunner;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;

/**
 * Everything the Azure infrastructure touches outside the JVM, injectable so tests run the real flows without
 * deploying: the project root ({@code process.cwd()} in TypeScript), the application class the function apps boot, the
 * package sources, the command runner ({@code terraform}, {@code az}), the HTTP transport (Entra ID, Resource Manager,
 * Kudu, the function app), the sleeper (polling delays) and the Azure settings.
 */
public final class InfrastructureContext {

  private final Path projectRoot;
  private final String applicationClass;
  private final Function<BoosterConfig, DeploySources> deploySources;
  private final CommandRunner commandRunner;
  private final HttpTransport httpTransport;
  private final Retrier.Sleeper sleeper;
  private final Params.Configuration configuration;
  private final Clock clock;
  private volatile TokenCredential credential;

  private InfrastructureContext(Builder builder) {
    this.projectRoot = Objects.requireNonNull(builder.projectRoot, "projectRoot").toAbsolutePath().normalize();
    this.applicationClass = builder.applicationClass;
    this.deploySources = builder.deploySources != null
        ? builder.deploySources
        : config -> DeploySources.fromRuntime(this.projectRoot, config.assets);
    this.commandRunner = builder.commandRunner != null ? builder.commandRunner : ProcessCommandRunner.INSTANCE;
    this.httpTransport = builder.httpTransport != null ? builder.httpTransport : new JdkHttpTransport();
    this.sleeper = builder.sleeper != null ? builder.sleeper : Retrier.Sleeper.THREAD_SLEEP;
    this.configuration = builder.configuration != null ? builder.configuration : Params.configuration();
    this.clock = builder.clock != null ? builder.clock : Clock.systemUTC();
    this.credential = builder.credential;
  }

  /**
   * The production context: the project root is {@code config.userProjectRootPath()} when {@code Booster.start} set
   * it, else the working directory; the application class is {@link #resolveApplicationClass()}; the Azure settings
   * are read from the environment now.
   *
   * @param config the configuration
   * @return the context
   */
  public static InfrastructureContext defaults(BoosterConfig config) {
    Path root;
    try {
      root = Path.of(config.userProjectRootPath());
    } catch (IllegalStateException notStarted) {
      root = Path.of(System.getProperty("user.dir"));
    }
    return builder().projectRoot(root).applicationClass(resolveApplicationClass()).build();
  }

  /**
   * The class the function apps pass to {@code Booster.start} ({@code BOOSTER_APP_CLASS}): the
   * {@code BOOSTER_APP_CLASS} environment variable when set, otherwise the main class of this JVM (the {@code boost}
   * CLI runs {@code java -cp ... <mainClass> deploy}).
   *
   * @return the class name, or {@code null} when it cannot be determined
   */
  public static String resolveApplicationClass() {
    String explicit = EnvironmentVariables.getOrDefault(OtherEnvironmentVarNames.BOOSTER_APP_CLASS, "");
    if (!explicit.isEmpty()) {
      return explicit;
    }
    String command = System.getProperty("sun.java.command", "").strip();
    if (command.isEmpty()) {
      return null;
    }
    String main = command.split("\\s+", 2)[0];
    if (main.endsWith(".jar") || main.contains("/") || main.contains("\\")) {
      return null;
    }
    return main;
  }

  /**
   * @return a builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * @return the project root
   */
  public Path projectRoot() {
    return projectRoot;
  }

  /**
   * @return the application class ({@code BOOSTER_APP_CLASS})
   * @throws IllegalStateException when it could not be determined
   */
  public String applicationClass() {
    if (applicationClass == null || applicationClass.isEmpty()) {
      throw new IllegalStateException("Could not determine the application class the Azure function apps must start."
          + " Set the " + OtherEnvironmentVarNames.BOOSTER_APP_CLASS + " environment variable to the class passed to"
          + " Booster.run (or Booster.start)");
    }
    return applicationClass;
  }

  /**
   * @param config the configuration
   * @return the package sources
   */
  public DeploySources deploySources(BoosterConfig config) {
    return deploySources.apply(config);
  }

  /**
   * @return the command runner
   */
  public CommandRunner commandRunner() {
    return commandRunner;
  }

  /**
   * @return the HTTP transport
   */
  public HttpTransport httpTransport() {
    return httpTransport;
  }

  /**
   * @return the sleeper
   */
  public Retrier.Sleeper sleeper() {
    return sleeper;
  }

  /**
   * @return the Azure settings
   */
  public Params.Configuration configuration() {
    return configuration;
  }

  /**
   * @return the clock
   */
  public Clock clock() {
    return clock;
  }

  /**
   * {@code azureCredentials()}: the service principal credential (created on first use).
   *
   * @return the credential
   */
  public TokenCredential credential() {
    TokenCredential current = credential;
    if (current == null) {
      synchronized (this) {
        if (credential == null) {
          credential = new ClientSecretCredential(configuration.tenantId(), configuration.appId(),
              configuration.secret(), httpTransport, clock);
        }
        current = credential;
      }
    }
    return current;
  }

  /**
   * @return a Resource Manager client for {@code AZURE_SUBSCRIPTION_ID}
   */
  public ArmClient armClient() {
    return new ArmClient(credential(), configuration.subscriptionId(), httpTransport, sleeper);
  }

  /**
   * @return {@code createWebSiteManagementClient(credentials)}
   */
  public WebSiteManagementClient webSiteManagementClient() {
    return new WebSiteManagementClient(armClient());
  }

  /**
   * @return {@code createWebPubSubManagementClient(credentials)}
   */
  public WebPubSubManagementClient webPubSubManagementClient() {
    return new WebPubSubManagementClient(armClient());
  }

  /**
   * @return {@code createResourceManagementClient(credentials)}
   */
  public ResourceManagementClient resourceManagementClient() {
    return new ResourceManagementClient(armClient());
  }

  /** Builds a context; unset parts get their production default. */
  public static final class Builder {
    private Path projectRoot;
    private String applicationClass;
    private Function<BoosterConfig, DeploySources> deploySources;
    private CommandRunner commandRunner;
    private HttpTransport httpTransport;
    private Retrier.Sleeper sleeper;
    private Params.Configuration configuration;
    private Clock clock;
    private TokenCredential credential;

    private Builder() {}

    /**
     * @param projectRoot the project root
     * @return this builder
     */
    public Builder projectRoot(Path projectRoot) {
      this.projectRoot = projectRoot;
      return this;
    }

    /**
     * @param applicationClass the application class ({@code BOOSTER_APP_CLASS})
     * @return this builder
     */
    public Builder applicationClass(String applicationClass) {
      this.applicationClass = applicationClass;
      return this;
    }

    /**
     * @param deploySources the package sources
     * @return this builder
     */
    public Builder deploySources(DeploySources deploySources) {
      this.deploySources = config -> deploySources;
      return this;
    }

    /**
     * @param commandRunner the command runner
     * @return this builder
     */
    public Builder commandRunner(CommandRunner commandRunner) {
      this.commandRunner = commandRunner;
      return this;
    }

    /**
     * @param httpTransport the HTTP transport
     * @return this builder
     */
    public Builder httpTransport(HttpTransport httpTransport) {
      this.httpTransport = httpTransport;
      return this;
    }

    /**
     * @param sleeper the sleeper
     * @return this builder
     */
    public Builder sleeper(Retrier.Sleeper sleeper) {
      this.sleeper = sleeper;
      return this;
    }

    /**
     * @param configuration the Azure settings
     * @return this builder
     */
    public Builder configuration(Params.Configuration configuration) {
      this.configuration = configuration;
      return this;
    }

    /**
     * @param clock the clock
     * @return this builder
     */
    public Builder clock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * @param credential a credential to use instead of the service principal one
     * @return this builder
     */
    public Builder credential(TokenCredential credential) {
      this.credential = credential;
      return this;
    }

    /**
     * @return the context
     */
    public InfrastructureContext build() {
      return new InfrastructureContext(this);
    }
  }
}
