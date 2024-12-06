import * as process from 'process'
import * as execa from 'execa'
import { fake, replace, restore } from 'sinon'
import { Effect, gen, mapError, pipe, unsafeRunEffect } from '@boostercloud/framework-types/dist/effect'
import { LiveProcess } from '../../../src/services/process/live.impl'
import { expect } from '../../expect'
import { guardError } from '../../../src/common/errors'
import { ProcessService } from '../../../src/services/process'

describe('Process - Live Implementation', () => {
  beforeEach(() => {
    replace(process, 'cwd', fake.returns(''))
    replace(execa, 'command', fake.resolves({ stdout: '', stderr: '' }))
  })

  afterEach(() => {
    restore()
  })

  const mapEffError = <R, A>(effect: Effect<R, { error: Error }, A>) =>
    pipe(
      effect,
      mapError((e) => e.error)
    )

  it('uses process.cwd', async () => {
    const effect = gen(function* ($) {
      const { cwd } = yield* $(ProcessService)
      return yield* $(cwd())
    })
    await unsafeRunEffect(mapEffError(effect), {
      layer: LiveProcess,
      onError: guardError('An error ocurred'),
    })
    expect(process.cwd).to.have.been.called
  })

  it('uses execa.command', async () => {
    const command = 'command'
    const cwd = 'cwd'

    const effect = gen(function* ($) {
      const { exec } = yield* $(ProcessService)
      return yield* $(exec(command, cwd))
    })

    await unsafeRunEffect(mapEffError(effect), {
      layer: LiveProcess,
      onError: guardError('An error ocurred'),
    })
    expect(execa.command).to.have.been.calledWith(command, { cwd })
  })
})
