import * as childProcessPromise from 'child-process-promise'
import { fake, replace, restore } from 'sinon'
import { installAllDependencies, installProductionDependencies } from '../../src/services/dependencies'
import { expect } from '../expect'

describe('dependencies service', () => {
  afterEach(() => {
    restore()
  })

  describe('installProductionDependencies', () => {
    it('installs dependencies in production mode', async () => {
      replace(childProcessPromise, 'exec', fake.resolves({}))
      const path = 'aPath'

      await expect(installProductionDependencies(path)).to.eventually.be.fulfilled

      expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install --production --no-bin-links', {
        cwd: path,
      })
    })

    it('wraps the exec error', async () => {
      const error = new Error('something wrong happened')
      const path = 'aPath'

      replace(childProcessPromise, 'exec', fake.rejects(error))

      await expect(installProductionDependencies(path)).to.eventually.be.rejectedWith(
        /Could not install production dependencies/
      )
    })
  })

  describe('installAllDependencies', () => {
    context('without passing a path', () => {
      it('installs dependencies in dev mode in the current directory', async () => {
        replace(childProcessPromise, 'exec', fake.resolves({}))

        await expect(installAllDependencies()).to.eventually.be.fulfilled

        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install', { cwd: process.cwd() })
      })
    })

    context('passing a path', () => {
      it('installs dependencies in dev mode in the passed path', async () => {
        replace(childProcessPromise, 'exec', fake.resolves({}))

        await expect(installAllDependencies('somewhere')).to.eventually.be.fulfilled

        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install', { cwd: 'somewhere' })
      })
    })

    it('wraps the exec error', async () => {
      const error = new Error('something wrong happened')

      replace(childProcessPromise, 'exec', fake.rejects(error))

      await expect(installAllDependencies()).to.eventually.be.rejectedWith(/Could not install dependencies/)
    })
  })
})
