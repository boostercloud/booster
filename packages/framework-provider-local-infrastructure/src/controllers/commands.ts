import * as express from 'express'
import { UserApp } from '@boostercloud/framework-types'
import { CommandResult } from '@boostercloud/framework-provider-local'
import { HttpCodes } from '../http'

export class CommandsController {
  public router: express.Router = express.Router()

  constructor(readonly userApp: UserApp) {
    this.router.post('/', this.handle.bind(this))
  }

  public async handle(req: express.Request, res: express.Response, next: express.NextFunction): Promise<void> {
    try {
      // We pass only headers and body to avoid flooding the user's terminal
      const result: CommandResult = await this.userApp.boosterCommandDispatcher({
        headers: req.headers,
        body: req.body,
      })
      switch (result.status) {
        case 'success':
          res.status(HttpCodes.Ok).json(result.result)
          break
        case 'failure':
          res.status(result.code).json({
            title: result.title,
            reason: result.reason,
          })
      }
    } catch (e) {
      next(e)
    }
  }
}
