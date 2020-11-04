import * as path from 'path'
import util = require('util')

const exec = util.promisify(require('child_process').exec)

const integrationTestsPackageRoot = path.dirname(__dirname)

before(async () => {
  await exec('lerna clean --yes && lerna bootstrap && lerna run clean && lerna run compile', {
    cwd: integrationTestsPackageRoot,
  })
})
