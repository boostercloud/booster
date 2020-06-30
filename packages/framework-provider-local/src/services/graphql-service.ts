import * as express from 'express'
import { UserApp } from '@boostercloud/framework-types'

export class GraphQLService {
  public constructor(readonly userApp: UserApp) {}

  public async handleGraphQLRequest(request: express.Request): Promise<any> {
    return await this.userApp.boosterServeGraphQL(request)
  }
}
