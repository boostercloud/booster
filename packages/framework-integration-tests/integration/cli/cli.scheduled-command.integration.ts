import path = require('path')
import util = require('util')
import { expect } from 'chai'
import { readFileContent } from '../helper/fileHelper'

const exec = util.promisify(require('child_process').exec)

const TEST_SCHEDULED_COMMAND_PATH = path.join('src', 'scheduled-commands', 'check-cart.ts')

export const CLI_SCHEDULED_COMMAND_INTEGRATION_TEST_FILES: Array<string> = [TEST_SCHEDULED_COMMAND_PATH]

describe('Scheduled Command', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')

  context('Valid scheduled command', () => {
    it('should create a new scheduled command', async () => {
      const expectedOutputRegex = new RegExp(
        /(.+) boost (.+)?new:scheduled-command(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new scheduled command\n(.+) Creating new scheduled command\n(.+) Scheduled command generated!\n/
      )

      const { stdout } = await exec(`${cliPath} new:scheduled-command CheckCart`)
      expect(stdout).to.match(expectedOutputRegex)

      const expectedCommandContent = await readFileContent('integration/fixtures/scheduled-commands/check-cart.ts')
      const commandContent = await readFileContent(TEST_SCHEDULED_COMMAND_PATH)
      expect(commandContent).to.equal(expectedCommandContent)
    })
  })

  context('Invalid scheduled command', () => {
    describe('missing scheduled command name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:scheduled-command`)

        expect(stderr).to.match(
          /You haven't provided a scheduled command name, but it is required, run with --help for usage/
        )
      })
    })
  })
})
