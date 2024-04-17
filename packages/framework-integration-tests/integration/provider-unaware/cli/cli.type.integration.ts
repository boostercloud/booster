import * as path from 'path'
import { expect } from 'chai'
import { loadFixture, readFileContent, removeFolders, sandboxPathFor } from '../../helper/file-helper'
import { exec } from 'child-process-promise'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'

describe('Type', () => {
  let typeSandboxDir: string

  before(async () => {
    typeSandboxDir = createSandboxProject(sandboxPathFor('type'))
  })

  after(async () => {
    await removeFolders([typeSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid type', () => {
    it('should create a new type', async () => {
      const expectedOutputRegex = new RegExp(
        ['boost new:type', 'Verifying project', 'Creating new type', 'Type generated'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} new:type Item`, { cwd: typeSandboxDir })
      expect(stdout).to.match(expectedOutputRegex)

      const expectedTypeContent = loadFixture('common/item.ts')
      const typeContent = readFileContent(`${typeSandboxDir}/src/common/item.ts`)
      expect(typeContent).to.equal(expectedTypeContent)
    })

    describe('with fields', () => {
      it('should create a new type with fields', async () => {
        await exec(`${cliPath} new:type ItemWithFields --fields sku:string quantity:number`, {
          cwd: typeSandboxDir,
        })

        const expectedTypeContent = loadFixture('common/item-with-fields.ts')
        const typeContent = readFileContent(`${typeSandboxDir}/src/common/item-with-fields.ts`)
        expect(typeContent).to.equal(expectedTypeContent)
      })
    })
  })

  context('Invalid type', () => {
    describe('missing type name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:type`, { cwd: typeSandboxDir })

        expect(stderr).to.match(/You haven't provided a type name, but it is required, run with --help for usage/)
      })
    })
  })
})
