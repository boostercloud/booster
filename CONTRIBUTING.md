# Contributing to Booster

Thanks for taking the time to contribute to Booster. It is an open-source project and it wouldn't be possible without people like you 🙏🎉

This document is a set of guidelines to help you contribute to Booster, which is hosted on the [`boostercloud`](https://github.com/boostercloud) GitHub
organization. These aren’t absolute laws, use your judgment and common sense 😀.
Remember that if something here doesn't make sense, you can also propose a change to this document.

<!-- toc -->

- [Code of Conduct](#code-of-conduct)
- [I don't want to read this whole thing, I just have a question!!!](#i-dont-want-to-read-this-whole-thing-i-just-have-a-question)
- [What should I know before I get started?](#what-should-i-know-before-i-get-started)
  * [Packages](#packages)
- [How Can I Contribute?](#how-can-i-contribute)
  * [Reporting Bugs](#reporting-bugs)
  * [Suggesting Enhancements](#suggesting-enhancements)
  * [Improving documentation](#improving-documentation)
  * [Create your very first GitHub issue](#create-your-very-first-github-issue)
- [Your First Code Contribution](#your-first-code-contribution)
  * [Getting the code](#getting-the-code)
  * [Github flow](#github-flow)
  * [Test-driven approach](#test-driven-approach)
  * [Publishing your Pull Request](#publishing-your-pull-request)
  * [Commit message guidelines](#commit-message-guidelines)

<!-- tocstop -->

## Code of Conduct

This project and everyone participating in it are expected to uphold the [Berlin Code of Conduct](https://berlincodeofconduct.org).
If you see unacceptable behavior, please communicate so to `hello@booster.cloud`.

## I don't want to read this whole thing, I just have a question!!!

Go ahead and [create a new issue](https://github.com/boostercloud/booster/issues).

## What should I know before I get started?

### Packages

Booster is divided in many different packages. The criteria to split the code in packages is that each package meets at least one of the following conditions:
* They must be run separately, for instance, the CLI is run locally, while the support code for the project is run on the cloud.
* They contain code that is used by at least two of the other packages.
* They're a vendor-specific specialization of some abstract part of the framework (for instance, all the code that is required by AWS is in separate packages). 

The packages are managed using [Lerna](https://lerna.js.org) and [Yarn](https://yarnpkg.com), if you run `lerna run compile`, it will run `yarn compile` in all the package folders.

The packages are published to `npm` under the prefix `@boostercloud/`, their purpose is as follows:

- `cli` - You guessed it! This package is the `boost` command-line tool, it interacts only with the core package in order to load the project configuration. The specific provider packages to interact with the cloud providers are loaded dynamically from the project config.
- `framework-core` - This one contains all the framework runtime vendor-independent logic. Stuff like the generation of the config or the commands and events handling happens here. The specific provider packages to interact with the cloud providers are loaded dynamically from the project config.
- `framework-integration-tests` - Implements integration tests for all supported vendors. Tests are run on real infrastructure using the same mechanisms than a production application. This package `src` folder includes a synthetic Booster application that can be deployed to a real provider for testing purposes.
- `framework-provider-aws` - Implements all the required adapters to make the booster core run on top of AWS technologies like Lambda and DynamoDB using the AWS SDK under the hoods.
- `framework-provider-aws-infrastructure` - Implements all the required adapters to allow Booster applications to be deployed to AWS using the AWS CDK under the hoods.
- `framework-provider-local` - Implements all the required adapters to run the Booster application on a local express server to be able to debug your code before deploying it to a real cloud provider.
- `framework-provider-local-infrastructure` - Implements all the required code to run the local development server.
- `framework-types` - This package defines types that the rest of the project will use. This is useful for avoiding cyclic dependencies. Note that this package should not contain stuff that are not types, or very simple methods related directly to them, i.e. a getter or setter. This package defines the main booster concepts like:
  - Entity
  - Command
  - etc…

This is a dependency graph that shows the dependencies among all packages, including the application using Booster:
![Booster packages dependencies](docs/img/packages-dependencies.png)

## How Can I Contribute?

Contributing to an open source project is never just a matter of code, you can help us significantly by just using Booster and interacting with our community. Here you'll find some tips on how to do it effectively.

### Reporting Bugs

Before creating a bug report, please search for similar issues to make sure that they're not already reported. If you don't find any, go ahead and create an issue including as many details as possible. Fill out the required template, the information requested helps us to resolve issues faster.

Note: If you find a Closed issue that seems related to the issues that you're experiencing, make sure to reference it in the body of your new one by writing its number like this => #42 (Github will autolink it for you).

Bugs are tracked as GitHub issues. Explain the problem and include additional details to help maintainers reproduce the problem:

- Use a clear and descriptive title for the issue to identify the problem.
- Describe the exact steps which reproduce the problem in as many details as possible.
- Provide specific examples to demonstrate the steps. Include links to files or GitHub projects, or copy/pasteable snippets, which you use in those examples. If you're providing snippets in the issue, use Markdown code blocks.
- Describe the behavior you observed after following the steps and point out what exactly is the problem with that behavior.
- Explain which behavior you expected to see instead and why.
- If the problem is related to performance or memory, include a CPU profile capture with your report.

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. Make sure you provide the following information:

- Use a clear and descriptive title for the issue to identify the suggestion.
- Provide a step-by-step description of the suggested enhancement in as many details as possible.
- Provide specific examples to demonstrate the steps. Include copy/pasteable snippets which you use in those examples, as Markdown code blocks.
- Describe the current behavior and explain which behavior you expected to see instead and why.
- Explain why this enhancement would be useful to most Booster users and isn't something that can or should be implemented as a community package.
- List some other libraries or frameworks where this enhancement exists.

### Improving documentation

Booster documentation, located at `/docs/README.md`, is treated as a live document that continues improving on a daily basis. If you find something that is missing or can be improved, please contribute, it will be of great help for other developers.

Bear in mind that if you have added a new section, or changed an existing one you will need to update the table of content. This can be easily done with the following command:

```sh
yarn update-tocs
```
### Create your very first GitHub issue

[Click here](https://github.com/boostercloud/booster/issues/new) to start making contributions to Booster.


## Your First Code Contribution

Unsure where to begin contributing to Booster? You can start by looking through issued tagged as `good-first-issue` and `help-wanted`:

- Beginner issues - issues which should only require a few lines of code, and a test or two.
- Help wanted issues - issues which should be a bit more involved than beginner issues.

Both issue lists are sorted by the total number of comments. While not perfect, number of comments is a reasonable proxy for impact a given change will have.

Make sure that you assign the chosen issue to yourself to communicate your intention to work on it and reduce the possibilities of other people taking the same assignment.

### Getting the code

To start contributing to the project you would need to set up the project in your system, to do so, you must first follow these steps in your terminal.

- Install Yarn: `npm install -g yarn`

- Install Lerna: `npm install -g lerna`

- Clone the repo and get into the directory of the project: `git clone <WRITE REPO URL HERE> && cd booster`

- Install project dependencies: `lerna bootstrap`

- Compile the project `lerna run compile`

- Add your contribution
  
- Make sure everything works by executing the unit tests: `lerna run test`

- Before making a PR you should run the `check-all-the-things` script:
  - `./scripts/check-all-the-things.sh` on Linux and MacOS
  - `.\scripts\check-all-the-things.ps1` on Windows

### Github flow

The preferred way of accepting contributions is following the [Github flow](https://guides.github.com/introduction/flow/), that is, you fork the project and work in your own branch until you're happy with the work, and then submit a PR in Github.

### Test-driven approach

Booster is a library, so we recommend that you take a test-driven approach, writing or changing the corresponding tests along with the code that you want to add, using the tests to debug it as you add more code and check when your work is complete. This approach not only helps you to design and debug your ongoing work, but also makes the code more robust. All packages have a `test` folder containing tests describing these package functionality, so tests are also a good way to understand how the code works.

You can run all packages tests with Lerna:

```bash
~/booster:$ lerna run test
```

Or in a specific package with yarn:

```bash
~/booster/packages/cli:$ yarn test
```

Once all your unit tests are passing and your code looks great, if your code changes any behavior in the cloud provider, it's important to update the integration test suite and iterate your code until it passes. Notice that in the `framework-integration-tests` there's an `integration` folder with subfolders for each supported provider (including the local provider). Integration tests require real deployments, so they'll last a while and you must have your provider credentials properly set. The test suite will fail with (hopefully) useful error messages with guidance when some parameter is missed. You can run the integration tests using lerna from any package or the project root, or yarn from within the integration tests package:

```bash
~/booster:$ lerna run integration --stream
```

You can run only the tests for a specific provider using the more specific scoped commands:

```bash
~/booster:$ lerna run integration/aws # runs AWS integration tests only

...

~/booster:$ lerna run integration/local # runs local integration tests only
```

### Publishing your Pull Request

Make sure that you describe your change thoroughly in the PR body, adding references for any related issues and links to any resource that helps clarifying the intent and goals of the change. 

When you submit a PR to the Booster repository:
* _Unit tests_ will be automatically run. PRs with non-passing tests can't be merged.
* If tests pass, your code will be reviewed by at least two people from the core team. Clarifications or improvements might be asked, and they reserve the right to close any PR that do not meet the project quality standards, goals or philosophy, so it's always a good idea to discuss your plans in an issue or the Spectrum channel before committing to significant changes.
* Code must be mergeable and all conflicts solved before merging it.
* Once the review process is done, unit tests pass and conflicts are fixed, you still need to make the _Integration tests check_ to pass. In order to do that, you need to **post a comment** in the pull request with the content "**bot: integration**". The _integration tests_ will run and a new check will appear with an "In progress" status. After some time, if everything went well, the status check will become green and your PR is now ready to merge. One of the contributors with write permissions will merge it as soon as possible. 

### Commit message guidelines

The merge commit message should be structured following the [conventional commits](https://www.conventionalcommits.org/) standard:

```text
<commit type>([optional scope]): <description>
```

As an example:

```text
fix(cli): Correct minor typos in code
```

The most important kind of commits are the ones that trigger version bumps and therefore a new release in the CI/CD system:

- `fix` - patch version bump (`0.0.x`)
- `feat` - minor version bump (`0.x.0`)
- Any commit type followed by `!`, i.e. `feat!` - major version bump (`x.0.0`)

Apart from those previously mentioned, there are more commit types:

- **build**: Changes that affect the build system or external dependencies (example scopes: lerna, tsconfig, yarn)
- **ci**: Changes to our CI configuration files and scripts
- **docs**: Documentation only changes
- **feat**: A new feature
- **fix**: A bug fix
- **perf**: A code change that improves performance
- **refactor**: A code change that neither fixes a bug nor adds a feature
- **style**: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc)
- **test**: Adding missing tests or correcting existing tests

We're using the following scopes in the project:

- **cli**
- **core**
- **types**
- **integration**
- **aws**
- **local**

Apart of using conventional commits for triggering releases, we use them to build the project changelog.
