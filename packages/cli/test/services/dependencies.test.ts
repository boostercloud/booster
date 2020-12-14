import * as childProcessPromise from 'child-process-promise'
import { fake, replace, restore } from 'sinon'
import { installAllDependencies, pruneDevDependencies } from '../../src/services/dependencies'
import { expect } from '../expect'

describe('dependencies service', () => {
  afterEach(() => {
    restore()
  })

  describe('pruneDevDependencies', () => {
    it('installs dependencies in production mode', async () => {
      replace(childProcessPromise, 'exec', fake.resolves({}))

      await expect(pruneDevDependencies()).to.eventually.be.fulfilled

      expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn install --production --no-bin-links')
    })

    it('wraps the exec error', async () => {
      const error = new Error('something wrong happened')

      replace(childProcessPromise, 'exec', fake.rejects(error))

      await expect(pruneDevDependencies()).to.eventually.be.rejectedWith(/Could not prune dev dependencies/)
    })
  })

  describe('installAllDependencies', () => {
    context('without passing a path', () => {
      it('installs dependencies in dev mode in the current directory', async () => {
        replace(childProcessPromise, 'exec', fake.resolves({}))

        await expect(installAllDependencies()).to.eventually.be.fulfilled

        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn install', { cwd: process.cwd() })
      })
    })

    context('passing a path', () => {
      it('installs dependencies in dev mode in the passed path', async () => {
        replace(childProcessPromise, 'exec', fake.resolves({}))

        await expect(installAllDependencies('somewhere')).to.eventually.be.fulfilled

        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn install', { cwd: 'somewhere' })
      })
    })

    it('wraps the exec error', async () => {
      const error = new Error('something wrong happened')

      replace(childProcessPromise, 'exec', fake.rejects(error))

      await expect(installAllDependencies()).to.eventually.be.rejectedWith(/Could not install dependencies/)
    })
  })
})
