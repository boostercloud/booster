import * as path from 'path'
import { expect } from 'chai'
import { sandboxPathFor, removeFolders, fileExists } from '../helper/fileHelper'
import { exec } from 'child-process-promise'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../cli/src/common/sandbox'

describe('Clean', () => {
  let cleanSandboxDir: string

  before(async () => {
    cleanSandboxDir = createSandboxProject(sandboxPathFor('clean'))
  })

  after(() => {
    removeFolders([cleanSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid clean', () => {
    it('should clean the project after build', async () => {
      
    //   const expectedBuildOutputRegex = new RegExp(
    //     ['boost build', 'Checking project structure', 'Building project', 'Build complete'].join('(.|\n)*')
    //   )

      await exec(`${cliPath} build`, { cwd: cleanSandboxDir })
      
      expect(fileExists(path.join(cleanSandboxDir,'dist'))).to.be.true
      expect(fileExists(path.join(cleanSandboxDir,'dist','index.js'))).to.be.true
      expect(fileExists(path.join(cleanSandboxDir,'dist','index.d.ts'))).to.be.true
      expect(fileExists(path.join(cleanSandboxDir,'dist','roles.js'))).to.be.true
      expect(fileExists(path.join(cleanSandboxDir,'dist','roles.d.ts'))).to.be.true
      expect(fileExists(path.join(cleanSandboxDir,'dist','config','config.js'))).to.be.true
      expect(fileExists(path.join(cleanSandboxDir,'dist','config','config.d.ts'))).to.be.true

      const expectedCleanOutputRegex = new RegExp(
        ['boost clean', 'Checking project structure', 'Cleaning project', 'Clean complete'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} clean`, { cwd: cleanSandboxDir })

      expect(stdout).to.match(expectedCleanOutputRegex)
      expect(fileExists(path.join(cleanSandboxDir,'dist'))).to.be.false

    })
  })
})