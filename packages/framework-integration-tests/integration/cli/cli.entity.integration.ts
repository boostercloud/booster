import util = require('util')

const exec = util.promisify(require('child_process').exec)
import { expect } from 'chai'
import { readFileContent, writeFileContent } from '../helper/fileHelper'
import path = require('path')

const EVENT_ENTITY_ID_PLACEHOLDER = '/* the associated entity ID */'
const ENTITY_REDUCER_PLACEHOLDER = '/* NEW PostWithReducer HERE */'

const FILE_POST_ENTITY = 'src/entities/Post.ts'
const FILE_POST_WITH_FIELDS_ENTITY = 'src/entities/PostWithFields.ts'
const FILE_POST_WITH_REDUCER_ENTITY = 'src/entities/PostWithReducer.ts'
const FILE_POST_CREATED_EVENT = 'src/events/PostCreated.ts'

export const CLI_ENTITY_INTEGRATION_TEST_FILES: Array<string> = [
  FILE_POST_ENTITY,
  FILE_POST_WITH_FIELDS_ENTITY,
  FILE_POST_WITH_REDUCER_ENTITY,
  FILE_POST_CREATED_EVENT,
]

describe('Entity', () => {
  const cliPath = path.join('..', 'cli', 'bin', 'run')

  context('valid entity', () => {
    describe('without fields', () => {
      it('should create new entity', async () => {
        const expectedOutputRegex = new RegExp(
          /(.+) boost (.+)?new:entity(.+)? (.+)\n- Verifying project\n(.+) Verifying project\n- Creating new entity\n(.+) Creating new entity\n(.+) Entity generated!\n/
        )
        const { stdout } = await exec(`${cliPath} new:entity Post`)
        expect(stdout).to.match(expectedOutputRegex)

        const expectedEntityContent = await readFileContent('integration/fixtures/entities/Post.ts')
        const entityContent = await readFileContent(FILE_POST_ENTITY)
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

        const expectedEntityContent = await readFileContent('integration/fixtures/entities/PostWithFields.ts')
        const entityContent = await readFileContent(FILE_POST_WITH_FIELDS_ENTITY)
        expect(entityContent).to.equal(expectedEntityContent)
      })
    })
  })

  context('invalid entity', () => {
    describe('missing entity name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:entity`)

        expect(stderr).to.equal("You haven't provided an entity name, but it is required, run with --help for usage\n")
      })
    })
  })

  describe('entity with reducer', () => {
    it('should create new entity with reducer', async () => {
      // Create event
      await exec(`${cliPath} new:event PostCreated --fields postId:UUID title:string body:string`)
      const expectedEventContent = await readFileContent('integration/fixtures/events/PostCreated.ts')
      const eventContent = await readFileContent(FILE_POST_CREATED_EVENT)
      expect(eventContent).to.equal(expectedEventContent)

      // Set event entity ID
      const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, 'this.postId')

      await writeFileContent('src/events/PostCreated.ts', updatedEventContent)

      // Create entity
      await exec(`${cliPath} new:entity PostWithReducer --fields title:string body:string --reduces PostCreated`)
      const expectedEntityContent = await readFileContent('integration/fixtures/entities/PostWithReducer.ts')
      const entityContent = await readFileContent(FILE_POST_WITH_REDUCER_ENTITY)
      expect(entityContent).to.equal(expectedEntityContent)

      // Set reducer response
      const updatedEntityContent = entityContent.replace(
        ENTITY_REDUCER_PLACEHOLDER,
        'new PostWithReducer(event.postId, event.title, event.body)'
      )

      await writeFileContent(FILE_POST_WITH_REDUCER_ENTITY, updatedEntityContent)
    })
  })
})
