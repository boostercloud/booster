import { processInternals } from '../../../src/services/process'
import * as process from 'process'
import * as childProcessPromise from 'child-process-promise'
import { fake, replace } from 'sinon'
import { unsafeRunEffect } from '@boostercloud/framework-types/src/effect'
import { LiveProcess } from '../../../src/services/process/live.impl'
import { expect } from '../../expect'

describe('Process - Live Implementation', () => {
  beforeEach(() => {
    replace(process, 'cwd', fake.returns(''))
    replace(childProcessPromise, 'exec', fake.resolves({}))
  })

  it('uses process.cwd', () => {
    const { cwd } = processInternals
    unsafeRunEffect(cwd(), {
      layer: LiveProcess,
      onError: (error) => {
        throw error
      },
    })
    expect(process.cwd).to.have.been.called
  })

  it('uses child-process-promise.exec', () => {
    const { exec } = processInternals
    const command = 'command'
    const cwd = 'cwd'

    unsafeRunEffect(exec(command, cwd), {
      layer: LiveProcess,
      onError: (error) => {
        throw error
      },
    })
    expect(childProcessPromise.exec).to.have.been.calledWith(command, { cwd })
  })
})
