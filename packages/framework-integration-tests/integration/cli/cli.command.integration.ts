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
  let commandSandboxDir: string

  before(async () => {
    commandSandboxDir = await createSandboxProject('command')
  })

  after(() => {
    removeFolders([commandSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid command', () => {
    it('should create a new command', async () => {
      const changeCartCommandPath = `${commandSandboxDir}/src/commands/change-cart.ts`

      const expectedOutputRegex = new RegExp(
        ['boost new:command', 'Verifying project', 'Creating new command', 'Command generated'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} new:command ChangeCart`, { cwd: commandSandboxDir })
      expect(stdout).to.match(expectedOutputRegex)

      const expectedCommandContent = loadFixture('commands/change-cart.ts')
      const commandContent = readFileContent(changeCartCommandPath)
      expect(commandContent).to.equal(expectedCommandContent)

      // Set command auth
      const updatedCommandContent = commandContent.replace(COMMAND_AUTH_PLACEHOLDER, "'all'")

      writeFileContent(changeCartCommandPath, updatedCommandContent)
    })

    describe('with fields', () => {
      it('should create a new command with fields', async () => {
        const changeCartWithFieldsCommandPath = `${commandSandboxDir}/src/commands/change-cart-with-fields.ts`

        await exec(`${cliPath} new:command ChangeCartWithFields --fields cartId:UUID sku:string quantity:number`, {
          cwd: commandSandboxDir,
        })

        const expectedCommandContent = loadFixture('commands/change-cart-with-fields.ts')
        const commandContent = readFileContent(changeCartWithFieldsCommandPath)
        expect(commandContent).to.equal(expectedCommandContent)

        // Set command auth
        const updatedCommandContent = commandContent.replace(COMMAND_AUTH_PLACEHOLDER, "'all'")

        writeFileContent(changeCartWithFieldsCommandPath, updatedCommandContent)
      })
    })
  })

  context('Invalid command', () => {
    describe('missing command name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:command`, { cwd: commandSandboxDir })

        expect(stderr).to.match(/You haven't provided a command name, but it is required, run with --help for usage/)
      })
    })
  })
})
