import * as express from 'express'
import { GraphQLService } from '@boostercloud/framework-provider-local'
import { BoosterConfig, UserApp } from '@boostercloud/framework-types'
import * as path from 'path'
import { requestFailed } from './http'
import { GraphQLController } from './controllers/graphql'
import * as cors from 'cors'

export * from './test-helper/local-test-helper'

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
    const expressServer = express()
    const router = express.Router()
    const userProject: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))
    const graphQLService = new GraphQLService(userProject)
    router.use('/graphql', new GraphQLController(graphQLService).router)
    expressServer.use(express.json())
    expressServer.use(cors())
    expressServer.use(function (req, res, next) {
      res.header('Access-Control-Allow-Origin', '127.0.0.1')
      res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept')
      next()
    })
    expressServer.use(router)
    expressServer.use(defaultErrorHandler)
    expressServer.listen(port)
  },
}
