import { Effect, pipe } from 'effect'
import { FileSystem } from '@effect/platform/FileSystem'
import { BoosterConfig, BoosterConfigTag } from '@boostercloud/framework-types'
import * as path from 'path'
import { Command, Options } from '@effect/cli'
import { Booster } from '..'
import { CliContext, CliError } from '@boostercloud/framework-types/dist/components'
// ---------------------------------------------------------------------------------------------

export const generateConfig = (): Command.Command<
  'config',
  CliContext,
  CliError,
  {
    readonly environment: string
    readonly output: string
    readonly format: 'json'
  }
> => {
  return Command.make(
    'config',
    {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      environment: pipe(
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        Options.choice('environment', Array.from(Booster.configuredEnvironments) as [string]),
        Options.withAlias('e'),
        Options.withDescription('The environment to use')
      ),
      output: pipe(
        Options.file('output'),
        Options.withAlias('o'),
        Options.withDescription('The file where the config will be written'),
        Options.withDefault(path.join('.booster', 'infra-config.json'))
      ),
      format: pipe(
        Options.choice('format', ['json']),
        Options.withAlias('f'),
        Options.withDescription('The format of the output file'),
        Options.withDefault('json')
      ),
    },
    (args) =>
      Effect.withSpan('cli/generate-config')(
        Effect.gen(function* (_) {
          const config = yield* _(BoosterConfigTag)
          const fs = yield* _(FileSystem)
          const configObject = extractWritableConfig(config)
          const renderedConfig = JSON.stringify(configObject, null, 2)
          const configFilePath = path.join(config.userProjectRootPath, args.output)
          yield* _(fs.writeFileString(configFilePath, renderedConfig))
        })
      )
  )
}

const extractWritableConfig = (config: BoosterConfig) => ({
  logLevel: config.logLevel,
  logPrefix: config.logPrefix,
  providerPackage: config.providerPackage,
  appName: config.appName,
  assets: config.assets,
  defaultResponseHeaders: config.defaultResponseHeaders,
  subscriptions: config.subscriptions,
  enableGraphQLIntrospection: config.enableGraphQLIntrospection,
  events: config.events,
  notifications: config.notifications,
  partitionKeys: config.partitionKeys,
  topicToEvent: config.topicToEvent,
  eventToTopic: config.eventToTopic,
  entities: config.entities,
  reducers: config.reducers,
  commandHandlers: config.commandHandlers,
  queryHandlers: config.queryHandlers,
  eventHandlers: config.eventHandlers,
  readModels: config.readModels,
  projections: config.projections,
  readModelSequenceKeys: config.readModelSequenceKeys,
  roles: config.roles,
  schemaMigrations: config.schemaMigrations,
  dataMigrationHandlers: config.dataMigrationHandlers,
  userHealthIndicators: config.userHealthIndicators,
  sensorConfiguration: config.sensorConfiguration,
  enableSubscriptions: config.enableSubscriptions,
  resourceNames: config.resourceNames,
})
