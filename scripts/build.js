;(async function() {
  try {
    const yargs = require('yargs')
    const exec = (x) => require('./utils').exec(x, { echo: true })
    const mv = require('./utils').mv
    const path = require('path')
    const fs = require('fs')

    const pwd = process.cwd()

    const argv = yargs
      .usage(`Usage: $0 <command>`)
      .demandCommand(1)
      .help().argv

    const ENVIRONMENT = argv._[0]

    switch (ENVIRONMENT) {
      case 'deploy-example':
        const exampleDir = path.join(pwd, 'packages', 'framework-example')
        process.chdir(exampleDir)
        await exec('lerna bootstrap')
        await exec('npx lerna clean --yes')
        await mv(path.join(pwd, 'node_modules'), path.join(pwd, 'node_modules_dev'))
        await exec('yarn install --production --no-bin-links --modules-folder ./node_modules')
        await mv(path.join(pwd, 'node_modules_dev'), path.join(pwd, 'node_modules'))
        await exec('npx lerna run clean')
        await exec('npx lerna run compile')
        fs.rmdirSync(path.join(exampleDir, 'node_modules', '@boostercloud', 'framework-example'))
        fs.rmdirSync(path.join(exampleDir, 'node_modules', '@boostercloud', 'cli'))
        await exec(path.join(pwd, 'packages', 'cli', 'bin', 'run') + ' deploy')
        break

      case 'all-checks':
        BUILD_OPTIONS = '--config config/webpack.staging.js'
        break

      case 'clean-dist':
        break

      default:
        throw new Error(`ERROR: wrong command as first argument.
        Available are:

        * deploy-example
        * all-checks
        * clean-dist`)
    }
  } catch (error) {
    console.error(error)
  }
})()
