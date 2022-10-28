import * as fs from 'fs'
import { fake, replace } from 'sinon'
import { gen, unsafeRunEffect } from '@boostercloud/framework-types/dist/effect'
import { expect } from '../../expect'
import { FileSystemService } from '../../../src/services/file-system'
import { LiveFileSystem } from '../../../src/services/file-system/live.impl'
import { guardError } from '../../../src/common/errors'

describe('FileSystem - Live Implementation', () => {
  beforeEach(() => {
    replace(fs.promises, 'readdir', fake.resolves(''))
  })

  it('uses fs.promises.readdir', async () => {
    const directoryPath = 'directoryPath'
    const effect = gen(function* ($) {
      const { readDirectoryContents } = yield* $(FileSystemService)
      return yield* $(readDirectoryContents(directoryPath))
    })
    await unsafeRunEffect(effect, {
      layer: LiveFileSystem,
      onError: guardError('An error ocurred'),
    })
    expect(fs.promises.readdir).to.have.been.calledWith(directoryPath)
  })
})
