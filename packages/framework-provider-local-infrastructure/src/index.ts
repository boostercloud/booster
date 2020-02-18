import * as express from 'express'
import { RuntimeStorage } from './runtime-storage'
import { AuthController } from './controllers/auth'
import { BoosterConfig } from '@boostercloud/framework-types'

/**
 * `deploy` serves as the entry point for the local provider. Even though
 * it doesn't deploy anything, it does start the required infrastructure
 * locally, which is running an `express` server.
 *
 * @param storage Instance of a `Storage` object, in order to store the data
 * @param port Port on which the express server will listen
 */
export function run(storage: RuntimeStorage, config: BoosterConfig, port: number): void {
  const expressServer = express()
  const router = express.Router()
  router.use('/', new AuthController(storage, config).router)
  expressServer.use(defaultErrorHandler)
  expressServer.use(express.json())
  expressServer.use(router)
  expressServer.listen(port)
}

/**
 * Default error handling middleware. Instead of performing a try/catch in all endpoints
 * express will check if contents were sent, and if it failed, it will send a 500 with the
 * error attached.
 */
function defaultErrorHandler(
  err: Error,
  _req: express.Request,
  res: express.Response,
  next: express.NextFunction
): void {
  if (res.headersSent) {
    return next(err)
  }
  console.error(err.message)
  res.status(500).json(err)
}
