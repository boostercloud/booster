import * as express from 'express'
import { ReadModelEnvelope, UserApp } from '@boostercloud/framework-types'
import { ExpressWebSocketMessage } from '../library/web-socket-server-adapter'

export class GraphQLService {
  public constructor(readonly userApp: UserApp) {}

  public async handleGraphQLRequest(request: express.Request | ExpressWebSocketMessage): Promise<any> {
    return await this.userApp.boosterServeGraphQL(request)
  }

  public async handleNotificationSubscription(request: Array<ReadModelEnvelope>): Promise<unknown> {
    return await this.userApp.boosterNotifySubscribers(request)
  }
}
