import { LocalQueries } from './local-queries'
import { LocalCounters } from './local-counters'

interface ApplicationOutputs {
  graphqlURL: string
  websocketURL: string
  healthURL: string
}

export class LocalTestHelper {
  private constructor(
    readonly outputs: ApplicationOutputs,
    readonly counters: LocalCounters,
    readonly queries: LocalQueries
  ) {}

  public static async build(appName: string): Promise<LocalTestHelper> {
    this.ensureProviderIsReady()
    return new LocalTestHelper(
      {
        graphqlURL: await this.graphqlURL(),
        websocketURL: await this.websocketURL(),
        healthURL: await this.healthURL(),
      },
      new LocalCounters(`${appName}-app`),
      new LocalQueries()
    )
  }

  private static ensureProviderIsReady(): void {
    //TODO check provider is ready making a HTTP request or a GraphQL query
  }

  private static async graphqlURL(): Promise<string> {
    const url = 'http://localhost:3000/graphql'
    return url
  }

  private static async healthURL(): Promise<string> {
    return 'http://localhost:3000/sensor/health/'
  }

  private static async websocketURL(): Promise<string> {
    const url = 'ws://localhost:65529/websocket'
    return url
  }
}
