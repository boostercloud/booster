import path = require('path')
import util = require('util')
import { expect } from 'chai'
import { readFileContent, writeFileContent } from '../helper/fileHelper'

const exec = util.promisify(require('child_process').exec)

const COMMAND_AUTH_PLACEHOLDER = "// Specify authorized roles here. Use 'all' to authorize anyone"

const FILE_CHANGE_CART_COMMAND = 'src/commands/ChangeCart.ts'
const FILE_CHANGE_CART_WITH_FIELDS_COMMAND = 'src/commands/ChangeCartWithFields.ts'

export const CLI_COMMAND_INTEGRATION_TEST_FILES: Array<string> = [
  FILE_CHANGE_CART_COMMAND,
  FILE_CHANGE_CART_WITH_FIELDS_COMMAND,
]

describe('Command', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')

  context('Valid command', () => {
    it('should create a new command', async () => {
      const expectedOutputRegex = new RegExp(
        /(.+) boost (.+)?new:command(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new command\n(.+) Creating new command\n(.+) Command generated!\n/
      )

      const { stdout } = await exec(`${cliPath} new:command ChangeCart`)
      expect(stdout).to.match(expectedOutputRegex)

      const expectedCommandContent = await readFileContent('integration/fixtures/commands/ChangeCart.ts')
      const commandContent = await readFileContent(FILE_CHANGE_CART_COMMAND)
      expect(commandContent).to.equal(expectedCommandContent)

      // Set command auth
      const updatedCommandContent = commandContent.replace(COMMAND_AUTH_PLACEHOLDER, "'all'")

      writeFileContent(FILE_CHANGE_CART_COMMAND, updatedCommandContent)
    })

    describe('with fields', () => {
      it('should create a new command with fields', async () => {
        await exec(`${cliPath} new:command ChangeCartWithFields --fields cartId:UUID sku:string quantity:number`)

        const expectedCommandContent = await readFileContent('integration/fixtures/commands/ChangeCartWithFields.ts')
        const commandContent = await readFileContent(FILE_CHANGE_CART_WITH_FIELDS_COMMAND)
        expect(commandContent).to.equal(expectedCommandContent)

        // Set command auth
        const updatedCommandContent = commandContent.replace(COMMAND_AUTH_PLACEHOLDER, "'all'")

        writeFileContent(FILE_CHANGE_CART_WITH_FIELDS_COMMAND, updatedCommandContent)
      })
    })
  })

  context('Invalid command', () => {
    describe('missing command name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:command`)

        expect(stderr).to.equal("You haven't provided a command name, but it is required, run with --help for usage\n")
      })
    })
  })
})
