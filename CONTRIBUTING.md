# Contributing to Booster

Thanks for taking the time to contribute to Booster. It is an open-source project and it wouldn’t be possible without people like you 🙏🎉

This document is a set of guidelines to help you contribute to Booster, which is hosted on the [`boostercloud`](https://github.com/boostercloud) GitHub
organization. These aren’t absolute laws, use your judgment and common sense 😀.
Remember that if something here doesn’t make sense, you can propose a change to this document also.

- [Code of Conduct](#code-of-conduct)
- [I don't want to read this whole thing, I just have a question!!!](#i-dont-want-to-read-this-whole-thing-i-just-have-a-question)
- [What should I know before I get started?](#what-should-i-know-before-i-get-started)
  - [Packages](#packages)
- [How Can I Contribute?](#how-can-i-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Enhancements](#suggesting-enhancements)
- [Your First Code Contribution](#your-first-code-contribution)
- [Commit message guidelines](#commit-message-guidelines)
  - [Commit types](#commit-types)
  - [Scopes](#scopes)
  - [Note for maintainers](#note-for-maintainers)


## Code of Conduct

This project and everyone participating in it are expected to uphold the Berlin Code of Conduct.
If you see unacceptable behavior, please communicate so to `hello@booster.cloud`.

## I don't want to read this whole thing, I just have a question!!!

**Please don't file an issue to ask a question**. You'll get faster results by using our Spectrum.
Note that even though it is a chat, some members might take some time to answer.

## What should I know before I get started?

### Packages

Booster is divided in different packages. The packages are managed using Lerna, if you run `lerna run compile`,
it will run `npm run compile` in all the package folders.

The packages are all prefixed with `booster-`, and are uploaded to `npm` under the prefix `@boostercloud/`, their purpose is as follows:

- `types` - This package defines types that the rest of the project will use. This is useful for avoiding cyclic dependencies. Note that this package should not contain stuff that are not types, or very simple methods related directly to them, i.e. a getter or setter. This package defines the main booster concepts like:
  - Entity
  - Command
  - etc…
- `core` - This one is the one that contains all the logic directly tied to the Booster architecture. Stuff like the generation of the config or the handler wrappers are defined here. This package uses the provider packages dynamically.
- `aws` - The first provider package, which acts as an adapter between the core and the AWS SDK (more concretely, the CDK library). All stuff that is directly tied to AWS go here.
- `cli` - You guessed it! This package is the boost command-line tool, it interacts only with the core package in order to load the configuration.
- `example` - An example project that is updated with the changes added to the project. It is useful to have this package to perform pre-release manual smoke tests.

## How Can I Contribute?

### Reporting Bugs

When you are creating a bug report, please include as many details as possible. Fill out the required template, the information it asks for helps us resolve issues faster.

Note: If you find a Closed issue that seems like it is the same thing that you're experiencing, open a new issue and include a link to the original issue in the body of your new one.

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

## Your First Code Contribution

Unsure where to begin contributing to Booster? You can start by looking through these beginner and help-wanted issues:

- Beginner issues - issues which should only require a few lines of code, and a test or two.
- Help wanted issues - issues which should be a bit more involved than beginner issues.

Both issue lists are sorted by the total number of comments. While not perfect, number of comments is a reasonable proxy for impact a given change will have.

## Commit message guidelines

The commit message should be structured as follows:

```text
<type>([optional scope]): <description>
```

As an example:

```text
fix(cli): Correct minor typos in code
```

The most important types of commits are the following, as they trigger version bumps in the CI/CD system:

- `fix` - patch version bump (`0.0.x`)
- `feat` - minor version bump (`0.x.0`)
- Any commit type followed by `!`, i.e. `feat!` - major version bump (`x.0.0`)

### Commit types

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

### Scopes

There are several scopes in the project:

- **cli**
- **core**
- **types**
- **example**
- **aws**
- **local**

### Note for maintainers

If you happen to be a maintainer of the project, all PRs must be _**Squash and merged**_, the merge commit should adhere to the [Conventional Commits standard](https://www.conventionalcommits.org/en/v1.0.0/). TL;DR:
