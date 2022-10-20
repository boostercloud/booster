import * as fs from 'fs'
import { fake, replace } from 'sinon'
import { unsafeRunEffect } from '@boostercloud/framework-types/src/effect'
import { expect } from '../../expect'
import { fileSystemInternals } from '../../../src/services/file-system'
import { LiveFileSystem } from '../../../src/services/file-system/live.impl'

describe('FileSystem - Live Implementation', () => {
  beforeEach(() => {
    replace(fs.promises, 'readdir', fake.resolves(''))
  })

  it('uses fs.promises.readdir', () => {
    const { readDirectoryContents } = fileSystemInternals
    const directoryPath = 'directoryPath'
    unsafeRunEffect(readDirectoryContents(directoryPath), {
      layer: LiveFileSystem,
      onError: (error) => {
        throw error
      },
    })
    expect(fs.promises.readdir).to.have.been.calledWith(directoryPath)
  })
})
