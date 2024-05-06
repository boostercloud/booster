import { BoosterConfig } from '@boostercloud/framework-types'
import { SocketsFunctionDefinition } from '../types/functionDefinition'

export class WebsocketDisconnectFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): SocketsFunctionDefinition {
    return {
      name: 'disconnect',
      config: {
        bindings: [
          {
            type: 'webPubSubTrigger',
            direction: 'in',
            name: 'data',
            hub: 'booster',
            eventType: 'system',
            eventName: 'disconnect',
          },
        ],
        scriptFile: '../dist/index.js',
        entryPoint: 'boosterServeGraphQL',
      },
    }
  }
}
