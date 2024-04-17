import * as path from 'path'
import { expect } from 'chai'
import { loadFixture, readFileContent, removeFolders, sandboxPathFor } from '../../helper/file-helper'
import { exec } from 'child-process-promise'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'

describe('Event handler', () => {
  let eventHandlerSandboxDir: string

  before(async () => {
    eventHandlerSandboxDir = createSandboxProject(sandboxPathFor('event-handler'))
  })

  after(async () => {
    await removeFolders([eventHandlerSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  describe('Valid event handler', () => {
    it('should create new event handler', async () => {
      const expectedOutputRegex = new RegExp(
        ['boost new:event-handler', 'Verifying project', 'Creating new event handler', 'Event handler generated'].join(
          '(.|\n)*'
        )
      )

      const { stdout } = await exec(`${cliPath} new:event-handler HandleCartChange -e CartItemChanged`, {
        cwd: eventHandlerSandboxDir,
      })
      expect(stdout).to.match(expectedOutputRegex)

      const expectedEventContent = loadFixture('event-handlers/handle-cart-change.ts')
      const eventContent = readFileContent(`${eventHandlerSandboxDir}/src/event-handlers/handle-cart-change.ts`)

      expect(eventContent).to.equal(expectedEventContent)
    })
  })

  describe('Invalid event handler', () => {
    context('without name and event', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:event-handler`, { cwd: eventHandlerSandboxDir })

        expect(stderr).to.match(
          /You haven't provided an event handler name, but it is required, run with --help for usage/
        )
      })
    })

    context('Without name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:event-handler -e CartPaid`, { cwd: eventHandlerSandboxDir })

        expect(stderr).to.match(
          /You haven't provided an event handler name, but it is required, run with --help for usage/
        )
      })
    })

    context('Without event', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:event-handler CartPaid`, { cwd: eventHandlerSandboxDir })

        expect(stderr).to.match(/You haven't provided an event, but it is required, run with --help for usage/)
      })
    })
  })
})
