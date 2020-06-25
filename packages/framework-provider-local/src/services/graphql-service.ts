import { boosterServeGraphQL } from '@boostercloud/framework-core/dist'

export class GraphQLService {
  public constructor() {}

  public async query(request: any): Promise<any> {
    await boosterServeGraphQL(request)
    return {
      result: true,
    }
  }
}
