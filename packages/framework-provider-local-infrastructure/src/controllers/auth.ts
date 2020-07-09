import * as express from 'express'
import { UserRegistry } from '@boostercloud/framework-provider-local'
import { UserApp } from '@boostercloud/framework-types'
import { HttpCodes, requestFailed } from '../http'

/**
 * This controller provides the sign up method, in order for the
 * user to be able to authenticate themselves, so they can interact
 * with the read and write API.
 */
export class AuthController {
  public router: express.Router = express.Router()

  constructor(readonly port: number, readonly userRegistry: UserRegistry, readonly userProject: UserApp) {
    this.router.post('/sign-up', this.signUp.bind(this))
    this.router.post('/sign-in', this.signIn.bind(this))
    this.router.post('/sign-out', this.signOut.bind(this))
    this.router.get('/confirm/:email', this.confirmUser.bind(this))
  }

  public async signUp(req: express.Request, res: express.Response, next: express.NextFunction): Promise<void> {
    try {
      if (req.body?.username && req.body?.userAttributes && req.body?.password) {
        await this.userProject.boosterPreSignUpChecker(req.body)
        const user = await this.userRegistry.signUp(req.body)
        console.info(
          `To confirm the user, use the following link: http://localhost:${this.port}/auth/confirm/${user.username}`
        )
        res.status(HttpCodes.Ok).json('')
      } else {
        res
          .status(HttpCodes.BadRequest)
          .json('The request body should have the `username`, `password` and `userAttributes` fields set')
      }
    } catch (e) {
      await requestFailed(e, res)
      next(e)
    }
  }

  public async signIn(req: express.Request, res: express.Response, next: express.NextFunction): Promise<void> {
    try {
      if (req.body?.username && req.body?.password) {
        const token = await this.userRegistry.signIn(req.body)
        res.status(HttpCodes.Ok).json(token)
      } else {
        res.status(HttpCodes.BadRequest).json('The request body should have the `username` and `password` fields set')
      }
    } catch (e) {
      await requestFailed(e, res)
      next(e)
    }
  }

  public async signOut(req: express.Request, res: express.Response, next: express.NextFunction): Promise<void> {
    try {
      const token = req.body?.accessToken
      if (token) {
        await this.userRegistry.signOut(token)
        res.status(HttpCodes.Ok).json('')
      } else {
        res.status(HttpCodes.BadRequest).json('accessToken field not set')
      }
    } catch (e) {
      await requestFailed(e, res)
      next(e)
    }
  }

  public async confirmUser(req: express.Request, res: express.Response, next: express.NextFunction): Promise<void> {
    try {
      const email = req.params?.email
      if (email) {
        await this.userRegistry.confirmUser(email)
        res.status(HttpCodes.Ok).json('User confirmed!')
      } else {
        res.status(HttpCodes.BadRequest).json('GET params must include the email of the user')
      }
    } catch (e) {
      await requestFailed(e, res)
      next(e)
    }
  }
}
