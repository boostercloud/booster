import * as express from 'express'
import { UserRegistry } from '../services/user-registry'
import { UserApp, UserEnvelope, BoosterConfig, NotAuthorizedError } from '@boostercloud/framework-types'
import { UUID } from '@boostercloud/framework-types'

/**
 * This controller provides the sign up method, in order for the
 * user to be able to authenticate themselves, so they can interact
 * with the read and write API.
 */
export class AuthController {
  public router: express.Router = express.Router()

  constructor(readonly userRegistry: UserRegistry, readonly config: BoosterConfig, readonly userProject: UserApp) {
    this.router.post('/sign-up', async (req: express.Request, res: express.Response) => {
      await this.userRegistry.signUp(req.body)
      res.status(200)
    })

    this.router.post('/sign-in', async (req: express.Request, res: express.Response) => {
      try {
        const token = await this.userRegistry.signIn(req.body)
        res.status(200).json(token)
      } catch (e) {
        if (e.name == NotAuthorizedError.name) {
          res.status(403).json(e.message)
        }
        throw e
      }
    })

    this.router.post('/sign-out', async (req: express.Request, res: express.Response) => {
      const token = req.body?.accessToken
      if (token) {
        await this.userRegistry.signOut(token)
        res.status(200)
      } else {
        res.status(400).json('accessToken field not set')
      }
    })
  }
}
