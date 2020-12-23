import * as childProcessPromise from 'child-process-promise'
import { fake, replace, restore } from 'sinon'
import { installDependencies } from '../../src/services/dependencies'
import { expect } from '../expect'

describe('dependencies service', () => {
  afterEach(() => {
    restore()
  })

  describe('installDependencies', () => {
    context('without passing a path', () => {
      it('installs dependencies in dev mode in the current directory', async () => {
        replace(childProcessPromise, 'exec', fake.resolves({}))

        await expect(installDependencies()).to.eventually.be.fulfilled

        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn install', { cwd: process.cwd() })
      })
    })

    context('passing a path', () => {
      it('installs dependencies in dev mode in the passed path', async () => {
        replace(childProcessPromise, 'exec', fake.resolves({}))

        await expect(installDependencies('somewhere')).to.eventually.be.fulfilled

        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn install', { cwd: 'somewhere' })
      })
    })

    it('wraps the exec error', async () => {
      const error = new Error('something wrong happened')

      replace(childProcessPromise, 'exec', fake.rejects(error))

      await expect(installDependencies()).to.eventually.be.rejectedWith(/Could not install dependencies/)
    })
  })
})
