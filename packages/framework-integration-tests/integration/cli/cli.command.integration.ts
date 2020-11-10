import * as path from 'path'
import { expect } from 'chai'
import {
  createSandboxProject,
  loadFixture,
  readFileContent,
  removeFolders,
  writeFileContent,
} from '../helper/fileHelper'
import { exec } from 'child-process-promise'

const COMMAND_AUTH_PLACEHOLDER = "// Specify authorized roles here. Use 'all' to authorize anyone"

describe('Command', () => {
  const SANDBOX_INTEGRATION_DIR = 'command-integration-sandbox'
  const FILE_CHANGE_CART_COMMAND = `${SANDBOX_INTEGRATION_DIR}/src/commands/change-cart.ts`
  const FILE_CHANGE_CART_WITH_FIELDS_COMMAND = `${SANDBOX_INTEGRATION_DIR}/src/commands/change-cart-with-fields.ts`

  before(async () => {
    createSandboxProject(SANDBOX_INTEGRATION_DIR)
  })

  after(() => {
    removeFolders([SANDBOX_INTEGRATION_DIR])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid command', () => {
    it('should create a new command', async () => {
      const expectedOutputRegex = new RegExp(
        ['boost new:command', 'Verifying project', 'Creating new command', 'Command generated'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} new:command ChangeCart`, { cwd: SANDBOX_INTEGRATION_DIR })
      expect(stdout).to.match(expectedOutputRegex)

      const expectedCommandContent = loadFixture('commands/change-cart.ts')
      const commandContent = readFileContent(FILE_CHANGE_CART_COMMAND)
      expect(commandContent).to.equal(expectedCommandContent)

      // Set command auth
      const updatedCommandContent = commandContent.replace(COMMAND_AUTH_PLACEHOLDER, "'all'")

      writeFileContent(FILE_CHANGE_CART_COMMAND, updatedCommandContent)
    })

    describe('with fields', () => {
      it('should create a new command with fields', async () => {
        await exec(`${cliPath} new:command ChangeCartWithFields --fields cartId:UUID sku:string quantity:number`, {
          cwd: SANDBOX_INTEGRATION_DIR,
        })

        const expectedCommandContent = loadFixture('commands/change-cart-with-fields.ts')
        const commandContent = readFileContent(FILE_CHANGE_CART_WITH_FIELDS_COMMAND)
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
        const { stderr } = await exec(`${cliPath} new:command`, { cwd: SANDBOX_INTEGRATION_DIR })

        expect(stderr).to.match(/You haven't provided a command name, but it is required, run with --help for usage/)
      })
    })
  })
})
