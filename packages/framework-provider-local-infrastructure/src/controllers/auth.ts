import * as express from 'express'
import { UserRegistry } from '../services/user-registry'
import { NotAuthorizedError } from '@boostercloud/framework-types'

/**
 * This controller provides the sign up method, in order for the
 * user to be able to authenticate themselves, so they can interact
 * with the read and write API.
 */
export class AuthController {
  public router: express.Router = express.Router()

  constructor(readonly userRegistry: UserRegistry) {
    this.router.post('/sign-up', this.signUp.bind(this))
    this.router.post('/sign-in', this.signIn.bind(this))
    this.router.post('/sign-out', this.signOut.bind(this))
  }

  public async signUp(req: express.Request, res: express.Response): Promise<void> {
    if (req.body?.email && req.body?.roles && req.body?.password) {
      await this.userRegistry.signUp(req.body)
      res.status(200)
    } else {
      res.status(400).json('The request body should have the `email`, `password` and `roles` fields set')
    }
  }

  public async signIn(req: express.Request, res: express.Response): Promise<void> {
    try {
      if (req.body?.email && req.body?.roles) {
        const token = await this.userRegistry.signIn(req.body)
        res.status(200).json(token)
      } else {
        res.status(400).json('The request body should have the `email` and `roles` fields set')
      }
    } catch (e) {
      if (e.name == NotAuthorizedError.name) {
        res.status(403).json(e.message)
      }
      throw e
    }
  }

  public async signOut(req: express.Request, res: express.Response): Promise<void> {
    const token = req.body?.accessToken
    if (token) {
      await this.userRegistry.signOut(token)
      res.status(200)
    } else {
      res.status(400).json('accessToken field not set')
    }
  }
}
