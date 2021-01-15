import * as express from 'express'
import { GraphQLSocketMessage, GraphQLService, UserRegistry } from '@boostercloud/framework-provider-local'
import { AuthController } from './controllers/auth'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import * as path from 'path'
import { requestFailed } from './http'
import { GraphQLController } from './controllers/graphql'
import { UserApp } from '@boostercloud/framework-types'
import * as ExpressWs from 'express-ws'

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
    const expressWsApp = ExpressWs(expressApp)
    const router = express.Router()
    const userProject: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))
    const userRegistry = new UserRegistry()
    const graphQLService = new GraphQLService(userProject)
    router.use('/auth', new AuthController(port, userRegistry, userProject).router)
    router.use('/graphql', new GraphQLController(graphQLService).router)
    expressApp.use(express.json())
    expressApp.use(router)
    expressApp.use(defaultErrorHandler)
    expressWsApp.app.ws('/graphql', function(ws) {
      const connectionId = UUID.generate().toString()
      ws.on('message', async (msg) => {
        const request = parseWebsocketMessage(msg.toString())
        request.connectionId = connectionId
        console.log('MESSAGE =====')
        console.log(request)
        await userProject.boosterServeGraphQL(request)
      })
    })
    expressApp.listen(port)
  },
}

function parseWebsocketMessage(rawMessage: string): GraphQLSocketMessage {
  const parsed = JSON.parse(rawMessage)
  if (!parsed['type'] || !parsed['payload']) {
    throw new Error('Error parsing GraphQLSocketMessage. Got ' + rawMessage)
  }
  return parsed as GraphQLSocketMessage
}
