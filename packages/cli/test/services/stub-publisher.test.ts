import { restore, stub, fake, SinonSpy, replace } from 'sinon'
import { join } from 'path'
import * as fs from 'fs-extra'
import { expect } from '../expect'
import {
  checkResourceStubFileExists,
  checkStubsFolderExists,
  createStubsFolder,
  createTemplateFileMap,
  publishStubFiles,
  resourceStubFilePath,
  resourceTemplateFilePath,
  resourceTemplatesPath,
  stubFolderPath,
} from '../../src/services/stub-publisher'
const rewire = require('rewire')

describe('stub publisher', () => {
  const directoryFileMocks: fs.Dirent[] = [
    {
      name: 'fake-command.ts',
      isFile: () => true,
      isDirectory: () => false,
      isBlockDevice: () => false,
      isCharacterDevice: () => false,
      isSymbolicLink: () => false,
      isFIFO: () => false,
      isSocket: () => false,
    },
    {
      name: 'fake-event.ts',
      isFile: () => true,
      isDirectory: () => false,
      isBlockDevice: () => false,
      isCharacterDevice: () => false,
      isSymbolicLink: () => false,
      isFIFO: () => false,
      isSocket: () => false,
    },
    {
      name: 'fake-directory-1',
      isFile: () => false,
      isDirectory: () => true,
      isBlockDevice: () => false,
      isCharacterDevice: () => false,
      isSymbolicLink: () => false,
      isFIFO: () => false,
      isSocket: () => false,
    },
    {
      name: 'fake-directory-2',
      isFile: () => false,
      isDirectory: () => true,
      isBlockDevice: () => false,
      isCharacterDevice: () => false,
      isSymbolicLink: () => false,
      isFIFO: () => false,
      isSocket: () => false,
    },
  ]

  afterEach(() => {
    restore()
  })

  describe('resourceStubFilePath and resourceTemplateFilePath', () => {
    it('should return path to stub file', () => {
      const fileName = 'test-command.ts'
      const stubFilePath = resourceStubFilePath(fileName)
      const expectedStubFilePath = join(stubFolderPath, fileName)

      expect(stubFilePath).to.be.equal(expectedStubFilePath)
    })

    it('should return path to template file', () => {
      const fileName = 'test-command.ts'
      const stubFilePath = resourceTemplateFilePath(fileName)
      const expectedTemplateFilePath = join(resourceTemplatesPath, fileName)

      expect(stubFilePath).to.be.equal(expectedTemplateFilePath)
    })
  })

  describe('checkStubsFolderExists', () => {
    it('should return true if `/stubs` folder exists', () => {
      stub(fs, 'existsSync').returns(true)

      const result = checkStubsFolderExists()

      expect(result).to.be.true
      expect(fs.existsSync).to.have.been.calledOnce
      expect(fs.existsSync).to.be.calledOnceWith(stubFolderPath)
    })

    it('should return false if `/stubs` folder does not exists', () => {
      stub(fs, 'existsSync').returns(false)

      const result = checkStubsFolderExists()

      expect(result).to.be.false
      expect(fs.existsSync).to.have.been.calledOnce
      expect(fs.existsSync).to.be.calledOnceWith(stubFolderPath)
    })
  })

  describe('checkResourceStubFileExists', () => {
    it('should return true if given file exists in `/stubs` folder', () => {
      stub(fs, 'existsSync').returns(true)
      const filePath = resourceStubFilePath('command.ts')

      const fileExists = checkResourceStubFileExists(filePath)

      expect(fileExists).to.be.true
      expect(fs.existsSync).to.have.been.calledOnce
      expect(fs.existsSync).to.be.calledOnceWith(filePath)
    })

    it('should return false if given file exists in `/stubs` folder', () => {
      stub(fs, 'existsSync').returns(false)
      const filePath = resourceStubFilePath('command.ts')

      const fileExists = checkResourceStubFileExists(filePath)

      expect(fileExists).to.be.false
      expect(fs.existsSync).to.have.been.calledOnce
      expect(fs.existsSync).to.be.calledOnceWith(filePath)
    })
  })

  describe('createStubsFolder', () => {
    it('creates `/stubs` folder', () => {
      const fakeMkdirSync: SinonSpy = fake()
      replace(fs, 'mkdirSync', fakeMkdirSync)

      createStubsFolder()

      expect(fakeMkdirSync).to.have.been.calledOnce
      expect(fakeMkdirSync).to.have.been.calledOnceWith(stubFolderPath)
    })
  })

  describe('createTemplateFileMap', () => {
    describe('filters out directories', () => {
      it('when has no directory', () => {
        const filteredFiles = createTemplateFileMap([
          {
            name: 'fake-file-1',
            isFile: () => true,
            isDirectory: () => false,
            isBlockDevice: () => false,
            isCharacterDevice: () => false,
            isSymbolicLink: () => false,
            isFIFO: () => false,
            isSocket: () => false,
          },
          {
            name: 'fake-file-2',
            isFile: () => true,
            isDirectory: () => false,
            isBlockDevice: () => false,
            isCharacterDevice: () => false,
            isSymbolicLink: () => false,
            isFIFO: () => false,
            isSocket: () => false,
          },
        ])

        expect(Object.keys(filteredFiles)).to.have.lengthOf(2)
      })

      it('when has directory', () => {
        const filteredFiles = createTemplateFileMap(directoryFileMocks)

        expect(Object.keys(filteredFiles)).to.have.lengthOf(2)
      })

      it('when has no files', () => {
        const filteredFiles = createTemplateFileMap([
          {
            name: 'fake-directory',
            isFile: () => false,
            isDirectory: () => true,
            isBlockDevice: () => false,
            isCharacterDevice: () => false,
            isSymbolicLink: () => false,
            isFIFO: () => false,
            isSocket: () => false,
          },
        ])

        expect(Object.keys(filteredFiles)).to.have.lengthOf(0)
      })
    })

    it('generates template file map', () => {
      const filteredFiles = createTemplateFileMap(directoryFileMocks)

      expect(Object.keys(filteredFiles)).to.have.lengthOf(2)
      expect(filteredFiles).to.deep.equal({
        [join(resourceTemplatesPath, 'fake-command.ts')]: join(stubFolderPath, 'fake-command.ts'),
        [join(resourceTemplatesPath, 'fake-event.ts')]: join(stubFolderPath, 'fake-event.ts'),
      })
    })
  })

  describe('publishStubFiles', () => {
    beforeEach(() => {
      replace(fs, 'readdirSync', fake.returns(directoryFileMocks))
    })

    it('copies template files', () => {
      const fakeWriteFileSync: SinonSpy = fake()
      const fakeReadFileSync: SinonSpy = fake()

      replace(fs, 'writeFileSync', fakeWriteFileSync)
      replace(fs, 'readFileSync', fakeReadFileSync)

      void publishStubFiles()

      expect(fs.readdirSync).to.have.been.calledOnceWith(resourceTemplatesPath, { withFileTypes: true })
      expect(fs.readdirSync).to.have.returned(directoryFileMocks)

      expect(fakeWriteFileSync).to.have.been.calledTwice
      expect(fakeReadFileSync).to.have.been.calledTwice
    })

    it("throws error if can't copy stub file", async () => {
      replace(fs, 'writeFileSync', fake.throws(new Error()))

      let exceptionThrown = false

      await publishStubFiles().catch(() => {
        exceptionThrown = true
      })

      expect(exceptionThrown).to.be.true
    })
  })

  describe('copyStubFile', () => {
    const filenames = rewire('../../src/services/stub-publisher')
    const copyStubFile = filenames.__get__('copyStubFile')

    it('should copy stub file', () => {
      const from = 'from/test.ts'
      const to = 'to/test.ts'
      const fakeContent = 'file content!'

      const fakeWriteFileSync: SinonSpy = fake()

      replace(fs, 'writeFileSync', fakeWriteFileSync)
      replace(fs, 'readFileSync', fake.returns(fakeContent))

      copyStubFile(from, to)

      expect(fakeWriteFileSync).to.have.been.calledOnce
      expect(fakeWriteFileSync).to.have.been.calledOnce
      expect(fakeWriteFileSync).to.have.been.calledOnceWith(to, fakeContent)

      expect(fs.readFileSync).to.have.been.calledOnceWith(from)
      expect(fs.readFileSync).to.returned(fakeContent)
    })
  })
})
