import {
  CommandMetadata,
  DataMigrationMetadata,
  EntityInterface,
  EntityMetadata,
  EventHandlerInterface,
  EventMetadata,
  EventStreamConfiguration,
  GlobalErrorHandlerMetadata,
  NotificationMetadata,
  ProjectionMetadata,
  QueryMetadata,
  ReadModelInterface,
  ReadModelMetadata,
  ReducerMetadata,
  RoleMetadata,
  ScheduledCommandMetadata,
  SchemaMigrationMetadata,
  TokenVerifier,
} from './concepts'
import { ProviderLibrary } from './provider'
import { Level } from './logger'
import * as path from 'path'
import { RocketDescriptor, RocketFunction } from './rockets'
import { DEFAULT_SENSOR_HEALTH_BOOSTER_CONFIGURATIONS, HealthIndicatorMetadata, Logger, SensorConfiguration } from '.'
import { TraceConfiguration } from './instrumentation/trace-types'
import { Context } from 'effect'
import { AzureConfiguration, DEFAULT_CHUNK_SIZE } from './provider/azure-configuration'

/**
 * Configuration provider interface for external configuration sources
 */
export interface ConfigurationProvider {
  /**
   * Retrieve a configuration value by key
   * @param key The configuration key to retrieve
   * @returns Promise resolving to the configuration value or undefined if not found
   */
  getValue(key: string): Promise<string | undefined>

  /**
   * Check if the configuration provider is available and properly initialized
   * @returns Promise resolving to a true if available, false otherwise
   */
  isAvailable(): Promise<boolean>

  /**
   * Priority of this configuration provider (higher number = higher priority
   */
  readonly priority: number

  /**
   * Name identifier for this configuration provider
   */
  readonly name: string
}

/**
 * Configuration resolution result with source tracking
 */
export interface ConfigurationResolution {
  value: string | undefined
  source: string
  key: string
}

/**
 * Configuration resolver that manages multiple providers with fallback
 */
export interface ConfigurationResolver {
  /**
   * Resolve a configuration value from all available providers
   * @param key The configuration key to resolve
   * @returns Promise resolving to the configuration resolution result
   */
  resolve(key: string): Promise<ConfigurationResolution>

  /**
   * Add a configuration provider
   * @param provider The configuration provider to add
   */
  addProvider(provider: ConfigurationProvider): void

  /**
   * Get all registered providers sorted by priority
   */
  getProviders(): ConfigurationProvider[]
}

/**
 * Class used by external packages that needs to get a representation of
 * the booster config. Used mainly for vendor-specific deployment packages
 */
export class BoosterConfig {
  public logLevel: Level = Level.debug
  public logPrefix?: string
  public logger?: Logger

  private _provider?: ProviderLibrary
  public providerPackage?: string

  public rockets?: Array<RocketDescriptor>

  public appName = 'new-booster-app'

  public assets?: Array<string>

  public defaultResponseHeaders: Record<string, string> = {}

  public injectable?: unknown

  public readonly subscriptions = {
    maxConnectionDurationInSeconds: 7 * 24 * 60 * 60, // 7 days
    maxDurationInSeconds: 2 * 24 * 60 * 60, // 2 days
  }

  public enableGraphQLIntrospection = true

  private _userProjectRootPath?: string

  public readonly codeRelativePath: string = 'dist'

  public readonly eventDispatcherHandler: string = path.join(this.codeRelativePath, 'index.boosterEventDispatcher')
  public readonly eventStreamConsumer: string = path.join(this.codeRelativePath, 'index.boosterConsumeEventStream')
  public readonly eventStreamProducer: string = path.join(this.codeRelativePath, 'index.boosterProduceEventStream')
  public readonly serveGraphQLHandler: string = path.join(this.codeRelativePath, 'index.boosterServeGraphQL')
  public readonly sensorHealthHandler: string = path.join(this.codeRelativePath, 'index.boosterHealth')
  public readonly scheduledTaskHandler: string = path.join(
    this.codeRelativePath,
    'index.boosterTriggerScheduledCommand'
  )
  public readonly notifySubscribersHandler: string = path.join(this.codeRelativePath, 'index.boosterNotifySubscribers')
  public readonly rocketDispatcherHandler: string = path.join(this.codeRelativePath, 'index.boosterRocketDispatcher')

  public readonly functionRelativePath: string = path.join('..', this.codeRelativePath, 'index.js')

