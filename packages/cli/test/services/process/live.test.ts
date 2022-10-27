import { processInternals } from '../../../src/services/process'
import * as process from 'process'
import * as childProcessPromise from 'child-process-promise'
import { fake, replace, restore } from 'sinon'
import { unsafeRunEffect } from '@boostercloud/framework-types/src/effect'
import { LiveProcess } from '../../../src/services/process/live.impl'
import { expect } from '../../expect'
import { guardError } from '../../../src/common/errors'

describe('Process - Live Implementation', () => {
  beforeEach(() => {
    replace(process, 'cwd', fake.returns(''))
    replace(childProcessPromise, 'exec', fake.resolves({}))
  })

  afterEach(() => {
    restore()
  })

  it('uses process.cwd', async () => {
    const { cwd } = processInternals
    await unsafeRunEffect(cwd(), {
      layer: LiveProcess,
      onError: guardError('An error ocurred'),
    })
    expect(process.cwd).to.have.been.called
  })

  it('uses child-process-promise.exec', async () => {
    const { exec } = processInternals
    const command = 'command'
    const cwd = 'cwd'

    await unsafeRunEffect(exec(command, cwd), {
      layer: LiveProcess,
      onError: guardError('An error ocurred'),
    })
    expect(childProcessPromise.exec).to.have.been.calledWith(command, { cwd })
  })
})
