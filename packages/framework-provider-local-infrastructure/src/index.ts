import * as express from 'express'
import { UserRegistry } from '@boostercloud/framework-provider-local'
import { AuthController } from './controllers/auth'
import { BoosterConfig } from '@boostercloud/framework-types'
import path = require('path')
import { CommandsController } from './controllers/commands'
import { HttpCodes } from './http'

/**
 * `deploy` serves as the entry point for the local provider. Even though
 * it doesn't deploy anything, it does start the required infrastructure
 * locally, which is running an `express` server.
 *
 * @param port Port on which the express server will listen
 */
export function run(config: BoosterConfig, port: number): void {
  const expressServer = express()
  const router = express.Router()
  const userProject = require(path.join(process.cwd(), 'dist', 'index.js'))
  const userRegistry = new UserRegistry()
  router.use('/auth', new AuthController(port, userRegistry, userProject).router)
  router.use('/commands', new CommandsController(userProject).router)
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
export function defaultErrorHandler(
  err: Error,
  _req: express.Request,
  res: express.Response,
  next: express.NextFunction
): void {
  if (res.headersSent) {
    return next(err)
  }
  console.error(err)
  res.status(HttpCodes.InternalError).json(err)
}
