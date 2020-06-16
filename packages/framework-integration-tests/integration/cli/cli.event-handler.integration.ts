import path = require('path')
import util = require('util')
import { expect } from 'chai'
import { readFileContent } from '../helper/fileHelper'

const exec = util.promisify(require('child_process').exec)

const FILE_HANDLE_CART_CHANGED_EVENT_HANDLER = 'src/event-handlers/HandleCartChange.ts'

export const CLI_EVENT_HANDLERS_INTEGRATION_TEST_FILES: Array<string> = [FILE_HANDLE_CART_CHANGED_EVENT_HANDLER]

describe('Event handler', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')

  describe('Valid event handler', () => {
    it('should create new event handler', async () => {
      const expectedOutputRegex = new RegExp(
        /(.+) boost (.+)?new:event-handler(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new event handler\n(.+) Creating new event handler\n(.+) Event handler generated!\n/
      )

      const { stdout } = await exec(`${cliPath} new:event-handler HandleCartChange -e CartItemChanged`)
      expect(stdout).to.match(expectedOutputRegex)

      const expectedEventContent = await readFileContent('integration/fixtures/event-handlers/HandleCartChange.ts')
      const eventContent = await readFileContent(FILE_HANDLE_CART_CHANGED_EVENT_HANDLER)

      expect(eventContent).to.equal(expectedEventContent)
    })
  })

  describe('Invalid event handler', () => {
    context('without name and event', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:event`)

        expect(stderr).to.equal(
          "You haven't provided an event handler name, but it is required, run with --help for usage\n"
        )
      })
    })

    context('Without name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:event`)

        expect(stderr).to.equal(
          "You haven't provided an event handler name, but it is required, run with --help for usage\n"
        )
      })
    })

    context('Without event', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:event`)

        expect(stderr).to.equal("You haven't provided an event, but it is required, run with --help for usage\n")
      })
    })
  })
})
