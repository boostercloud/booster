import * as path from 'path'
import { expect } from 'chai'
import { sandboxPathFor, removeFolders, fileExists } from '../helper/fileHelper'
import { exec } from 'child-process-promise'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../cli/src/common/sandbox'

describe('Build', () => {
  let buildSandboxDir: string

  before(async () => {
    buildSandboxDir = createSandboxProject(sandboxPathFor('build'))
  })

  after(() => {
    removeFolders([buildSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid build', () => {
    it('should build the project', async () => {
      
      const expectedOutputRegex = new RegExp(
        ['boost build', 'Checking project structure', 'Building project', 'Build complete'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} build`, { cwd: buildSandboxDir })

      expect(stdout).to.match(expectedOutputRegex)
      expect(fileExists(path.join(buildSandboxDir,'dist','index.js'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir,'dist','index.d.ts'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir,'dist','roles.js'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir,'dist','roles.d.ts'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir,'dist','config','config.js'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir,'dist','config','config.d.ts'))).to.be.true
    })
  })
})