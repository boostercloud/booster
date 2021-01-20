import * as express from 'express'
import { GraphQLService, UserRegistry } from '@boostercloud/framework-provider-local'
import { AuthController } from './controllers/auth'
import { BoosterConfig, UserApp } from '@boostercloud/framework-types'
import * as path from 'path'
import { requestFailed } from './http'
import { GraphQLController } from './controllers/graphql'
import * as ExpressWS from 'express-ws'
import { useServer } from 'graphql-ws/lib/use/ws'
import { execute, subscribe } from 'graphql'

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

export const Infrastructure = {
  /**
   * `run` serves as the entry point for the local provider. It starts the required infrastructure
   * locally, which is running an `express` server.
   *
   * @param config The user's project config
   * @param port Port on which the express server will listen
   */
  start: (config: BoosterConfig, port: number): void => {
    const expressApp = express()
    const expressWSApp = ExpressWS(expressApp)
    const router = express.Router()
    const userProject: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))
    const userRegistry = new UserRegistry()
    const graphQLService = new GraphQLService(userProject)
    router.use('/auth', new AuthController(port, userRegistry, userProject).router)
    router.use('/graphql', new GraphQLController(graphQLService).router)
    expressApp.use(express.json())
    expressApp.use(router)
    expressApp.use(defaultErrorHandler)
    expressApp.listen(port)
    const wsServer = expressWSApp.getWss()
    useServer({ execute, subscribe, schema: userProject.boosterGetGraphQLSchema() }, wsServer)
  },
}

// function parseWebsocketMessage(rawMessage: string): GraphQLSocketMessage {
//   const parsed = JSON.parse(rawMessage)
//   if (!parsed['type'] || !parsed['payload']) {
//     throw new Error('Error parsing GraphQLSocketMessage. Got ' + rawMessage)
//   }
//   return parsed as GraphQLSocketMessage
// }