  public readonly events: Record<EventName, EventMetadata> = {}
  public readonly notifications: Record<EventName, NotificationMetadata> = {}
  public readonly partitionKeys: Record<EventName, string> = {}
  public readonly topicToEvent: Record<string, EventName> = {}
  public readonly eventToTopic: Record<EventName, string> = {}
  public readonly entities: Record<EntityName, EntityMetadata> = {}
  public readonly reducers: Record<EventName, ReducerMetadata> = {}
  public readonly commandHandlers: Record<CommandName, CommandMetadata> = {}
  public readonly queryHandlers: Record<QueryName, QueryMetadata> = {}
  public readonly eventHandlers: Record<EventName, Array<EventHandlerInterface>> = {}
  public readonly readModels: Record<ReadModelName, ReadModelMetadata> = {}
  public readonly projections: Record<EntityName, Array<ProjectionMetadata<EntityInterface, ReadModelInterface>>> = {}
  public readonly unProjections: Record<EntityName, Array<ProjectionMetadata<EntityInterface, ReadModelInterface>>> = {}
  public readonly readModelSequenceKeys: Record<EntityName, string> = {}
  public readonly roles: Record<RoleName, RoleMetadata> = {}
  public readonly schemaMigrations: Record<ConceptName, Map<Version, SchemaMigrationMetadata>> = {}
  public readonly scheduledCommandHandlers: Record<ScheduledCommandName, ScheduledCommandMetadata> = {}
  public readonly dataMigrationHandlers: Record<DataMigrationName, DataMigrationMetadata> = {}
  public userHealthIndicators: Record<string, HealthIndicatorMetadata> = {}
  public readonly sensorConfiguration: SensorConfiguration = {
    health: {
      globalAuthorizer: {
        authorize: 'all',
      },
      booster: DEFAULT_SENSOR_HEALTH_BOOSTER_CONFIGURATIONS,
    },
  }
  public readonly azureConfiguration: AzureConfiguration = {
    enableEventBatching: true, // enable batching by default
    cosmos: {
      batchSize: DEFAULT_CHUNK_SIZE,
    },
  }

  public globalErrorsHandler: GlobalErrorHandlerMetadata | undefined
  public enableSubscriptions = true
  public readonly nonExposedGraphQLMetadataKey: Record<string, Array<string>> = {}

  private rocketFunctionMap: Record<string, RocketFunction> = {}

  // TTL for events stored in dispatched events table. Default to 5 minutes (i.e., 300 seconds).
  public dispatchedEventsTtl = 300

  public registerRocketFunction(id: string, func: RocketFunction): void {
    const currentFunction = this.rocketFunctionMap[id]
    if (currentFunction) {
      throw new Error(
        `Error registering rocket function with id ${id}: There is already a rocket function registered under the same ID, "${currentFunction.name}"`
      )
    }
    this.rocketFunctionMap[id] = func
  }

  public getRegisteredRocketFunction(id: string): RocketFunction | undefined {
    return this.rocketFunctionMap[id]
  }

  public traceConfiguration: TraceConfiguration = {
    enableTraceNotification: false,
    includeInternal: false,
    onStart: async (): Promise<void> => {},
    onEnd: async (): Promise<void> => {},
  }

  public eventStreamConfiguration: EventStreamConfiguration = { enabled: false }

  /** Environment variables set at deployment time on the target lambda functions */
  public readonly env: Record<string, string> = {}

  /** Configuration providers for external configuration sources (Azure App Configuration, etc.) **/
  public readonly configurationProviders: ConfigurationProvider[] = []

  /**
   * Add `TokenVerifier` implementations to this array to enable token verification.
   * When a bearer token arrives in a request 'Authorization' header, it will be checked
   * against all the verifiers registered here.
   */
  public tokenVerifiers: Array<TokenVerifier> = []

  public constructor(public readonly environmentName: string) {}

  public get resourceNames(): ResourceNames {
    if (this.appName.length === 0) throw new Error('Application name cannot be empty')
    const applicationStackName = this.appName + '-app'
    return {
      applicationStack: applicationStackName,
      eventsStore: applicationStackName + '-events-store',
      dispatchedEventsStore: applicationStackName + '-dispatched-events',
      eventsDedup: applicationStackName + '-events-dedup',
      subscriptionsStore: applicationStackName + '-subscriptions-store',
      connectionsStore: applicationStackName + '-connections-store',
      streamTopic: this.eventStreamConfiguration.parameters?.streamTopic ?? 'booster_events',
      forReadModel(readModelName: string): string {
        return applicationStackName + '-' + readModelName
      },
    }
  }

  /**
   * Returns the name of the ReadModel from the name of its resouce (normally, a table)
   * @param resourceName
   */
  public readModelNameFromResourceName(resourceName: string): string {
    const resourceNamePrefixRegex = new RegExp(`^${this.resourceNames.applicationStack}-`)
    return resourceName.replace(resourceNamePrefixRegex, '')
  }

  /**
   * This is a convenience property to easily check if the application has defined any roles.
   * Only in that case we will create a user pool and an authorization API.
   * If there are no roles defined, it means that all app endpoints are public and users
   * won't be registered (they are all anonymous)
   */
  public get thereAreRoles(): boolean {
    return Object.entries(this.roles).length > 0
  }

  public currentVersionFor(className: string): number {
    const migrations = this.schemaMigrations[className]
    if (!migrations) {
      return 1
    }

    return Math.max(...migrations.keys())
  }

  public validate(): void {
    this.validateAllMigrations()
  }

