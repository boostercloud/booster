import * as path from 'path'
import util = require('util')
import { removeFiles } from '../helper/fileHelper'
import { CLI_ENTITY_INTEGRATION_TEST_FILES } from './cli.entity.integration'
import { CLI_COMMAND_INTEGRATION_TEST_FILES } from './cli.command.integration'
import { CLI_READ_MODEL_INTEGRATION_TEST_FILES } from './cli.readmodel.integration'

const exec = util.promisify(require('child_process').exec)

const testFiles: Array<string> = [
  ...CLI_ENTITY_INTEGRATION_TEST_FILES,
  ...CLI_COMMAND_INTEGRATION_TEST_FILES,
  ...CLI_READ_MODEL_INTEGRATION_TEST_FILES,
]

before(async () => {
  const integrationTestsPackageRoot = path.dirname(__dirname)
  process.chdir(integrationTestsPackageRoot)

  await exec('lerna bootstrap')
  await exec('lerna clean --yes')
  await exec('lerna run clean')

  process.chdir('..')

  try {
    await Promise.all(removeFiles(testFiles))
  } catch (e) {
    // error whilst deleting files
  }
})

after(async () => {
  try {
    await exec('lerna run compile')
  } catch (e) {
    // error whilst deleting files
    console.log(e)
  } finally {
    await Promise.all(removeFiles(testFiles))
  }
})
