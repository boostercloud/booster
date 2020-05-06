import * as express from 'express'
import { UserRegistry } from '@boostercloud/framework-provider-local'
import { AuthController } from './controllers/auth'
import { BoosterConfig } from '@boostercloud/framework-types'
import path = require('path')
import { requestFailed } from './http'

/**
 * `run` serves as the entry point for the local provider. It starts the required infrastructure
 * locally, which is running an `express` server.
 *
 * @param config The user's project config
 * @param port Port on which the express server will listen
 */
export function run(config: BoosterConfig, port: number): void {
  const expressServer = express()
  const router = express.Router()
  const userProject = require(path.join(process.cwd(), 'dist', 'index.js'))
  const userRegistry = new UserRegistry()
  router.use('/auth', new AuthController(port, userRegistry, userProject).router)
  expressServer.use(express.json())
  expressServer.use(router)
  expressServer.use(defaultErrorHandler)
  expressServer.listen(port)
}

/**
 * Default error handling middleware. Instead of performing a try/catch in all endpoints
 * express will check if contents were sent, and if it failed, it will send a 500 with the
 * error attached.
 */
export async function defaultErrorHandler(
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
