import path = require('path')
import util = require('util')
import { expect } from 'chai'
import { readFileContent, writeFileContent } from '../helper/fileHelper'

const exec = util.promisify(require('child_process').exec)

const EVENT_ENTITY_ID_PLACEHOLDER = '/* the associated entity ID */'

const FILE_CART_CHANGED_EVENT = 'src/events/CartChanged.ts'

export const CLI_EVENTS_INTEGRATION_TEST_FILES: Array<string> = [
  FILE_CART_CHANGED_EVENT,
]

describe('Event', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')

  context('Valid event', () => {
    it('should create new event', async () => {
      const expectedOutputRegex = new RegExp(
        /(.+) boost (.+)?new:event(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new event\n(.+) Creating new event\n(.+) Event generated!\n/,
      )

      const { stdout } = await exec(`${cliPath} new:event CartChanged`)
      expect(stdout).to.match(expectedOutputRegex)
    })

    describe('without fields', () => {
      it('should create new event', async () => {
        await exec(`${cliPath} new:event CartChanged`)

        const expectedEventContent = await readFileContent('integration/fixtures/events/CartChanged.ts')
        const eventContent = await readFileContent(FILE_CART_CHANGED_EVENT)
        expect(eventContent).to.equal(expectedEventContent)

        // Set event entity ID
        const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, '\'some-id\'')

        writeFileContent('src/events/CartChanged.ts', updatedEventContent)
      })
    })

    describe('with fields', () => {

    })
  })

  context('Invalid event', () => {
    describe('missing event name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:event`)

        expect(stderr).to.equal('You haven\'t provided an event name, but it is required, run with --help for usage\n')
      })
    })
  })
})
