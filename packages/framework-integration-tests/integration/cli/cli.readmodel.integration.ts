import { expect } from 'chai'

import path = require('path')
import util = require('util')
import { readFileContent, writeFileContent } from '../helper/fileHelper'
const exec = util.promisify(require('child_process').exec)

const READ_MODEL_AUTH_PLACEHOLDER = "// Specify authorized roles here. Use 'all' to authorize anyone"
const READ_MODEL_PROJECTION_PLACEHOLDER = '/* NEW CartWithProjectionReadModel HERE */'

const FILE_CART_READ_MODEL = 'src/read-models/cart-read-model.ts'
const FILE_CART_WITH_FIELDS_READ_MODEL = 'src/read-models/cart-with-fields-read-model.ts'
const FILE_CART_WITH_PROJECTION_READ_MODEL = 'src/read-models/cart-with-projection-read-model.ts'

export const CLI_READ_MODEL_INTEGRATION_TEST_FILES: Array<string> = [
  FILE_CART_READ_MODEL,
  FILE_CART_WITH_FIELDS_READ_MODEL,
  FILE_CART_WITH_PROJECTION_READ_MODEL,
]
describe('Read model', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')
  const EXPECTED_OUTPUT_REGEX = new RegExp(
    /(.+) boost (.+)?new:read-model(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new read model\n(.+) Creating new read model\n(.+) Read model generated!\n/
  )

  context('valid read model', () => {
    describe('without fields', () => {
      it('should create new read model', async () => {
        const { stdout } = await exec(`${cliPath} new:read-model CartReadModel`)
        expect(stdout).to.match(EXPECTED_OUTPUT_REGEX)

        const expectedEntityContent = await readFileContent('integration/fixtures/read-models/cart-read-model.ts')
        const entityContent = await readFileContent(FILE_CART_READ_MODEL)
        expect(entityContent).to.equal(expectedEntityContent)

        // set Auth
        const updatedReadModelContent = entityContent.replace(READ_MODEL_AUTH_PLACEHOLDER, "'all'")

        writeFileContent(FILE_CART_READ_MODEL, updatedReadModelContent)
      })
    })

    describe('with fields', () => {
      it('should create new read model', async () => {
        const { stdout } = await exec(
          cliPath + " new:read-model CartWithFieldsReadModel --fields 'items:Array<CartItem>'"
        )
        expect(stdout).to.match(EXPECTED_OUTPUT_REGEX)

        const expectedEntityContent = await readFileContent(
          'integration/fixtures/read-models/cart-with-fields-read-model.ts'
        )
        const entityContent = await readFileContent(FILE_CART_WITH_FIELDS_READ_MODEL)
        expect(entityContent).to.equal(expectedEntityContent)

        // set Auth
        let updatedReadModelContent = entityContent.replace(READ_MODEL_AUTH_PLACEHOLDER, "'all'")

        // Add CartItem import
        updatedReadModelContent = `import { CartItem } from '../common/cart-item'\n${updatedReadModelContent}`

        writeFileContent(FILE_CART_WITH_FIELDS_READ_MODEL, updatedReadModelContent)
      })
    })

    describe('with projection', () => {
      it('should create new read model', async () => {
        const { stdout } = await exec(
          cliPath + " new:read-model CartWithProjectionReadModel --fields 'items:Array<CartItem>' --projects Cart:id"
        )
        expect(stdout).to.match(EXPECTED_OUTPUT_REGEX)

        const expectedEntityContent = await readFileContent(
          'integration/fixtures/read-models/cart-with-projection-read-model.ts'
        )
        const entityContent = await readFileContent(FILE_CART_WITH_PROJECTION_READ_MODEL)
        expect(entityContent).to.equal(expectedEntityContent)

        // set Auth
        let updatedReadModelContent = entityContent.replace(READ_MODEL_AUTH_PLACEHOLDER, "'all'")

        // Set projection return
        updatedReadModelContent = updatedReadModelContent.replace(
          READ_MODEL_PROJECTION_PLACEHOLDER,
          'new CartWithProjectionReadModel(entity.id, entity.cartItems)'
        )

        // Add CartItem import
        updatedReadModelContent = `import { CartItem } from '../common/cart-item'\n${updatedReadModelContent}`

        writeFileContent(FILE_CART_WITH_PROJECTION_READ_MODEL, updatedReadModelContent)
      })
    })
  })

  context('invalid read model', () => {
    describe('missing read model name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:read-model`)

        expect(stderr).to.equal(
          "You haven't provided a read model name, but it is required, run with --help for usage\n"
        )
      })
    })
  })
})
