import * as process from 'process'
import * as childProcessPromise from 'child-process-promise'
import { fake, replace, restore } from 'sinon'
import { gen, unsafeRunEffect } from '@boostercloud/framework-types/dist/effect'
import { LiveProcess } from '../../../src/services/process/live.impl'
import { expect } from '../../expect'
import { guardError } from '../../../src/common/errors'
import { ProcessService } from '../../../src/services/process'

describe('Process - Live Implementation', () => {
  beforeEach(() => {
    replace(process, 'cwd', fake.returns(''))
    replace(childProcessPromise, 'exec', fake.resolves({}))
  })

  afterEach(() => {
    restore()
  })

  it('uses process.cwd', async () => {
    const effect = gen(function* ($) {
      const { cwd } = yield* $(ProcessService)
      return yield* $(cwd())
    })
    await unsafeRunEffect(effect, {
      layer: LiveProcess,
      onError: guardError('An error ocurred'),
    })
    expect(process.cwd).to.have.been.called
  })

  it('uses child-process-promise.exec', async () => {
    const command = 'command'
    const cwd = 'cwd'

    const effect = gen(function* ($) {
      const { exec } = yield* $(ProcessService)
      return yield* $(exec(command, cwd))
    })

    await unsafeRunEffect(effect, {
      layer: LiveProcess,
      onError: guardError('An error ocurred'),
    })
    expect(childProcessPromise.exec).to.have.been.calledWith(command, { cwd })
  })
})
