/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from '../expect'
import { replace, restore, fake, match, spy } from 'sinon'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import * as CDK from 'aws-cdk'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { deploy } from '../../src/infrastructure/deploy'
import * as StackServiceConfiguration from '../../src/infrastructure/stack-service-configuration'
import { InfrastructureRocket } from '../../src/rockets/infrastructure-rocket'

const testEnvironment = {
  account: 'testAccount',
  region: 'testRegion',
}

describe('the deployment module', () => {
  beforeEach(() => {
    replace(CDK.SDK.prototype, 'defaultAccount', fake.returns(testEnvironment.account))
    replace(CDK.SDK.prototype, 'defaultRegion', fake.returns(testEnvironment.region))
  })

  afterEach(() => {
    restore()
  })

  describe('the `deploy` method', () => {
    it('logs progress through the passed logger', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })
      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await deploy(config, logger)

      expect(logger.info).to.have.been.called
    })

    it('builds the AppStack calling to the `getStackServiceConfiguration`', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })
      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())
      spy(StackServiceConfiguration, 'getStackServiceConfiguration')

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await deploy(config, logger)

      expect(StackServiceConfiguration.getStackServiceConfiguration).to.have.been.calledOnceWith(config)
    })

    it('calls the CDK bootstrap with the default environment parameters', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })

      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await deploy(config, logger)

      expect(fakeBootstrapEnvironment).to.have.been.calledOnce
      expect(fakeBootstrapEnvironment).to.be.calledWith(match(testEnvironment))
    })

    it('calls the CDK bootstrap with the environment parameters overridden by the configuration') //TODO

    it('calls the CDK bootstrap with the right config parameters', async () => {
      const config = new BoosterConfig('test')
      const testAppName = 'testing'
      config.appName = testAppName

      const fakeBootstrapEnvironment = fake.returns({ noOp: true })

      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await deploy(config, logger)

      const appNamePrefixRegExp = new RegExp('^' + testAppName + '-')
      expect(fakeBootstrapEnvironment).to.have.been.calledOnce
      expect(fakeBootstrapEnvironment).to.be.calledWith(
        match.any,
        match.any,
        match(appNamePrefixRegExp),
        match.any,
        match.has('bucketName', match(appNamePrefixRegExp))
      )
    })

    context('with rockets', () => {
      it('forwards the rockets to the `getStackServiceConfiguration` method for initialization', async () => {
        const config = new BoosterConfig('test')
        const fakeBootstrapEnvironment = fake.returns({ noOp: true })
        replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
        replace(CdkToolkit.prototype, 'deploy', fake())
        spy(StackServiceConfiguration, 'getStackServiceConfiguration')

        const logger = ({
          info: fake(),
        } as unknown) as Logger

        const fakeRocket: InfrastructureRocket = {
          mountStack: fake(),
          unmountStack: fake(),
        }

        await deploy(config, logger, [fakeRocket])

        expect(StackServiceConfiguration.getStackServiceConfiguration).to.have.been.calledOnceWith(config, [fakeRocket])
      })
    })
  })
})