  /**
   * Register a configuration provider for external configuration sources
   * @param provider The configuration provider to register
   */
  public addConfigurationProvider(provider: ConfigurationProvider): void {
    // Remove any existing provider with the same name
    const existingIndex = this.configurationProviders.findIndex((p) => p.name === provider.name)
    if (existingIndex >= 0) {
      this.configurationProviders.splice(existingIndex, 1)
    }

    // Add the new provider and sort by priority (highest first)
    this.configurationProviders.push(provider)
    this.configurationProviders.sort((a, b) => b.priority - a.priority)
  }

  /**
   * Enable Azure App Configuration for this environment
   * This is a convenience method that automatically configures the Azure App Configuration provider
   * @param options Configuration options for Azure App Configuration
   */
  public enableAzureAppConfiguration(options?: {
    connectionString?: string
    endpoint?: string
    labelFilter?: string
  }): void {
    // This method signature needs to remain in framework-types, but the actual implementation
    // will be provided by the Azure provider package to avoid circular dependencies
    // Store the options in a special property that the Azure provider can read
    ;(this as any)._azureAppConfigOptions = {
      connectionString: options?.connectionString,
      endpoint: options?.endpoint,
      labelFilter: options?.labelFilter,
      enabled: true,
    }
  }

  public get provider(): ProviderLibrary {
    if (!this._provider && this.providerPackage) {
      const rockets = this.rockets ?? []
      const provider = require(this.providerPackage)
      this._provider = provider.Provider(rockets)
    }
    if (!this._provider) throw new Error('It is required to set a valid provider runtime in your configuration files')
    return this._provider
  }

  public set provider(provider: ProviderLibrary) {
    console.warn(`
      The usage of the 'config.provider' field is deprecated,
      please use 'config.providerPackage' instead.

      For more information, check out the docs:

      https://docs.boosterframework.com/going-deeper/environment-configuration
    `)
    this._provider = provider
  }

  public get userProjectRootPath(): string {
    if (!this._userProjectRootPath)
      throw new Error('Property "userProjectRootPath" is not set. Ensure you have called "Booster.start"')
    return this._userProjectRootPath
  }

  public set userProjectRootPath(path: string) {
    this._userProjectRootPath = path
  }

  public mustGetEnvironmentVar(varName: string): string {
    const value = process.env[varName]
    if (value == undefined) {
      throw new Error(`Missing environment variable '${varName}'`)
    }
    return value
  }

  private validateAllMigrations(): void {
    for (const conceptName in this.schemaMigrations) {
      this.validateConceptSchemaMigrations(conceptName, this.schemaMigrations[conceptName])
    }
  }

  private validateConceptSchemaMigrations(conceptName: string, migrations: Map<number, SchemaMigrationMetadata>): void {
    // Check that migrations are defined consecutively. In other words, there are no gaps between the version numbers
    const currentVersion = this.currentVersionFor(conceptName)
    for (let toVersion = 2; toVersion <= currentVersion; toVersion++) {
      if (!migrations.has(toVersion)) {
        throw new Error(
          `Schema Migrations for '${conceptName}' are invalid: they are missing a migration with toVersion=${toVersion}. ` +
            `There must be a migration for '${conceptName}' for every version in the range [2..${currentVersion}]`
        )
      }
    }
  }

  public readonly eventStoreRetry: RetryConfig = {
    maxRetries: 3,
    initialDelay: 1000,
    maxDelay: 30000,
    backoffFactor: 2,
    jitterFactor: 0.1,
  }
}

export const BoosterConfigTag = Context.GenericTag<BoosterConfig>('BoosterConfig')

interface ResourceNames {
  applicationStack: string
  eventsStore: string
  dispatchedEventsStore: string
  eventsDedup: string
  subscriptionsStore: string
  connectionsStore: string
  streamTopic: string

  forReadModel(entityName: string): string
}

/**
 * Configuration for retrying event store operations
 * @interface
 */
export interface RetryConfig {
  /** Maximum number of retry attempts for event store operations */
  maxRetries: number

  /** Initial delay in milliseconds before the first retry */
  initialDelay: number

  /** Maximum delay in milliseconds between retries */
  maxDelay: number

  /** Multiplier for exponential backoff (each retry will wait initialDelay * (backoffFactor ^ attempt)) */
  backoffFactor: number

  /** Random jitter factor (0-1) to prevent thundering herd */
  jitterFactor: number

  /** Whether to retry all errors by default. If false, only errors in retryableErrors will be retried */
  retryAllErrors?: boolean

  /** List of error class names that should never be retried, regardless of other settings */
  nonRetryableErrors?: Array<string>

  /** List of error class names that should be retried when retryAllErrors is false */
  retryableErrors?: Array<string>
}

type EntityName = string
type EventName = string
type CommandName = string
type QueryName = string
type ReadModelName = string
type RoleName = string
type ConceptName = string
type Version = number
type ScheduledCommandName = string
type DataMigrationName = string
