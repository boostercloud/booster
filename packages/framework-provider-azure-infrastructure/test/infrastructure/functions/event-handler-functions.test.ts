import { BoosterConfig } from '@boostercloud/framework-types'
import { describe } from 'mocha'
import { EventHandlerFunction } from '../../../src/infrastructure/functions/event-handler-function'
import { expect } from '../../expect'

describe('Creating event-handler-functions', () => {
  const config = new BoosterConfig('test')
  config.resourceNames.applicationStack = 'applicationStack'
  config.resourceNames.eventsStore = 'eventsStore'

  it('create the expected EventHandlerFunctionDefinition', () => {
    const definition = new EventHandlerFunction(config).getFunctionDefinition()
    expect(definition).not.to.be.null
    expect(definition.name).to.be.equal('eventHandler')
    expect(definition.config.bindings[0].type).to.be.equal('cosmosDBTrigger')
    expect(definition.config.bindings[0].name).to.be.equal('rawEvent')
    expect(definition.config.bindings[0].direction).to.be.equal('in')
    expect(definition.config.bindings[0].leaseContainerName).to.be.equal('leases')
    expect(definition.config.bindings[0].connection).to.be.equal('COSMOSDB_CONNECTION_STRING')
    expect(definition.config.bindings[0].databaseName).to.be.equal('new-booster-app-app')
    expect(definition.config.bindings[0].containerName).to.be.equal('new-booster-app-app-events-store')
    expect(definition.config.bindings[0].createLeaseContainerIfNotExists).to.be.equal('true')
    expect(definition.config.scriptFile).to.be.equal('../dist/index.js')
    expect(definition.config.entryPoint).to.be.equal('boosterEventDispatcher')
  })
})
