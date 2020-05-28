import * as path from 'path'
import util = require('util')
import { removeFile } from '../helper/fileHelper'
import { CLI_ENTITY_INTEGRATION_TEST_FILES } from './cli.entity.integration'

const exec = util.promisify(require('child_process').exec)

const testFiles: Array<string> = [...CLI_ENTITY_INTEGRATION_TEST_FILES]
const removeFiles = testFiles.map((file: string) => removeFile(path.join(path.dirname(__dirname), '..', file)))

before(async () => {
  const integrationTestsPackageRoot = path.dirname(__dirname)
  process.chdir(integrationTestsPackageRoot)

  await exec('lerna bootstrap')
  await exec('lerna clean --yes')
  await exec('lerna run clean')

  process.chdir('..')

  try {
    await Promise.all(removeFiles)
  } catch (e) {
    // error whilst deleting files
  }
})

after(async () => {
  try {
    await exec('lerna run compile')
  } finally {
    await Promise.all(removeFiles)
  }
})
