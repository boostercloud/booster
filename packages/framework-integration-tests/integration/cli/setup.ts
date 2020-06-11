import * as path from 'path'
import util = require('util')
import { removeFiles, removeFolders } from '../helper/fileHelper'
import { CLI_ENTITY_INTEGRATION_TEST_FILES } from './cli.entity.integration'
import { CLI_COMMAND_INTEGRATION_TEST_FILES } from './cli.command.integration'
import { CLI_TYPE_INTEGRATION_TEST_FILES } from './cli.type.integration'
import { CLI_EVENTS_INTEGRATION_TEST_FILES } from './cli.event.integration'
import { CLI_READ_MODEL_INTEGRATION_TEST_FILES } from './cli.readmodel.integration'
import { CLI_PROJECT_INTEGRATION_TEST_FOLDERS } from './cli.project.integration'

const exec = util.promisify(require('child_process').exec)

const testFiles: Array<string> = [
  ...CLI_ENTITY_INTEGRATION_TEST_FILES,
  ...CLI_COMMAND_INTEGRATION_TEST_FILES,
  ...CLI_TYPE_INTEGRATION_TEST_FILES,
  ...CLI_EVENTS_INTEGRATION_TEST_FILES,
  ...CLI_READ_MODEL_INTEGRATION_TEST_FILES,
]

const testFolders: Array<string> = [
  ...CLI_PROJECT_INTEGRATION_TEST_FOLDERS
]

const removeGeneratedResources = () => {
  return Promise.all([
    ...removeFiles(testFiles),
    ...removeFolders(testFolders),
  ])
}

before(async () => {
  const integrationTestsPackageRoot = path.dirname(__dirname)
  process.chdir(integrationTestsPackageRoot)

  await exec('lerna bootstrap')
  await exec('lerna clean --yes')
  await exec('lerna run clean')

  process.chdir('..')

  try {
    await removeGeneratedResources()
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
    await removeGeneratedResources()
  }
})
