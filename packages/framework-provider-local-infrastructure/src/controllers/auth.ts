import * as express from 'express'
import { RuntimeStorage } from '../runtime-storage'
import { UserApp, UserEnvelope, BoosterConfig } from '@boostercloud/framework-types'

export class AuthController {
  public router: express.Router = express.Router()

  constructor(readonly storage: RuntimeStorage, readonly config: BoosterConfig, readonly userProject: UserApp) {
    this.router.post('/sign-up', async (req: express.Request, res: express.Response) => {
      await this.signUp(req.body)
      res.status(200)
    })
  }

  public async signUp(user: UserEnvelope): Promise<void> {
    await this.userProject.boosterPreSignUpChecker(user)
    this.storage.registeredUsers[user.email] = user
  }
}
