import { fake, replace, restore, SinonSpy, stub } from 'sinon'
import { expect } from '../expect'
import * as fs from 'fs'
import * as fse from 'fs-extra'
import { createSandboxProject } from '../../src/common/sandbox'
import { Dirent } from 'fs'
import * as path from 'path'

describe('sandbox', () => {
  let fakeRmSync: SinonSpy
  let fakeMkdirSync: SinonSpy
  let fakeCopySync: SinonSpy
  beforeEach(() => {
    fakeRmSync = fake()
    fakeMkdirSync = fake()
    fakeCopySync = fake()
    const fakeStatSync = (fileName: string) => ({
      isDirectory: () => !fileName.includes('.'),
    })
    replace(fs, 'rmSync', fakeRmSync)
    replace(fs, 'mkdirSync', fakeMkdirSync)
    replace(fse, 'copySync', fakeCopySync)
    replace(fs, 'statSync', fakeStatSync as any)
  })
  afterEach(() => {
    restore()
  })

  describe('createSandboxProject', () => {
    it('copies all needed files', async () => {
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

      expect(fakeRmSync).to.have.been.calledOnceWith(sandboxPath)

      expect(fakeMkdirSync).to.have.been.calledTwice
      expect(fakeMkdirSync).to.have.been.calledWith(sandboxPath)
      expect(fakeMkdirSync).to.have.been.calledWith(path.join(sandboxPath, 'src', 'commands'))

      expect(fakeReaddirSync).to.have.been.calledThrice
      expect(fakeReaddirSync).to.have.been.calledWith('src')
      expect(fakeReaddirSync).to.have.been.calledWith(path.join('src', 'commands'))
      expect(fakeReaddirSync).to.have.been.calledWith(path.join('assetFolder'))

      expect(fakeCopySync).to.have.callCount(9)
      const copyFileCallsArguments = [
        ['package.json', path.join(sandboxPath, 'package.json')],
        ['package-lock.json', path.join(sandboxPath, 'package-lock.json')],
        ['tsconfig.json', path.join(sandboxPath, 'tsconfig.json')],
        [path.join('src', 'index.js'), path.join(sandboxPath, 'src', 'index.js')],
        [path.join('src', 'commands', 'commandA.js'), path.join(sandboxPath, 'src', 'commands', 'commandA.js')],
        [path.join('src', 'commands', 'commandB.js'), path.join(sandboxPath, 'src', 'commands', 'commandB.js')],
        [path.join('assetFolder', 'assetFile1.txt'), path.join(sandboxPath, 'assetFolder', 'assetFile1.txt')],
        [path.join('assetFolder', 'assetFile2.txt'), path.join(sandboxPath, 'assetFolder', 'assetFile2.txt')],
        ['assetFile3.txt', path.join(sandboxPath, 'assetFile3.txt')],
      ]
      copyFileCallsArguments.forEach((args) => {
        expect(fakeCopySync).to.have.been.calledWith(...args)
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
