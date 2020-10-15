import path = require('path')
import util = require('util')
import { expect } from 'chai'
import { readFileContent } from '../helper/fileHelper'

const exec = util.promisify(require('child_process').exec)

const FILE_CART_ITEM_TYPE = 'src/common/item.ts'
const FILE_CART_ITEM_WITH_FIELDS_TYPE = 'src/common/item-with-fields.ts'

export const CLI_TYPE_INTEGRATION_TEST_FILES: Array<string> = [FILE_CART_ITEM_TYPE, FILE_CART_ITEM_WITH_FIELDS_TYPE]

describe('Type', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')

  context('Valid type', () => {
    it('should create a new type', async () => {
      const expectedOutputRegex = new RegExp(
        /(.+) boost (.+)?new:type(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new type\n(.+) Creating new type\n(.+) Type generated!\n/
      )

      const { stdout } = await exec(`${cliPath} new:type CartItem`)
      expect(stdout).to.match(expectedOutputRegex)

      const expectedTypeContent = await readFileContent('integration/fixtures/common/cart-item.ts')
      const typeContent = await readFileContent(FILE_CART_ITEM_TYPE)
      expect(typeContent).to.equal(expectedTypeContent)
    })

    describe('with fields', () => {
      it('should create a new type with fields', async () => {
        await exec(`${cliPath} new:type CartItemWithFields --fields sku:string quantity:number`)

        const expectedTypeContent = await readFileContent('integration/fixtures/common/cart-item-with-fields.ts')
        const typeContent = await readFileContent(FILE_CART_ITEM_WITH_FIELDS_TYPE)
        expect(typeContent).to.equal(expectedTypeContent)
      })
    })
  })

  context('Invalid type', () => {
    describe('missing type name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:type`)

        expect(stderr).to.equal("You haven't provided a type name, but it is required, run with --help for usage\n")
      })
    })
  })
})
