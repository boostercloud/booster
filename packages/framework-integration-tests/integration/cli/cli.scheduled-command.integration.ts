import path = require('path')
import util = require('util')
import { expect } from 'chai'
import { readFileContent } from '../helper/fileHelper'

const exec = util.promisify(require('child_process').exec)

const TEST_SCHEDULED_COMMAND_PATH = path.join('src','scheduled-commands','CheckCart.ts')

export const CLI_SCHEDULED_COMMAND_INTEGRATION_TEST_FILES: Array<string> = [FILE_CHECK_CART_SCHEDULED_COMMAND]

describe('ScheduledCommand', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')

  context('Valid command', () => {
    it('should create a new scheduled command', async () => {
      const expectedOutputRegex = new RegExp(
        [
          '.*boost.*new:scheduled-command',
          'Verifying project',
          'Creating new scheduled command',
          'Scheduled command generated!.*'
        ].join('.*')
      )

      const { stdout } = await exec(`${cliPath} new:scheduled-command CheckCart`)
      expect(stdout).to.match(expectedOutputRegex)

      const expectedCommandContent = await readFileContent('integration/fixtures/scheduled-commands/CheckCart.ts')
      const commandContent = await readFileContent(FILE_CHECK_CART_SCHEDULED_COMMAND)
      expect(commandContent).to.equal(expectedCommandContent)
    })
  })

  context('Invalid scheduled command', () => {
    describe('missing scheduled command name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:scheduled-command`)

        expect(stderr).to.equal(
          "You haven't provided a scheduled command name, but it is required, run with --help for usage\n"
        )
      })
    })
  })
})
