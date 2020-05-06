import { exec } from 'child_process'
import { expect } from 'chai'

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
    context('valid entity', () => {
      describe('without fields', () => {
        it('should create new entity', async () => {
          const newEntityResult = await execAndOutput('boost new:entity Post')
          expect(newEntityResult).to.equal(
            'ℹ boost new:entity 🚧\n- Verifying project\n✔ Verifying project\n- Creating new entity\n✔ Creating new entity\nℹ Entity generated!\n',
          )

          const expectedEntityContent = await execAndOutput('cat test/fixtures/entities/Post.ts')
          const entityContent = await execAndOutput('cat src/entities/Post.ts')
          expect(entityContent).to.equal(expectedEntityContent)
        })
      })

      describe('with fields', () => {
        it('should create new entity with expected fields', async () => {
          const newEntityResult = await execAndOutput('boost new:entity Post --fields title:string body:string')
          expect(newEntityResult).to.equal(
            'ℹ boost new:entity 🚧\n- Verifying project\n✔ Verifying project\n- Creating new entity\n✔ Creating new entity\nℹ Entity generated!\n',
          )

          const expectedEntityContent = await execAndOutput('cat test/fixtures/entities/Post_with_fields.ts')
          const entityContent = await execAndOutput('cat src/entities/Post.ts')
          expect(entityContent).to.equal(expectedEntityContent)
        })
      })
    })

    context('invalid entity', () => {
      describe('missing entity name', () => {
        it('should fail', async () => {
          let error: any

          await execAndOutput('boost new:entity')
            .catch((e) => {
              error = e
            })
            .finally(() => {
              expect(error).to.equal(
                "You haven't provided an entity name, but it is required, run with --help for usage\n"
              )
            })
        })
      })
    })
  })
})