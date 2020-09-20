import { fake } from 'sinon'
import { PluginDescriptor } from '@boostercloud/framework-types'
import { expect } from './expect'

const rewire = require('rewire')
const infrastructurePlugin = rewire('../src/infrastructure-plugin')

const pluginDescriptor: PluginDescriptor = {
  packageName: 'some-package-name',
  parameters: { some: 'parameters' },
}

describe('loadPlugin', () => {
  it('throws an error when the plugin package is not found', () => {
    infrastructurePlugin.__set__('requirePlugin', fake())

    expect(() => {
      infrastructurePlugin.loadPlugin(pluginDescriptor)
    }).to.throw(/Could not load the plugin package/)
  })

  it("throws an error when the package don't implement a builder method", () => {
    infrastructurePlugin.__set__(
      'requirePlugin',
      fake.returns({
        whatever: true,
      })
    )

    expect(() => {
      infrastructurePlugin.loadPlugin(pluginDescriptor)
    }).to.throw(/Could not initialize plugin package/)
  })

  it("throws an error when the package don't implement the 'InfrastructurePlugin' interface", () => {
    infrastructurePlugin.__set__(
      'requirePlugin',
      fake.returns(() => ({
        whatever: true,
      }))
    )

    expect(() => {
      infrastructurePlugin.loadPlugin(pluginDescriptor)
    }).to.throw(/The package.*doesn't seem to implement the required interface/)
  })

  it('returns the loaded plugin properly initialized when it passes all checks', () => {
    infrastructurePlugin.__set__(
      'requirePlugin',
      fake.returns(() => ({ mountStack: fake() }))
    )

    const plugin = infrastructurePlugin.loadPlugin(pluginDescriptor)

    expect(plugin.mountStack).to.be.a('function')
  })
})
