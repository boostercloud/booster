import * as express from 'express'
import { UserApp } from '@boostercloud/framework-types'

export class HealthService {
  public constructor(readonly userApp: UserApp) {}

  public async handleHealthRequest(request: express.Request): Promise<any> {
    return await this.userApp.boosterHealth(request)
  }
}
