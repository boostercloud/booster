/*
 *     Deploys the example project in the `framework-integration-tests`
 *     package. Run it with npx from the project root as follows:
 *
 *      npx ./scripts/deploy-example-project-to-aws.js
 *
 */

async function main(argv) {
  const exec = require('child-process-promise').exec
  console.info('Compiling project...')
  await exec('lerna run compile')
  console.info('Project compiled.')

  const deployScript = require('../packages/framework-integration-tests/dist/deploy')
  if (argv[2] === 'KILLITWITHFIRE') {
    return deployScript.nuke()
  }
  return deployScript.deploy()
}

main(process.argv)
