import * as express from 'express'
import { GraphQLService } from '@boostercloud/framework-provider-local'
import { BoosterConfig, ProviderInfrastructure, RocketDescriptor, UserApp } from '@boostercloud/framework-types'
import * as path from 'node:path'
import { requestFailed } from './http'
import { GraphQLController } from './controllers/graphql'
import * as cors from 'cors'
import { configureScheduler } from './scheduler'
import { RocketLoader } from '@boostercloud/framework-common-helpers'
import { InfrastructureRocket } from './infrastructure-rocket'

export * from './test-helper/local-test-helper'
export * from './infrastructure-rocket'

/**
 * Default error handling middleware. Instead of performing a try/catch in all endpoints
 * express will check if contents were sent, and if it failed, it will send a 500 with the
 * error attached.
 */
async function defaultErrorHandler(
  error: Error,
  _request: express.Request,
  response: express.Response,
  next: express.NextFunction
): Promise<void> {
  if (response.headersSent) {
    return next(error)
  }
  console.error(error)
  await requestFailed(error, response)
}

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  const rockets = rocketDescriptors?.map(RocketLoader.loadRocket) as InfrastructureRocket[]
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
      // TODO: Make this compatible with ES Modules
      // More info: https://github.com/sindresorhus/eslint-plugin-unicorn/blob/v43.0.2/docs/rules/prefer-module.md
      // eslint-disable-next-line unicorn/prefer-module
      const userProject = require(path.join(process.cwd(), 'dist', 'index.js'))
      const graphQLService = new GraphQLService(userProject as UserApp)
      router.use('/graphql', new GraphQLController(graphQLService).router)
      if (rockets && rockets.length > 0) {
        for (const rocket of rockets) {
          rocket.mountStack(config, router)
        }
      }
      expressServer.use(
        express.json({
          limit: '6mb',
          verify: (request, _, buf) => {
            request.rawBody = buf
          },
        })
      )
      expressServer.use(
        express.urlencoded({
          extended: true,
          limit: '6mb',
        })
      )
      expressServer.use(cors())
      expressServer.use(function (_request, response, next) {
        response.header('Access-Control-Allow-Origin', '*')
        response.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept')
        next()
      })
      expressServer.use(router)
      expressServer.use(defaultErrorHandler)
      expressServer.listen(port, () => {
        configureScheduler(config, userProject)
      })
    },
  }
}

declare module 'http' {
  export interface IncomingMessage {
    rawBody: Buffer
  }
}
