import * as path from 'path'
import util = require('util')
const exec = util.promisify(require('child_process').exec)

before(async () => {
  const integrationTestsPackageRoot = path.dirname(__dirname)
  process.chdir(integrationTestsPackageRoot)

  await exec('lerna bootstrap')
  await exec('lerna clean --yes')
  await exec('lerna run clean')
  await exec('lerna run compile')

  process.chdir('..')
})
