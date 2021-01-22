/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { restore, replace, stub, spy } from 'sinon'
import * as fs from 'fs'
import * as path from 'path'
import { Importer } from '../src/importer'
import { expect } from './expect'

describe('the `importer` service', () => {
  afterEach(() => {
    restore()
  })

  // FIXME
  describe('the `importUserProjectFiles` function', () => {
    it('calls `require` for each import file', () => {
      const codeRelativePath = 'dist'
      const fakeStatSync = (fileName: string) => ({
        isDirectory: () => !fileName.includes('.'),
      })
      replace(fs, 'statSync', fakeStatSync as any)

      const fakeReaddirSync = stub()
        .onFirstCall()
        .returns(['src', 'index.js', 'lol.js'])
        .onSecondCall()
        .returns(['index.js', 'test.ts', 'types.d.ts', 'lol.js'])
      replace(fs, 'readdirSync', fakeReaddirSync as any)

      const fakeImportWithoutExtension = spy()
      replace(Importer as any, 'importWithoutExtension', fakeImportWithoutExtension)

      Importer.importUserProjectFiles(codeRelativePath)

      expect(fakeImportWithoutExtension).to.have.been.calledTwice
      expect(fakeImportWithoutExtension.firstCall).to.have.been.calledWith(path.join(codeRelativePath, 'src', 'lol.js'))
      expect(fakeImportWithoutExtension.secondCall).to.have.been.calledWith(path.join(codeRelativePath, 'lol.js'))
    })
  })
})
