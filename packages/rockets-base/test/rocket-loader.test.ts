import { fake } from 'sinon'
import { RocketDescriptor } from '@boostercloud/framework-types'
import { expect } from '../../framework-provider-aws-infrastructure/test/expect'
import { AWSProviderContext } from '../../framework-provider-aws-infrastructure/src/infrastructure/provider-context/aws-provider-context'

const rewire = require('rewire')
const rocketsBase = rewire('@boostercloud/rockets-base')

const rocketDescriptor: RocketDescriptor<AWSProviderContext> = {
  context: {} as AWSProviderContext,
  packageName: 'some-package-name',
  parameters: { some: 'parameters' },
}

describe('loadRocket', () => {
  it('throws an error when the rocket package is not found', () => {
    rocketsBase.__set__('requireRocket', fake())

    expect(() => {
      rocketsBase.loadRocket(rocketDescriptor)
    }).to.throw(/Could not load the rocket package/)
  })

  it("throws an error when the package don't implement a builder method", () => {
    rocketsBase.__set__(
      'requireRocket',
      fake.returns({
        whatever: true,
      })
    )

    expect(() => {
      rocketsBase.loadRocket(rocketDescriptor)
    }).to.throw(/Could not initialize rocket package/)
  })

  it("throws an error when the package don't implement the 'InfrastructureRocket' interface", () => {
    rocketsBase.__set__(
      'requireRocket',
      fake.returns(() => ({
        whatever: true,
      }))
    )

    expect(() => {
      rocketsBase.loadRocket(rocketDescriptor)
    }).to.throw(/The package.*doesn't seem to implement the required interface/)
  })

  it('returns the loaded rocket properly initialized when it passes all checks', () => {
    rocketsBase.__set__(
      'requireRocket',
      fake.returns(() => ({ mount: fake() }))
    )

    const rocket = rocketsBase.loadRocket(rocketDescriptor)

    expect(rocket.mount).to.be.a('function')
  })
})
