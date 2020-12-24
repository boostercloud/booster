import * as path from 'path'
import { expect } from 'chai'
import { createSandboxProject, loadFixture, readFileContent, removeFolders } from '../helper/fileHelper'
import { exec } from 'child-process-promise'

describe('Scheduled Command', () => {
  let scheduledCommandSandboxDir: string

  before(async () => {
    scheduledCommandSandboxDir = await createSandboxProject('scheduled-command')
  })

  after(() => {
    removeFolders([scheduledCommandSandboxDir])
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
