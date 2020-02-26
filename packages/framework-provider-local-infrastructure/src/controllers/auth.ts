import * as express from 'express'
import { RuntimeStorage } from '../runtime-storage'
import { UserApp, UserEnvelope, BoosterConfig, NotAuthorizedError } from '@boostercloud/framework-types'
import { UUID } from '@boostercloud/framework-types'

/**
 * This controller provides the sign up method, in order for the
 * user to be able to authenticate themselves, so they can interact
 * with the read and write API.
 */
export class AuthController {
  public router: express.Router = express.Router()

  constructor(readonly storage: RuntimeStorage, readonly config: BoosterConfig, readonly userProject: UserApp) {
    this.router.post('/sign-up', async (req: express.Request, res: express.Response) => {
      await this.signUp(req.body)
      res.status(200)
    })

    this.router.post('/sign-in', async (req: express.Request, res: express.Response) => {
      try {
        const token = await this.signIn(req.body)
        res.status(200).json(token)
      } catch (e) {
        if (e.name == NotAuthorizedError.name) {
          res.status(403).json(e.message)
        }
        throw e
      }
    })
  }

  public async signUp(user: UserEnvelope): Promise<void> {
    await this.userProject.boosterPreSignUpChecker(user)
    await this.storage.registerUser(user)
  }

  public async signIn(user: UserEnvelope): Promise<UUID> {
    await this.userProject.boosterPreSignUpChecker(user)
    const token = UUID.generate()
    await this.storage.authenticateUser(token, user)
    return token
  }
}
