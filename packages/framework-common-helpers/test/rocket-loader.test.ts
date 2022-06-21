/* eslint-disable @typescript-eslint/no-explicit-any */
import { fake, replace, restore } from 'sinon'
import { RocketDescriptor } from '@boostercloud/framework-types'
import { expect } from './helpers/expect'
import { RocketLoader } from '../src/rocket-loader'

const rocketDescriptor: RocketDescriptor = {
  packageName: 'some-package-name',
  parameters: { some: 'parameters' },
}

describe('RocketLoader', () => {
  afterEach(() => {
    restore()
  })

  describe('loadRocket', () => {
    it('throws an error when the rocket infrastructure package is not found', () => {
      replace(RocketLoader as any, 'requireRocket', fake())

      expect(() => {
        RocketLoader.loadRocket(rocketDescriptor)
      }).to.throw(/Could not load the rocket infrastructure package/)
    })

    it("throws an error when the package don't implement a builder method", () => {
      replace(
        RocketLoader as any,
        'requireRocket',
        fake.returns({
          whatever: true,
        })
      )

      expect(() => {
        RocketLoader.loadRocket(rocketDescriptor)
      }).to.throw(/Could not initialize rocket infrastructure package/)
    })

    it("throws an error when the package don't implement the 'InfrastructureRocket' interface", () => {
      replace(
        RocketLoader as any,
        'requireRocket',
        fake.returns(() => {
          return {
            whatever: true,
          }
        })
      )

      expect(() => {
        RocketLoader.loadRocket(rocketDescriptor)
      }).to.throw(/The package.*doesn't seem to be a rocket/)
    })

    it('returns the loaded rocket properly initialized when it passes all checks', () => {
      replace(
        RocketLoader as any,
        'requireRocket',
        fake.returns(() => ({ mountStack: fake() }))
      )

      const rocket = RocketLoader.loadRocket(rocketDescriptor)

      expect(rocket.mountStack).to.be.a('function')
    })
  })
})
