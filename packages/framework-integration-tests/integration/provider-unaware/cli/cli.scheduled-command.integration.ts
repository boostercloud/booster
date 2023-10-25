import * as path from 'path'
import { expect } from 'chai'
import { loadFixture, readFileContent, removeFolders, sandboxPathFor } from '../../helper/file-helper'
import { exec } from 'child-process-promise'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'

describe('Scheduled Command', () => {
  let scheduledCommandSandboxDir: string

  before(async () => {
    scheduledCommandSandboxDir = createSandboxProject(sandboxPathFor('scheduled-command'))
  })

  after(async () => {
    await removeFolders([scheduledCommandSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid scheduled command', () => {
    it('should create a new scheduled command', async () => {
      const expectedOutputRegex = new RegExp(
        [
          'boost new:scheduled-command',
          'Verifying project',
          'Creating new scheduled command',
          'Scheduled command generated',
        ].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} new:scheduled-command CheckCart`, { cwd: scheduledCommandSandboxDir })
      expect(stdout).to.match(expectedOutputRegex)

      const expectedCommandContent = loadFixture('scheduled-commands/check-cart.ts')
      const commandContent = readFileContent(
        path.join(scheduledCommandSandboxDir, 'src', 'scheduled-commands', 'check-cart.ts')
      )
      expect(commandContent).to.equal(expectedCommandContent)
    })
  })

  context('Invalid scheduled command', () => {
    describe('missing scheduled command name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:scheduled-command`, { cwd: scheduledCommandSandboxDir })

        expect(stderr).to.match(
          /You haven't provided a scheduled command name, but it is required, run with --help for usage/
        )
      })
    })
  })
})
