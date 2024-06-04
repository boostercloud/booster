import * as path from 'path'
import { expect } from 'chai'
import { sandboxPathFor, removeFolders, fileExists } from '../../helper/file-helper'
import { exec } from 'child-process-promise'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'
import * as fs from 'fs'

describe('Build', () => {
  let buildSandboxDir: string

  before(async () => {
    buildSandboxDir = createSandboxProject(sandboxPathFor('build'))
  })

  after(async () => {
    await removeFolders([buildSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('Valid build', () => {
    it('should build the project', async () => {
      const expectedOutputRegex = new RegExp(
        ['boost build', 'Checking project structure', 'Building project', 'Build complete'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} build`, { cwd: buildSandboxDir })

      expect(stdout).to.match(expectedOutputRegex)
      expect(fileExists(path.join(buildSandboxDir, 'dist', 'index.js'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir, 'dist', 'index.d.ts'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir, 'dist', 'roles.js'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir, 'dist', 'roles.d.ts'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir, 'dist', 'config', 'config.js'))).to.be.true
      expect(fileExists(path.join(buildSandboxDir, 'dist', 'config', 'config.d.ts'))).to.be.true
    })
  })
})

describe('Compile fallback', () => {
  let compileSandboxDir: string

  before(async () => {
    compileSandboxDir = createSandboxProject(sandboxPathFor('compile'))

    // Add a 'compile' script to the package.json
    const packageJsonPath = path.join(compileSandboxDir, 'package.json')
    const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'))
    packageJson.scripts.compile = 'echo "eureka" > eureka'
    fs.writeFileSync(packageJsonPath, JSON.stringify(packageJson, null, 2))
  })

  after(async () => {
    await removeFolders([compileSandboxDir])
  })

  const cliPath = path.join('..', '..', 'cli', 'bin', 'run')

  context('when a compile script is present', () => {
    it('should call the compile script instead', async () => {
      const expectedOutputRegex = new RegExp(
        ['boost build', 'Checking project structure', 'Building project', 'Build complete'].join('(.|\n)*')
      )

      const { stdout } = await exec(`${cliPath} build`, { cwd: compileSandboxDir })

      expect(stdout).to.match(expectedOutputRegex)
      expect(fileExists(path.join(compileSandboxDir, 'eureka'))).to.be.true
    })
  })
})
