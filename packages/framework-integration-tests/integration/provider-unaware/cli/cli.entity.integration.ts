import { expect } from 'chai'
import { loadFixture, readFileContent, removeFolders, sandboxPathFor, writeFileContent } from '../../helper/file-helper'
import * as path from 'path'
import { command } from 'execa'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'

const EVENT_ENTITY_ID_PLACEHOLDER = '/* the associated entity ID */'
const ENTITY_REDUCER_PLACEHOLDER = '/* NEW PostWithReducer HERE */'

describe('Entity', () => {
  let entitySandboxDir: string

  before(async () => {
    entitySandboxDir = createSandboxProject(sandboxPathFor('entity'))
  })

  after(async () => {
    await removeFolders([entitySandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('valid entity', () => {
    describe('without fields', () => {
      it('should create new entity', async () => {
        const expectedOutputRegex = new RegExp(
          ['boost new:entity', 'Verifying project', 'Creating new entity', 'Entity generated'].join('(.|\n)*'),
          'm'
        )
        const { stdout } = await command(`${cliPath} new:entity Post`, { cwd: entitySandboxDir })
        expect(stdout).to.match(expectedOutputRegex)

        const expectedEntityContent = readFileContent('integration/fixtures/entities/post.ts')
        const entityContent = readFileContent(`${entitySandboxDir}/src/entities/post.ts`)
        expect(entityContent).to.equal(expectedEntityContent)
      })
    })

    describe('with fields', () => {
      it('should create new entity with expected fields', async () => {
        const expectedOutputRegex = new RegExp(
          ['boost new:entity', 'Verifying project', 'Creating new entity', 'Entity generated'].join('(.|\n)*'),
          'm'
        )
        const { stdout } = await command(`${cliPath} new:entity PostWithFields --fields title:string body:string`, {
          cwd: entitySandboxDir,
        })
        expect(stdout).to.match(expectedOutputRegex)

        const expectedEntityContent = readFileContent('integration/fixtures/entities/post-with-fields.ts')
        const entityContent = readFileContent(`${entitySandboxDir}/src/entities/post-with-fields.ts`)
        expect(entityContent).to.equal(expectedEntityContent)
      })
    })

    describe('with reducer', () => {
      it('should create new entity with reducer', async () => {
        const FILE_POST_WITH_REDUCER_ENTITY = `${entitySandboxDir}/src/entities/post-with-reducer.ts`
        const FILE_POST_CREATED_EVENT = `${entitySandboxDir}/src/events/post-created.ts`

        // Create event
        await command(`${cliPath} new:event PostCreated --fields postId:UUID title:string body:string`, {
          cwd: entitySandboxDir,
        })

        const expectedEventContent = loadFixture('events/post-created.ts')
        const eventContent = readFileContent(FILE_POST_CREATED_EVENT)
        expect(eventContent).to.equal(expectedEventContent)

        // Set event entity ID
        const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, 'this.postId')

        writeFileContent(FILE_POST_CREATED_EVENT, updatedEventContent)

        // Create entity
        await command(`${cliPath} new:entity PostWithReducer --fields title:string body:string --reduces PostCreated`, {
          cwd: entitySandboxDir,
        })
        const expectedEntityContent = loadFixture('entities/post-with-reducer.ts')
        const entityContent = readFileContent(FILE_POST_WITH_REDUCER_ENTITY)
        expect(entityContent).to.equal(expectedEntityContent)

        // Set reducer response
        const updatedEntityContent = entityContent.replace(
          ENTITY_REDUCER_PLACEHOLDER,
          'new PostWithReducer(event.postId, event.title, event.body)'
        )

        writeFileContent(FILE_POST_WITH_REDUCER_ENTITY, updatedEntityContent)
      })
    })
  })

  context('invalid entity', () => {
    describe('missing entity name', () => {
      it('should fail', async () => {
        const { stderr } = await command(`${cliPath} new:entity`, { cwd: entitySandboxDir })

        expect(stderr).to.match(/You haven't provided an entity name, but it is required, run with --help for usage/m)
      })
    })
  })
})
