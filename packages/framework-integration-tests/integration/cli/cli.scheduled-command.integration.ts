import * as path from 'path'
import { expect } from 'chai'
import { createSandboxProject, loadFixture, readFileContent, removeFolders } from '../helper/fileHelper'
import { exec } from 'child-process-promise'

describe('Scheduled Command', () => {
  const SANDBOX_INTEGRATION_DIR = 'scheduled-command-integration-sandbox'
  const TEST_SCHEDULED_COMMAND_PATH = path.join(SANDBOX_INTEGRATION_DIR, 'src', 'scheduled-commands', 'check-cart.ts')

  before(async () => {
    createSandboxProject(SANDBOX_INTEGRATION_DIR)
  })

  after(() => {
    removeFolders([SANDBOX_INTEGRATION_DIR])
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

      const { stdout } = await exec(`${cliPath} new:scheduled-command CheckCart`, { cwd: SANDBOX_INTEGRATION_DIR })
      expect(stdout).to.match(expectedOutputRegex)

      const expectedCommandContent = loadFixture('scheduled-commands/check-cart.ts')
      const commandContent = readFileContent(TEST_SCHEDULED_COMMAND_PATH)
      expect(commandContent).to.equal(expectedCommandContent)
    })
  })

  context('Invalid scheduled command', () => {
    describe('missing scheduled command name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:scheduled-command`, { cwd: SANDBOX_INTEGRATION_DIR })

        expect(stderr).to.match(
          /You haven't provided a scheduled command name, but it is required, run with --help for usage/
        )
      })
    })
  })
})
