import { Effect, pipe } from 'effect'
import { FileSystem } from '@effect/platform/FileSystem'
import { BoosterConfig, BoosterConfigTag } from '@boostercloud/framework-types'
import * as path from 'path'
import { Options } from '@effect/cli'
import * as Nexus from '.'

export const options = {
  output: pipe(
    Options.file('output'),
    Options.withAlias('o'),
    Options.withDescription('The file where the config will be written'),
    Options.withDefault(path.join('.booster', 'infra-config.json'))
  ),
}

export const handler = Nexus.handler(options, (args) =>
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

export const command = Nexus.command('config', options, handler)

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
