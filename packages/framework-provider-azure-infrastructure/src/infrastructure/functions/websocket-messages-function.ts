import { BoosterConfig } from '@boostercloud/framework-types'
import { SocketsFunctionDefinition } from '../types/functionDefinition'

export class WebsocketMessagesFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): SocketsFunctionDefinition {
    return {
      name: 'messages',
      config: {
        bindings: [
          {
            type: 'webPubSubTrigger',
            direction: 'in',
            name: 'data',
            hub: 'booster',
            eventType: 'user',
            eventName: 'message',
          },
        ],
        scriptFile: '../dist/index.js',
        entryPoint: 'boosterServeGraphQL',
      },
    }
  }
}
