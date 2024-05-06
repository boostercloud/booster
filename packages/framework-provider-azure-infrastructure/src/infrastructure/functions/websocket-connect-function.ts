import { BoosterConfig } from '@boostercloud/framework-types'
import { SocketsFunctionDefinition } from '../types/functionDefinition'

export class WebsocketConnectFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): SocketsFunctionDefinition {
    return {
      name: 'connect',
      config: {
        bindings: [
          {
            type: 'webPubSubTrigger',
            direction: 'in',
            name: 'data',
            hub: 'booster',
            eventType: 'system',
            eventName: 'connect',
          },
        ],
        scriptFile: '../dist/index.js',
        entryPoint: 'boosterServeGraphQL',
      },
    }
  }
}
