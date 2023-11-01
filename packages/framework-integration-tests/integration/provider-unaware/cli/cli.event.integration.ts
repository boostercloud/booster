import * as path from 'path'
import { expect } from 'chai'
import {
  loadFixture,
  readFileContent,
  removeFiles,
  removeFolders,
  sandboxPathFor,
  writeFileContent,
} from '../../helper/file-helper'
import { exec } from 'child-process-promise'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'

const EVENT_ENTITY_ID_PLACEHOLDER = '/* the associated entity ID */'

describe('Event', () => {
  let eventSandboxDir: string

  before(async () => {
    eventSandboxDir = createSandboxProject(sandboxPathFor('event'))
  })

  after(async () => {
    await removeFolders([eventSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid event', () => {
    it('should create new event', async () => {
      const expectedOutputRegex = new RegExp(
        ['boost new:event', 'Verifying project', 'Creating new event', 'Event generated'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} new:event CartChanged`, { cwd: eventSandboxDir })
      expect(stdout).to.match(expectedOutputRegex)
    })

    describe('without fields', () => {
      it('should create new event', async () => {
        const FILE_CART_CHANGED_EVENT = `${eventSandboxDir}/src/events/cart-changed.ts`
        removeFiles([FILE_CART_CHANGED_EVENT])

        await exec(`${cliPath} new:event CartChanged`, { cwd: eventSandboxDir })

        const expectedEventContent = loadFixture('events/cart-changed.ts')
        const eventContent = readFileContent(FILE_CART_CHANGED_EVENT)
        expect(eventContent).to.equal(expectedEventContent)

        // Set event entity ID
        const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, "'some-id'")

        writeFileContent(FILE_CART_CHANGED_EVENT, updatedEventContent)
      })
    })

    describe('with fields', () => {
      it('should create new event', async () => {
        const FILE_CART_CHANGED_WITH_FIELDS_EVENT = `${eventSandboxDir}/src/events/cart-changed-with-fields.ts`

        await exec(`${cliPath} new:event CartChangedWithFields --fields cartId:UUID sku:string quantity:number`, {
          cwd: eventSandboxDir,
        })

        const expectedEventContent = loadFixture('events/cart-changed-with-fields.ts')
        const eventContent = readFileContent(FILE_CART_CHANGED_WITH_FIELDS_EVENT)
        expect(eventContent).to.equal(expectedEventContent)

        // Set event entity ID
        const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, 'this.cartId')

        writeFileContent(FILE_CART_CHANGED_WITH_FIELDS_EVENT, updatedEventContent)
      })
    })
  })

  context('Invalid event', () => {
    describe('missing event name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:event`, { cwd: eventSandboxDir })

        expect(stderr).to.match(/You haven't provided an event name, but it is required, run with --help for usage/)
      })
    })
  })
})
