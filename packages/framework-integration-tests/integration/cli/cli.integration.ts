import { exec } from 'child_process'
import * as chai from 'chai'
import { expect } from 'chai'
import { readFileContent, removeFile } from '../helper/fileHelper'

chai.use(require('chai-as-promised'))

const execAndOutput = (command: string): Promise<string> => {
  return new Promise((resolve, reject) => {
    exec(command, (error, stdout, stderr) => {
      if (error) {
        reject(error)
      }

      if (stderr) {
        reject(stderr)
      }

      resolve(stdout)
    })
  })
}

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
      await execAndOutput('npm run compile --scripts-prepend-node-path')
      await removeFile('src/entities/Post.ts')
      await removeFile('src/entities/PostWithFields.ts')
    })

    context('valid entity', () => {
      describe('without fields', () => {
        it('should create new entity', async () => {
          const newEntityResult = await execAndOutput('boost new:entity Post')
          expect(newEntityResult).to.equal(
            'ℹ boost new:entity 🚧\n- Verifying project\n✔ Verifying project\n- Creating new entity\n✔ Creating new entity\nℹ Entity generated!\n',
          )

          const expectedEntityContent = await readFileContent('test/fixtures/entities/Post.ts')
          const entityContent = await readFileContent('src/entities/Post.ts')
          expect(entityContent).to.equal(expectedEntityContent)
        })
      })

      describe('with fields', () => {
        it('should create new entity with expected fields', async () => {
          const newEntityResult = await execAndOutput(
            'boost new:entity PostWithFields --fields title:string body:string',
          )
          expect(newEntityResult).to.equal(
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
          await expect(execAndOutput('boost new:entity')).to.eventually.be.rejectedWith(
            "You haven't provided an entity name, but it is required, run with --help for usage\n"
          )
        })
      })
    })
  })
})
