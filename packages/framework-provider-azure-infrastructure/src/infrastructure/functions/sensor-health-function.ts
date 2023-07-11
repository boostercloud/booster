import { BoosterConfig } from '@boostercloud/framework-types'
import { HttpFunctionDefinition } from '../types/functionDefinition'

export class SensorHealthFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): HttpFunctionDefinition {
    return {
      name: 'sensor-health',
      config: {
        bindings: [
          {
            authLevel: 'anonymous',
            type: 'httpTrigger',
            direction: 'in',
            name: 'rawRequest',
            methods: ['get'],
            route: 'sensor/health/{*url}',
          },
          {
            type: 'http',
            direction: 'out',
            name: '$return',
          },
        ],
        scriptFile: this.config.functionRelativePath,
        entryPoint: this.config.sensorHealthHandler.split('.')[1],
      },
    }
  }
}
