import * as express from 'express'
import { UserApp } from '@boostercloud/framework-types'

export class CommandController {
  public router: express.Router = express.Router()

  constructor(readonly userApp: UserApp) {
    this.router.post('/', this.handle.bind(this))
  }

  public async handle(req: express.Request, res: express.Response, next: express.NextFunction): Promise<void> {
    try {
      await this.userApp.boosterCommandDispatcher(req)
      res.status(200).json()
    } catch (e) {
      next(e)
    }
  }
}
