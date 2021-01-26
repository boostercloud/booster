/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from '../expect'
import { replace, restore, fake, mock, match } from 'sinon'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { deploy } from '../../src/infrastructure/deploy'
import { InfrastructureRocket } from '../../src/rockets/infrastructure-rocket'
import { EnvironmentUtils } from '@aws-cdk/cx-api'
import { SdkProvider } from 'aws-cdk'
import * as StackTools from '../../src/infrastructure/stack-tools'

const testEnvironment = {
  account: 'testAccount',
  region: 'testRegion',
}
const config = new BoosterConfig('test')
config.userProjectRootPath = '.'

describe('the deployment module', () => {
  beforeEach(() => {
    replace(SdkProvider.prototype, 'forEnvironment', fake.returns(mock()))
  })

  afterEach(() => {
    restore()
  })

  describe('the `deploy` method', () => {
    it('logs progress through the passed logger', async () => {
      replace(
        StackTools,
        'getStackServiceConfiguration',
        fake.returns({
          environment: testEnvironment,
          cdkToolkit: {
            bootstrap: fake(),
            deploy: fake(),
          },
        })
      )
      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await deploy(config, logger)

      expect(logger.info).to.have.been.called
    })

    it('throws errors', async () => {
      const errorMessage = 'Testing error'

      replace(
        StackTools,
        'getStackServiceConfiguration',
        fake.returns({
          environment: testEnvironment,
          cdkToolkit: {
            bootstrap: fake(),
            deploy: fake.throws(errorMessage),
          },
        })
      )

      const logger = ({
        info: fake(),
        error: fake(),
      } as unknown) as Logger

      await expect(deploy(config, logger)).to.eventually.be.rejectedWith(errorMessage)
    })

    it('builds the AppStack calling to the `getStackServiceConfiguration`', async () => {
      replace(
        StackTools,
        'getStackServiceConfiguration',
        fake.returns({
          environment: testEnvironment,
          cdkToolkit: {
            bootstrap: fake(),
            deploy: fake(),
          },
        })
      )

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await deploy(config, logger)

      expect(StackTools.getStackServiceConfiguration).to.have.been.calledOnceWith(config)
    })

    it('calls the CDK bootstrap with the default environment parameters', async () => {
      const fakeBootstrap = fake()

      replace(
        StackTools,
        'getStackServiceConfiguration',
        fake.returns({
          environment: testEnvironment,
          cdkToolkit: {
            bootstrap: fakeBootstrap,
            deploy: fake(),
          },
        })
      )

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await deploy(config, logger)

      expect(fakeBootstrap).to.be.calledOnceWith(
        match([EnvironmentUtils.format(testEnvironment.account, testEnvironment.region)])
      )
    })

    it('calls the CDK bootstrap with the right config parameters', async () => {
      const testAppName = 'testing'
      config.appName = testAppName
      const fakeBootstrap = fake()

      replace(
        StackTools,
        'getStackServiceConfiguration',
        fake.returns({
          environment: testEnvironment,
          cdkToolkit: {
            bootstrap: fakeBootstrap,
            deploy: fake(),
          },
        })
      )

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await deploy(config, logger)

      const appNamePrefixRegExp = new RegExp('^' + testAppName + '-')
      expect(fakeBootstrap).to.have.been.calledOnce
      expect(fakeBootstrap).to.be.calledWith(
        match.any,
        match.any,
        match({
          toolkitStackName: match(appNamePrefixRegExp),
          parameters: {
            bucketName: match(appNamePrefixRegExp),
          },
        })
      )
    })

    context('with rockets', () => {
      it('forwards the rockets to the `getStackServiceConfiguration` method for initialization', async () => {
        replace(
          StackTools,
          'getStackServiceConfiguration',
          fake.returns({
            environment: testEnvironment,
            cdkToolkit: {
              bootstrap: fake(),
              deploy: fake(),
            },
          })
        )

        const logger = ({
          info: fake(),
        } as unknown) as Logger

        const fakeRocket: InfrastructureRocket = {
          mountStack: fake(),
          unmountStack: fake(),
        }

        await deploy(config, logger, [fakeRocket])

        expect(StackTools.getStackServiceConfiguration).to.have.been.calledOnceWith(config, [fakeRocket])
      })
    })
  })
})
