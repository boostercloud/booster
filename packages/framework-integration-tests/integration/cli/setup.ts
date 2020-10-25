import * as path from 'path'
import util = require('util')

const exec = util.promisify(require('child_process').exec)

const integrationTestsPackageRoot = path.dirname(__dirname)

before(async () => {
  await exec('lerna bootstrap', { cwd: integrationTestsPackageRoot })
  await exec('lerna clean --yes', { cwd: integrationTestsPackageRoot })
  await exec('lerna run clean --stream', { cwd: integrationTestsPackageRoot })
})
