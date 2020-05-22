import util = require('util')

const exec = util.promisify(require('child_process').exec)
import { expect } from 'chai'
import { readFileContent, removeFile, writeFileContent } from '../helper/fileHelper'
import path = require('path')

const EVENT_ENTITY_ID_PLACEHOLDER = '/* the associated entity ID */'
const ENTITY_REDUCER_PLACEHOLDER = '/* NEW PostWithReducer HERE */'

describe('cli', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')
  const files: string[] = [
    'src/entities/Post.ts',
    'src/entities/PostWithFields.ts',
    'src/entities/PostWithReducer.ts',
    'src/events/PostCreated.ts',
  ]

  describe('new entity', () => {
    before(async () => {
      try {
        await Promise.all(files.map(removeFile))
      } catch (e) {
        // error whilst deleting files
      }
    })

    after(async () => {
      try {
        await exec('lerna run compile')
      } finally {
        await Promise.all([files.map(await removeFile)])
      }
    })

    context('valid entity', () => {
      describe('without fields', () => {
        it('should create new entity', async () => {
          const expectedOutputRegex = new RegExp(
            /(.+) boost (.+)?new:entity(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new entity\n(.+) Creating new entity\n(.+) Entity generated!\n/
          )
          const { stdout } = await exec(`${cliPath} new:entity Post`)
          expect(stdout).to.match(expectedOutputRegex)

          const expectedEntityContent = await readFileContent('test/fixtures/entities/Post.ts')
          const entityContent = await readFileContent('src/entities/Post.ts')
          expect(entityContent).to.equal(expectedEntityContent)
        })
      })

      describe('with fields', () => {
        it('should create new entity with expected fields', async () => {
          const expectedOutputRegex = new RegExp(
            /(.+) boost (.+)?new:entity(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new entity\n(.+) Creating new entity\n(.+) Entity generated!\n/
          )
          const { stdout } = await exec(`${cliPath} new:entity PostWithFields --fields title:string body:string`)
          expect(stdout).to.match(expectedOutputRegex)

          const expectedEntityContent = await readFileContent('test/fixtures/entities/PostWithFields.ts')
          const entityContent = await readFileContent('src/entities/PostWithFields.ts')
          expect(entityContent).to.equal(expectedEntityContent)
        })
      })
    })

    context('invalid entity', () => {
      describe('missing entity name', () => {
        it('should fail', async () => {
          const { stderr } = await exec(`${cliPath} new:entity`)

          expect(stderr).to.equal(
            "You haven't provided an entity name, but it is required, run with --help for usage\n"
          )
        })
      })
    })

    describe('with reducer', () => {
      it('should create new entity with reducer', async () => {
        // Create event
        await exec(`${cliPath} new:event PostCreated --fields postId:UUID title:string body:string`)
        const eventContent = await readFileContent('src/events/PostCreated.ts')
        const expectedEventContent = await readFileContent('test/fixtures/events/PostCreated.ts')
        expect(eventContent).to.equal(expectedEventContent)

        // Set event entity ID
        const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, 'this.postId')

        await writeFileContent('src/events/PostCreated.ts', updatedEventContent)

        // Create entity
        await exec(`${cliPath} new:entity PostWithReducer --fields title:string body:string --reduces PostCreated`)
        const entityContent = await readFileContent('src/entities/PostWithReducer.ts')
        const expectedEntityContent = await readFileContent('test/fixtures/entities/PostWithReducer.ts')
        expect(entityContent).to.equal(expectedEntityContent)

        // Set reducer response
        const updatedEntityContent = entityContent.replace(
          ENTITY_REDUCER_PLACEHOLDER,
          'new PostWithReducer(event.postId, event.title, event.body)'
        )

        await writeFileContent('src/entities/PostWithReducer.ts', updatedEntityContent)
      })
    })
  })
})
