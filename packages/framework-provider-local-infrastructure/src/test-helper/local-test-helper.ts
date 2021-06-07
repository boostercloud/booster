import { LocalQueries } from './local-queries'
import { LocalCounters } from './local-counters'

interface ApplicationOutputs {
    graphqlURL: string
    websocketURL: string
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
          },
          new LocalCounters(`${appName}-app`),
          new LocalQueries()
        )
      }
    
      private static ensureProviderIsReady(): void {
        if (false) {
          throw new Error(`The local provider is not ready.`)
        }
      }         
    
      private static async graphqlURL(): Promise<string> {
        const url = "http://localhost:3000/graphql"
        return url
      }
    
      private static async websocketURL(): Promise<string> {
        const url = "TODO"
        return url
      }
}