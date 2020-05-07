import util = require('util')
const exec = util.promisify(require('child_process').exec)
import * as chai from 'chai'
import { expect } from 'chai'
import { readFileContent, removeFile } from '../helper/fileHelper'

chai.use(require('chai-as-promised'))

describe('cli', () => {
  describe('new entity', () => {
    before(async () => {
      try {
        await removeFile('src/entities/Post.ts')
        await removeFile('src/entities/PostWithFields.ts')
      } catch (e) {
        // error whilst deleting file
      }
    })

    after(async () => {
      await exec('npm run compile --scripts-prepend-node-path')
      await removeFile('src/entities/Post.ts')
      await removeFile('src/entities/PostWithFields.ts')
    })

    context('valid entity', () => {
      describe('without fields', () => {
        it('should create new entity', async () => {
          const { stdout } = await exec('boost new:entity Post')
          expect(stdout).to.equal(
            'ℹ boost new:entity 🚧\n- Verifying project\n✔ Verifying project\n- Creating new entity\n✔ Creating new entity\nℹ Entity generated!\n',
          )

          const expectedEntityContent = await readFileContent('test/fixtures/entities/Post.ts')
          const entityContent = await readFileContent('src/entities/Post.ts')
          expect(entityContent).to.equal(expectedEntityContent)
        })
      })

      describe('with fields', () => {
        it('should create new entity with expected fields', async () => {
          const { stdout } = await exec('boost new:entity PostWithFields --fields title:string body:string')
          expect(stdout).to.equal(
            'ℹ boost new:entity 🚧\n- Verifying project\n✔ Verifying project\n- Creating new entity\n✔ Creating new entity\nℹ Entity generated!\n',
          )

          const expectedEntityContent = await readFileContent('test/fixtures/entities/Post_with_fields.ts')
          const entityContent = await readFileContent('src/entities/PostWithFields.ts')
          expect(entityContent).to.equal(expectedEntityContent)
        })
      })
    })

    context('invalid entity', () => {
      describe('missing entity name', () => {
        it('should fail', async () => {
          const { stderr } = await exec('boost new:entity')

          expect(stderr).to.equal(
            "You haven't provided an entity name, but it is required, run with --help for usage\n"
          )
        })
      })
    })
  })
})
