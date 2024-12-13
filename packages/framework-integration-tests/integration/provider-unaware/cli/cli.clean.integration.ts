import * as path from 'path'
import { expect } from 'chai'
import { fileExists, removeFolders, sandboxPathFor } from '../../helper/file-helper'
import { command } from 'execa'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'

describe('Clean', () => {
  let cleanSandboxDir: string

  before(async () => {
    cleanSandboxDir = createSandboxProject(sandboxPathFor('clean'))
  })

  after(async () => {
    await removeFolders([cleanSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid clean', () => {
    it('should clean the project after build', async () => {
      await command(`${cliPath} build`, { cwd: cleanSandboxDir })

      expect(fileExists(path.join(cleanSandboxDir, 'dist'))).to.be.true

      const expectedCleanOutputRegex = new RegExp(
        ['boost clean', 'Checking project structure', 'Cleaning project', 'Clean complete'].join('(.|\n)*')
      )

      const { stdout } = await command(`${cliPath} clean`, { cwd: cleanSandboxDir })

      expect(stdout).to.match(expectedCleanOutputRegex)
      expect(fileExists(path.join(cleanSandboxDir, 'dist'))).to.be.false
    })
  })
})
