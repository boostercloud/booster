import { BoosterConfig } from '@boostercloud/framework-types'
import { describe } from 'mocha'
import { GraphqlFunction } from '../../../src/infrastructure/functions/graphql-function'
import { expect } from '../../expect'
import * as path from 'path'

describe('Creating graphql-functions', () => {
  const config = new BoosterConfig('test')
  config.resourceNames.applicationStack = 'applicationStack'
  config.resourceNames.eventsStore = 'eventsStore'

  it('create the expected GraphQLFunctionDefiniton', () => {
    const definition = new GraphqlFunction(config).getFunctionDefinition()
    expect(definition).not.to.be.null
    expect(definition.name).to.be.equal('graphql')
    expect(definition.config.bindings[0].type).to.be.equal('httpTrigger')
    expect(definition.config.bindings[0].name).to.be.equal('rawRequest')
    expect(definition.config.bindings[0].direction).to.be.equal('in')
    expect(definition.config.bindings[0].authLevel).to.be.equal('anonymous')
    expect(definition.config.bindings[0]?.methods?.length).to.be.equal(1)
    expect(definition.config.bindings[0].methods?.pop()).to.be.equal('post')
    expect(definition.config.bindings[1].type).to.be.equal('http')
    expect(definition.config.bindings[1].name).to.be.equal('$return')
    expect(definition.config.bindings[1].direction).to.be.equal('out')
    expect(definition.config.scriptFile).to.be.equal(path.join('../dist/index.js'))
    expect(definition.config.entryPoint).to.be.equal('boosterServeGraphQL')
  })
})
