import * as express from 'express'
import { GraphQLService } from '@boostercloud/framework-provider-local'
import {
  BoosterConfig,
  ProviderInfrastructure,
  RocketDescriptor,
  ScheduledCommandMetadata,
  UserApp,
} from '@boostercloud/framework-types'
import * as path from 'path'
import { requestFailed } from './http'
import { GraphQLController } from './controllers/graphql'
import * as cors from 'cors'
import { loadRocket } from './infrastructure-rocket'
import * as scheduler from 'node-schedule'

export * from './test-helper/local-test-helper'
export * from './infrastructure-rocket'

/**
 * Default error handling middleware. Instead of performing a try/catch in all endpoints
 * express will check if contents were sent, and if it failed, it will send a 500 with the
 * error attached.
 */
async function defaultErrorHandler(
  err: Error,
  _req: express.Request,
  res: express.Response,
  next: express.NextFunction
): Promise<void> {
  if (res.headersSent) {
    return next(err)
  }
  console.error(err)
  await requestFailed(err, res)
}

interface ScheduledCommandInfo {
  name: string
  metadata: ScheduledCommandMetadata
}

function configureScheduler(config: BoosterConfig): void {
  Object.keys(config.scheduledCommandHandlers)
    .map((scheduledCommandName) => buildScheduledCommandInfo(config, scheduledCommandName))
    .filter((scheduledCommandInfo) => scheduledCommandInfo.metadata.scheduledOn)
    .forEach((scheduledCommandInfo) => {
      scheduler.scheduleJob(scheduledCommandInfo.name, scheduledCommandInfo.metadata.scheduledOn, () => {
        console.log('AQUI QUE HACEMOS')
      })
    })
}

function buildScheduledCommandInfo(config: BoosterConfig, scheduledCommandName: string): ScheduledCommandInfo {
  return {
    name: scheduledCommandName,
    metadata: config.scheduledCommandHandlers[scheduledCommandName],
  }
}

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  const rockets = rocketDescriptors?.map(loadRocket)
  return {
    /**
     * `run` serves as the entry point for the local provider. It starts the required infrastructure
     * locally, which is running an `express` server.
     *
     * @param config The user's project config
     * @param port Port on which the express server will listen
     */
    start: async (config: BoosterConfig, port: number): Promise<void> => {
      const expressServer = express()
      const router = express.Router()
      const userProject: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))
      const graphQLService = new GraphQLService(userProject)
      router.use('/graphql', new GraphQLController(graphQLService).router)
      if (rockets && rockets.length > 0) {
        rockets.forEach((rocket) => {
          rocket.mountStack(config, router)
        })
      }
      expressServer.use(
        express.json({
          verify: (req, res, buf) => {
            req.rawBody = buf
          },
        })
      )
      expressServer.use(cors())
      expressServer.use(function (req, res, next) {
        res.header('Access-Control-Allow-Origin', '*')
        res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept')
        next()
      })
      expressServer.use(router)
      expressServer.use(defaultErrorHandler)
      expressServer.listen(port, () => {
        configureScheduler(config)
      })
    },
  }
}

declare module 'http' {
  export interface IncomingMessage {
    rawBody: Buffer
  }
}
