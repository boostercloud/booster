import * as express from 'express'
import { HttpCodes, requestFailed } from '../http'
import { GraphQLService } from '@boostercloud/framework-provider-local'

export class GraphQLController {
  public router: express.Router = express.Router()
  constructor(readonly graphQLService: GraphQLService) {
    this.router.post('/', this.handleGraphQL.bind(this))
  }

  public async handleGraphQL(req: express.Request, res: express.Response, next: express.NextFunction): Promise<void> {
    try {
      const response = await this.graphQLService.handleGraphQLRequest(req)
      res.status(HttpCodes.Ok).json(response.result)
    } catch (e) {
      await requestFailed(e, res)
      next(e)
    }
  }
}
