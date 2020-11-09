import { fake } from 'sinon'
import { RocketDescriptor } from '@boostercloud/framework-types'
import { expect } from './expect'

const rewire = require('rewire')
const infrastructureRocket = rewire('../src/rockets/infrastructure-rocket')

const rocketDescriptor: RocketDescriptor = {
  packageName: 'some-package-name',
  parameters: { some: 'parameters' },
}

describe('loadRocket', () => {
  it('throws an error when the rocket package is not found', () => {
    infrastructureRocket.__set__('requireRocket', fake())

    expect(() => {
      infrastructureRocket.loadRocket(rocketDescriptor)
    }).to.throw(/Could not load the rocket package/)
  })

  it("throws an error when the package don't implement a builder method", () => {
    infrastructureRocket.__set__(
      'requireRocket',
      fake.returns({
        whatever: true,
      })
    )

    expect(() => {
      infrastructureRocket.loadRocket(rocketDescriptor)
    }).to.throw(/Could not initialize rocket package/)
  })

  it("throws an error when the package don't implement the 'InfrastructureRocket' interface", () => {
    infrastructureRocket.__set__(
      'requireRocket',
      fake.returns(() => ({
        whatever: true,
      }))
    )

    expect(() => {
      infrastructureRocket.loadRocket(rocketDescriptor)
    }).to.throw(/The package.*doesn't seem to implement the required interface/)
  })

  it('returns the loaded rocket properly initialized when it passes all checks', () => {
    infrastructureRocket.__set__(
      'requireRocket',
      fake.returns(() => ({ mountStack: fake() }))
    )

    const rocket = infrastructureRocket.loadRocket(rocketDescriptor)

    expect(rocket.mountStack).to.be.a('function')
  })
})
