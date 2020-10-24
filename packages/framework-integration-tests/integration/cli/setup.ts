import * as path from 'path'
import util = require('util')
import { removeFiles } from '../helper/fileHelper'
import { CLI_ENTITY_INTEGRATION_TEST_FILES } from './cli.entity.integration'
import { CLI_COMMAND_INTEGRATION_TEST_FILES } from './cli.command.integration'
import { CLI_TYPE_INTEGRATION_TEST_FILES } from './cli.type.integration'
import { CLI_EVENTS_INTEGRATION_TEST_FILES } from './cli.event.integration'
import { CLI_READ_MODEL_INTEGRATION_TEST_FILES } from './cli.readmodel.integration'
import { CLI_EVENT_HANDLERS_INTEGRATION_TEST_FILES } from './cli.event-handler.integration'
import { CLI_SCHEDULED_COMMAND_INTEGRATION_TEST_FILES } from './cli.scheduled-command.integration'

const exec = util.promisify(require('child_process').exec)

const testFiles: Array<string> = [
  ...CLI_ENTITY_INTEGRATION_TEST_FILES,
  ...CLI_COMMAND_INTEGRATION_TEST_FILES,
  ...CLI_TYPE_INTEGRATION_TEST_FILES,
  ...CLI_EVENTS_INTEGRATION_TEST_FILES,
  ...CLI_READ_MODEL_INTEGRATION_TEST_FILES,
  ...CLI_EVENT_HANDLERS_INTEGRATION_TEST_FILES,
  ...CLI_SCHEDULED_COMMAND_INTEGRATION_TEST_FILES,
]

const integrationTestsPackageRoot = path.dirname(__dirname)

before(async () => {
  await exec('lerna bootstrap', { cwd: integrationTestsPackageRoot })
  await exec('lerna clean --yes', { cwd: integrationTestsPackageRoot })
  await exec('lerna run clean --stream', { cwd: integrationTestsPackageRoot })

  // Try deleting files just in case we're re-running the tests and there are
  // rests from previous runs.
  removeFiles(testFiles, true)
})

after(async () => {
  try {
    await exec('lerna run compile --stream', { cwd: integrationTestsPackageRoot })
  } finally {
    // Make sure we clean unuseful files
    removeFiles(testFiles)
  }
})
