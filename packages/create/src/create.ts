#!/usr/bin/env node
import { OptionValues, program } from 'commander';
const packageJSON = require('../package.json');

// Parse the input using Commander.js
//
// Typical usage: npm create @boostercloud <app-name> --template githubuser/repo
program
  .version(packageJSON.version)
  .argument('<project-name>')
  .option('-t, --template <template>', 'Template to use for the project', 'boostercloud/base-template')
  .description('Creates a new Booster project using the given name. If no template is provided it will use the default (boostercloud/base-template)')
  .action((projectName: string, options: OptionValues) => {
    console.log(`Creating a new Booster project ${projectName} using the template ${options.template}`)
  })
  .showHelpAfterError()
  .parse()