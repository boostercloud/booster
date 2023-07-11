import * as express from 'express'
import { HttpCodes, requestFailed } from '../http'
import { HealthService } from '@boostercloud/framework-provider-local'

export class HealthController {
  public router: express.Router = express.Router()
  constructor(readonly healthService: HealthService) {
    this.router.get('/*', this.handleHealth.bind(this))
  }

  public async handleHealth(req: express.Request, res: express.Response, next: express.NextFunction): Promise<void> {
    try {
      const response = await this.healthService.handleHealthRequest(req)
      if (response.status === 'success') {
        res.status(HttpCodes.Ok).json(response.result)
      } else {
        res.status(response.code).json({
          title: response.title,
          reason: response.message,
        })
      }
    } catch (e) {
      await requestFailed(e, res)
      next(e)
    }
  }
}
