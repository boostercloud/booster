import { restore, SinonStub, stub } from 'sinon'
import * as projectChecker from '../../src/services/project-checker'
import * as executorService from '../../src/services/executor-service'
import { compileProjectAndLoadConfig } from '../../src/services/config-service'
import { BoosterApp, BoosterConfig } from '@boostercloud/framework-types'
import { expect } from '../expect'
import sinon = require('sinon')

describe('configService', () => {
  beforeEach(() => {
    restore()
  })

  const Module = require('module')

  class BoosterMock {
    constructor(public readonly config: BoosterConfig) {}
    public configureCurrentEnv(configurator: (config: BoosterConfig) => void): void {
      configurator(this.config)
    }
  }

  class BoosterAppMock {
    constructor(public readonly config: BoosterConfig) {}
    // @ts-ignore
    Booster: BoosterApp = new BoosterMock(this.config)
  }

  describe('compileProjectAndLoadConfig', () => {
    let checkItIsABoosterProject: SinonStub
    let withinWorkingDirectory: SinonStub

    beforeEach(() => {
      checkItIsABoosterProject = stub(projectChecker, 'checkItIsABoosterProject').resolves()
      withinWorkingDirectory = stub(executorService, 'withinWorkingDirectory').resolves()
    })

    it('loads the config when the selected environment exists', async () => {
      const config = new BoosterConfig('test')
      config.addConfiguredEnvironment('test')
      Module.prototype.require = function() {
        return new BoosterAppMock(config)
      }

      await expect(compileProjectAndLoadConfig()).to.eventually.become(config)
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly()
      expect(withinWorkingDirectory).to.have.been.calledOnceWithExactly(sinon.match.string, sinon.match.func)
    })

    it('throws the right error when there are not configured environments', async () => {
      const config = new BoosterConfig('test')
      Module.prototype.require = function() {
        return new BoosterAppMock(config)
      }

      await expect(compileProjectAndLoadConfig()).to.eventually.be.rejectedWith(
        /You haven't configured any environment/
      )
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly()
      expect(withinWorkingDirectory).to.have.been.calledOnceWithExactly(sinon.match.string, sinon.match.func)
    })

    it('throws the right error when the environment does not exist', async () => {
      const config = new BoosterConfig('test')
      config.addConfiguredEnvironment('stage')
      Module.prototype.require = function() {
        return new BoosterAppMock(config)
      }

      await expect(compileProjectAndLoadConfig()).to.eventually.be.rejectedWith(
        /The environment 'test' does not match any of the environments/
      )
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly()
      expect(withinWorkingDirectory).to.have.been.calledOnceWithExactly(sinon.match.string, sinon.match.func)
    })
  })
})
