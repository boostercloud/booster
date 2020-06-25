import { boosterServeGraphQL } from '@boostercloud/framework-core'

export class GraphQLService {
  public constructor() {}

  public async query(request: any): Promise<any> {
    return await boosterServeGraphQL(request)
  }
}
