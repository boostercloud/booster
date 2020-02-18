import * as express from 'express'
import { RuntimeStorage } from '../runtime-storage'
import { UserEnvelope, BoosterConfig } from '@boostercloud/framework-types'

export class AuthController {
  public router: express.Router = express.Router()

  constructor(readonly storage: RuntimeStorage, readonly config: BoosterConfig) {
    this.router.post('/sign-up', this.signUp.bind(this))
  }

  private async signUp(req: express.Request, res: express.Response): Promise<void> {
    const user: UserEnvelope = req.body
    // await this.config.provider.preSignUpHandlerReference(req.body) // TODO
    this.storage.registeredUsers[user.email] = user
    res.status(200).json()
  }
}
