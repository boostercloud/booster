import path = require('path')
import { expect } from 'chai'
import { createSandboxProject, loadFixture, readFileContent, removeFolders } from '../helper/fileHelper'
import { exec } from 'child-process-promise'

describe('Type', () => {
  const SANDBOX_INTEGRATION_DIR = 'type-integration-sandbox'
  const FILE_CART_ITEM_TYPE = `${SANDBOX_INTEGRATION_DIR}/src/common/item.ts`
  const FILE_CART_ITEM_WITH_FIELDS_TYPE = `${SANDBOX_INTEGRATION_DIR}/src/common/item-with-fields.ts`

  before(async () => {
    createSandboxProject(SANDBOX_INTEGRATION_DIR)
  })

  after(() => {
    removeFolders([SANDBOX_INTEGRATION_DIR])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid type', () => {
    it('should create a new type', async () => {
      const expectedOutputRegex = new RegExp(
        ['boost new:type', 'Verifying project', 'Creating new type', 'Type generated'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} new:type Item`, { cwd: SANDBOX_INTEGRATION_DIR })
      expect(stdout).to.match(expectedOutputRegex)

      const expectedTypeContent = loadFixture('common/item.ts')
      const typeContent = readFileContent(FILE_CART_ITEM_TYPE)
      expect(typeContent).to.equal(expectedTypeContent)
    })

    describe('with fields', () => {
      it('should create a new type with fields', async () => {
        await exec(`${cliPath} new:type ItemWithFields --fields sku:string quantity:number`, {
          cwd: SANDBOX_INTEGRATION_DIR,
        })

        const expectedTypeContent = loadFixture('common/item-with-fields.ts')
        const typeContent = readFileContent(FILE_CART_ITEM_WITH_FIELDS_TYPE)
        expect(typeContent).to.equal(expectedTypeContent)
      })
    })
  })

  context('Invalid type', () => {
    describe('missing type name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:type`, { cwd: SANDBOX_INTEGRATION_DIR })

        expect(stderr).to.match(/You haven't provided a type name, but it is required, run with --help for usage/)
      })
    })
  })
})
