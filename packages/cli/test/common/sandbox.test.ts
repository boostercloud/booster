import { fake, replace, restore, SinonSpy, stub } from 'sinon'
import { expect } from '../expect'
import * as fs from 'fs'
import { createSandboxProject } from '../../src/common/sandbox'
import { Dirent } from 'fs'
import * as path from 'path'

describe('sandbox', () => {
  let fakeRmdirSync: SinonSpy
  let fakeMkdirSync: SinonSpy
  let fakeCopyFileSync: SinonSpy
  beforeEach(() => {
    fakeRmdirSync = fake()
    fakeMkdirSync = fake()
    fakeCopyFileSync = fake()
    const fakeStatSync = (fileName: string) => ({
      isDirectory: () => !fileName.includes('.'),
    })
    replace(fs, 'rmdirSync', fakeRmdirSync)
    replace(fs, 'mkdirSync', fakeMkdirSync)
    replace(fs, 'copyFileSync', fakeCopyFileSync)
    replace(fs, 'statSync', fakeStatSync as any)
  })
  afterEach(() => {
    restore()
  })

  describe('createSandboxProject', () => {
    it('copy all needed files', async () => {
      const fakeReaddirSync = stub()
        .onFirstCall()
        .returns([fakeDirent('commands', true), fakeDirent('index.js')])
        .onSecondCall()
        .returns([fakeDirent('commandA.js'), fakeDirent('commandB.js')])
        .onThirdCall()
        .returns([fakeDirent('assetFile1.txt'), fakeDirent('assetFile2.txt')])
      replace(fs, 'readdirSync', fakeReaddirSync as any)

      const sandboxPath = 'testProjectPath'
      const projectAssets = ['assetFolder', 'assetFile3.txt']
      createSandboxProject(sandboxPath, projectAssets)

      expect(fakeRmdirSync).to.have.been.calledOnceWith(sandboxPath)

      expect(fakeMkdirSync).to.have.been.calledTwice
      expect(fakeMkdirSync).to.have.been.calledWith(sandboxPath)
      expect(fakeMkdirSync).to.have.been.calledWith(path.join(sandboxPath, 'src', 'commands'))

      expect(fakeReaddirSync).to.have.been.calledThrice
      expect(fakeReaddirSync).to.have.been.calledWith('src')
      expect(fakeReaddirSync).to.have.been.calledWith(path.join('src', 'commands'))
      expect(fakeReaddirSync).to.have.been.calledWith(path.join('assetFolder'))

      expect(fakeCopyFileSync).to.have.callCount(8)
      const copyFileCallsArguments = [
        ['package.json', path.join(sandboxPath, 'package.json')],
        ['tsconfig.json', path.join(sandboxPath, 'tsconfig.json')],
        [path.join('src', 'index.js'), path.join(sandboxPath, 'src', 'index.js')],
        [path.join('src', 'commands', 'commandA.js'), path.join(sandboxPath, 'src', 'commands', 'commandA.js')],
        [path.join('src', 'commands', 'commandB.js'), path.join(sandboxPath, 'src', 'commands', 'commandB.js')],
        [path.join('assetFolder', 'assetFile1.txt'), path.join(sandboxPath, 'assetFolder', 'assetFile1.txt')],
        [path.join('assetFolder', 'assetFile2.txt'), path.join(sandboxPath, 'assetFolder', 'assetFile2.txt')],
        ['assetFile3.txt', path.join(sandboxPath, 'assetFile3.txt')],
      ]
      copyFileCallsArguments.forEach((args) => {
        expect(fakeCopyFileSync).to.have.been.calledWith(...args)
      })
    })
  })
})

function fakeDirent(name: string, isDirectory = false): Partial<Dirent> {
  return {
    name,
    isFile: () => !isDirectory,
    isDirectory: () => isDirectory,
  }
}
