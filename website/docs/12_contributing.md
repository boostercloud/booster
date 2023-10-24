# Contributing to Booster

> **DISCLAIMER:** The Booster docs are undergoing an overhaul. Most of what's written here applies, but expect some hiccups in the build process
> that is described here, as it changed in the last version. New documentation will have this documented properly.

Thanks for taking the time to contribute to Booster. It is an open-source project and it wouldn't be possible without people like you 🙏🎉

This document is a set of guidelines to help you contribute to Booster, which is hosted on the [`boostercloud`](https://github.com/boostercloud) GitHub
organization. These aren’t absolute laws, use your judgment and common sense 😀.
Remember that if something here doesn't make sense, you can also propose a change to this document.

## Code of Conduct

This project and everyone participating in it are expected to uphold the [Booster's Code of Conduct](https://github.com/boostercloud/booster/blob/main/CODE_OF_CONDUCT.md), based on the Covenant Code of Conduct.
If you see unacceptable behavior, please communicate so to `hello@booster.cloud`.

## I don't want to read this whole thing, I just have a question

Go ahead and ask the community in [Discord](https://discord.com/invite/bDY8MKx) or [create a new issue](https://github.com/boostercloud/booster/issues).

## What should I know before I get started?

### Packages

Booster is divided in many different packages. The criteria to split the code in packages is that each package meets at least one of the following conditions:

- They must be run separately, for instance, the CLI is run locally, while the support code for the project is run on the cloud.
- They contain code that is used by at least two of the other packages.
- They're a vendor-specific specialization of some abstract part of the framework (for instance, all the code that is required by Azure is in separate packages).

The packages are managed using [rush](https://rushjs.io/) and [npm](https://npmjs.com), if you run `rush build`, it will build all the packages.

The packages are published to `npmjs` under the prefix `@boostercloud/`, their purpose is as follows:

- `cli` - You guessed it! This package is the `boost` command-line tool, it interacts only with the core package in order to load the project configuration. The specific provider packages to interact with the cloud providers are loaded dynamically from the project config.
- `framework-core` - This one contains all the framework runtime vendor-independent logic. Stuff like the generation of the config or the commands and events handling happens here. The specific provider packages to interact with the cloud providers are loaded dynamically from the project config.
- `framework-integration-tests` - Implements integration tests for all supported vendors. Tests are run on real infrastructure using the same mechanisms than a production application. This package `src` folder includes a synthetic Booster application that can be deployed to a real provider for testing purposes.
- `framework-provider-aws` (Currently Deprecated) - Implements all the required adapters to make the booster core run on top of AWS technologies like Lambda and DynamoDB using the AWS SDK under the hoods.
- `framework-provider-aws-infrastructure` (Currently Deprecated) - Implements all the required adapters to allow Booster applications to be deployed to AWS using the AWS CDK under the hoods.
- `framework-provider-local` - Implements all the required adapters to run the Booster application on a local express server to be able to debug your code before deploying it to a real cloud provider.
- `framework-provider-local-infrastructure` - Implements all the required code to run the local development server.
- `framework-types` - This package defines types that the rest of the project will use. This is useful for avoiding cyclic dependencies. Note that this package should not contain stuff that are not types, or very simple methods related directly to them, i.e. a getter or setter. This package defines the main booster concepts like:
  - Entity
  - Command
  - etc…

This is a dependency graph that shows the dependencies among all packages, including the application using Booster:
![Booster packages dependencies](https://raw.githubusercontent.com/boostercloud/booster/main/docs/img/packages-dependencies.png)

## How Can I Contribute?

Contributing to an open source project is never just a matter of code, you can help us significantly by just using Booster and interacting with our community. Here you'll find some tips on how to do it effectively.

### Reporting Bugs

Before creating a bug report, please search for similar issues to make sure that they're not already reported. If you don't find any, go ahead and create an issue including as many details as possible. Fill out the required template, the information requested helps us to resolve issues faster.

> **Note**: If you find a Closed issue that seems related to the issues that you're experiencing, make sure to reference it in the body of your new one by writing its number like this => #42 (Github will autolink it for you).

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

[Booster documentation](https://docs.boosterframework.com) is treated as a live document that continues improving on a daily basis. If you find something that is missing or can be improved, please contribute, it will be of great help for other developers.
To contribute you can use the button "Edit on github" at the top of each chapter.

#### Documentation principles and practices

The ultimate goal of a technical document is to translate the knowledge from the technology creators into the reader's mind so that they learn. The challenging
part here is the one in which they learn. It is challenging because, under the same amount of information, a person can suffer an information overload because
we (humans) don't have the same information-processing capacity. That idea is going to work as our compass, it should drive our efforts so people with less
capacity is still able to follow and understand our documentation.

To achieve our goal we propose writing documentation following these principles:

1. Clean and Clear
2. Simple
3. Coherent
4. Explicit
5. Attractive
6. Inclusive
7. Cohesive

##### Principles

**1. Clean and Clear**

Less is more. Apple is, among many others, a good example of creating clean and clear content, where visual elements are carefully chosen to look beautiful
(e.g. [Apple's swift UI](https://developer.apple.com/tutorials/swiftui)) and making the reader getting the point as soon as possible.

The intention of every section, paragraph, and sentence must be clear, we should avoid writing details of two different things even when they are related.
It is better to link pages and keep the focus and the intention clear, Wikipedia is the best example on this.

**2. Simple**

Technical writings deal with different backgrounds and expertise from the readers. We should not assume the reader knows everything we are talking about
but we should not explain everything in the same paragraph or section. Every section has a goal to stick to the goal and link to internal or external resources
to go deeper.

Diagrams are great tools, you know a picture is worth more than a thousand words unless that picture contains too much information.
Keep it simple intentionally omitting details.

**3. Coherent**

The documentation tells a story. Every section should integrate naturally without making the reader switch between different contexts. Text, diagrams,
and code examples should support each other without introducing abrupt changes breaking the reader’s flow. Also, the font, colors, diagrams, code samples,
animations, and all the visual elements we include, should support the story we are telling.

**4. Explicit**

Go straight to the point without assuming the readers should know about something. Again, link internal or external resources to clarify.

The index of the whole content must be visible all the time so the reader knows exactly where they are and what is left.

**5. Attractive**

Our text must be nice to read, our diagrams delectable to see, and our site… a feast for the eyes!!

**6. Inclusive**

Everybody should understand our writings, especially the topics at the top. We have arranged the documentation structure in a way that anybody can dig
deeper by just going down so, sections 1 to 4 must be suitable for all ages.

Use gender-neutral language to avoid the use of he, him, his to refer to undetermined gender. It is better to use their or they as a gender-neutral
approach than s/he or similars.

**7. Cohesive**

Writing short and concise sentences is good, but remember to use proper connectors (“Therefore”, “Besides”, “However”, “thus”, etc) that provide a
sense of continuation to the whole paragraph. If not, when people read the paragraphs, their internal voice sounds like a robot with unnatural stops.

For example, read this paragraph and try to hear your internal voice:

> Entities are created on the fly, by reducing the whole event stream. You shouldn't assume that they are stored anywhere.  Booster does create
automatic snapshots to make the reduction process efficient. You are the one in charge of writing the reducer function.

And now read this one:

> Entities are created on the fly by reducing the whole event stream. While you shouldn't assume that they are stored anywhere,  Booster does create automatic
snapshots to make the reduction process efficient. In any case, this is opaque to you and the only thing you should care is to provide the reducer function.

Did you feel the difference? The latter makes you feel that everything is connected, it is more cohesive.

##### Practices

There are many writing styles depending on the type of document. It is common within technical and scientific writing to use Inductive and/or Deductive styles
for paragraphs. They have different outcomes and one style may suit better in one case or another, that is why it is important to know them, and decide which
one to use in every moment. Let’s see the difference with 2 recursive examples.

**Deductive paragraphs ease the reading for advanced users but still allows you to elaborate on ideas and concepts for newcomers**. In deductive paragraphs,
the conclusions or definitions appear at the beginning, and then, details, facts, or supporting phrases complete the paragraph’s idea. By placing the
conclusion in the first sentence, the reader immediately identifies the main point so they can decide to skip the whole paragraph or keep reading.
If you take a look at the structure of this paragraph, it is deductive.

On the other hand, if you want to drive the readers' attention and play with it as if they were in a roller coaster, you can do so by using a different approach.
In that approach, you first introduce the facts and ideas and then you wrap them with a conclusion. This style is more narrative and forces the reader to
continue because the main idea is diluted in the whole paragraph. Once all the ideas are placed together, you can finally conclude the paragraph. **This style is
called Inductive.**

The first paragraph is deductive and the last one is inductive. In general, it is better to use the deductive style, but if we stick to one, our writing will start looking weird and maybe boring.
So decide one or another being conscious about your intention.

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

- Install Rush: `npm install -g @microsoft/rush`

- Clone the repo and get into the directory of the project: `git clone <WRITE REPO URL HERE> && cd booster`

- Install project dependencies: `rush update`

- Compile the project `rush build`

- Add your contribution

- Make sure everything works by [executing the unit tests](#running-unit-tests): `rush rest`

> **DISCLAIMER**: The integration test process changed, feel free to chime in into our Discord for more info

- Make sure everything works by [running the integration tests](#running-integration-tests):

```bash
rush pack-integration-deps
cd packages/framework-integration-tests
rushx integration -v
```

### Understanding the "rush monorepo" approach and how dependencies are structured in the project

The Booster Framework project is organized following the ["rush monorepo"](https://rushjs.io/) structure. There are several "package.json" files and each one has its purpose with regard to the dependencies you include on them:

- The "package.json" files that are on each package root should contain the dependencies used by that specific package. Be sure to correctly differentiate which dependency is only for development and which one is for production.

Finally, **always use exact numbers for dependency versions**. This means that if you want to add the dependency "graphql" in version 1.2.3, you should add `"graphql": "1.2.3"` to the corresponding "package.json" file, and never `"graphql": "^1.2.3"` or `"graphql": "~1.2.3"`. This restriction comes from hard problems we've had in the past.

### Running unit tests

Unit tests are executed when you type `rush test`. If you want to run the unit tests for an especific package, you should move to the corresponding folder and run one of the following commands:

- `rushx test:cli -v`: Run unit tests for the `cli` package.
- `rushx test:core -v`: Run unit tests for the `framework-core` package.
- `rushx test:provider-aws -v`: Run unit tests for the `framework-provider-aws` package.
- `rushx test:provider-aws-infrastructure -v`: Run unit tests for the `framework-provider-aws-infrastructure` package.
- `rushx test:provider-azure -v`: Run unit tests for the `framework-provider-azure` package.
- `rushx test:provider-azure-infrastructure -v`: Run unit tests for the `framework-provider-azure-infrastructure` package.
- `rushx test:provider-local -v`: Run unit tests for the `framework-provider-local` package.
- `rushx test:provider-local-infrastructure -v`: Run unit tests for the `framework-provider-local-infrastructure` package.
- `rushx test:types -v`: Run unit tests for the `framework-types` package.

### Running integration tests

Integration tests are run automatically in Github Actions when a PR is locked, but it would be recommendable to run them locally before submitting a PR for review. You can find several scripts in `packages/framework-integration-tests/package.json` to run different test suites. You can run them using rush tool:

`rushx <script name> -v`

These are the available scripts to run integration tests:

1. **General Integration Tests:**
    - `rushx integration -v`: Run all integration test scripts.

2. **CLI Integration Tests:**
    - `rushx integration/cli -v`: Tests CLI commands and verifies that they produce the expected results.

3. **Local Integration Tests:**
    - `rushx integration/local -v`: Runs all integration scripts in the local development server.
    - `rushx integration/local-ongoing -v`: Runs the start and stop integration tests.
    - `rushx integration/local-start -v`: Checks the start functionality of the local environment.
    - `rushx integration/local-func -v`: Functional tests for the local environment.
    - `rushx integration/local-end-to-end -v`: Runs end-to-end tests in the local environment.
    - `rushx integration/local-stop -v`: Checks the stop functionality of the local environment.

4. **AWS Integration Tests:**
    - `rushx integration/aws -v`: Runs all integration test scripts for provider AWS.
    - `rushx integration/aws-deploy -v`: Tests the deployment of a sample project to AWS.
    - `rushx integration/aws-func -v`: Runs functional tests on AWS, stressing the deployed app's write API and verifying the results in databases and read APIs.
    - `rushx integration/aws-end-to-end -v`: Performs end-to-end tests on AWS.
    - `rushx integration/aws-load -v`: (Currently skipped) Intended for load tests on AWS.
    - `rushx integration/aws-nuke -v`: Verifies that the deployed application on AWS can be properly nuked.

5. **Azure Integration Tests:**
    - `rushx integration/azure -v`: Runs all integration test scripts for provider Azure.
    - `rushx integration/azure-deploy -v`: Tests the deployment of a project to Azure.
    - `rushx integration/azure-func -v`: Runs functional tests on Azure.
    - `rushx integration/azure-end-to-end -v`: Performs end-to-end tests on Azure.
    - `rushx integration/azure-nuke -v`: Verifies that the deployed application on Azure can be properly nuked.

Azure and AWS integration tests run in real environments, so you'll need to have your credentials properly set in your development machine in order to run them. They will deploy a sample project to your default account, run the tests and nuke the application when the process finishes. Notice that running integration tests in your cloud account could incur in some expenses.

### Github flow

The preferred way of accepting contributions is following the [Github flow](https://guides.github.com/introduction/flow/), that is, you fork the project and work in your own branch until you're happy with the work, and then submit a PR in Github.

### Publishing your Pull Request

Make sure that you describe your change thoroughly in the PR body, adding references for any related issues and links to any resource that helps clarify the intent and goals of the change.

When you submit a PR to the Booster repository:

- _Unit tests_ will be automatically run. PRs with non-passing tests can't be merged.
- If tests pass, your code will be reviewed by at least two core team members. Clarifications or improvements might be asked. They reserve the right to close any PR that does not meet the project quality standards, goals, or philosophy. So, discussing your plans in an issue or the Spectrum channel is always a good idea before committing to significant changes.
- Code must be mergeable, and all conflicts must be solved before merging.
- Once the review process is done, unit tests pass, and conflicts are fixed, you still need to make the Integration tests check to pass. You need to **Lock conversation** in the pull request to do that. The _integration tests_ will run, and a new check will appear with an "In progress" status. After some time, if everything goes well, the status check will become green, and your PR is now ready to merge.

### Branch naming conventions

In order to create a PR, you must create a branch from `main`. You should follow the GitFlow naming conventions, as detailed below:

- `feature/*` - PR that implements a new feature
- `fix/*` - PR that fixes a bug
- `doc/*` - PR that enhances the documentation

In the right side of the branch name you can include the GitHub issue number. An example of doing this could be:

```bash
git checkout -b feature/XXX_add-an-awesome-new-feature
```

(where `XXX` is the issue number)

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

- **build**: Changes that affect the build system or external dependencies (example scopes: rush, tsconfig, npm)
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

## Code Style Guidelines

The Booster project comes with a nice set of ESLint config files to help you follow a consistent style, and we really encourage to use it in your editor. You can also run the `rush run lint:fix` commands to try solving any linter problems automatically.

For everything else, the rule of thumb is: Try to be consistent with the code around yours, and if you're not sure, ask :-)

There are some things that the linter doesn't force but are prefered this way:

### Importing other files and libraries

Use `import` instead of `require` and import the objects individually when possible:

```typescript
import { Object, function } from 'some-package'
```

### Functional style

We give priority to a functional style of programming, but always taking into account how the objects are used to make sure they form a nice DSL. Classes are allowed when there's an actual state to hold, and we usually avoid default exports:

```typescript
// module-a.ts, a conventional functional module
export functionA() {
  ...
}

export const someConstantA = 42
```

```typescript
// module-b.ts, grouping functions with a scope
export const ModuleB = {
  functionB1: () => {...},
  functionB2: () => {...},
}
```

```typescript
// object-c.ts, a class
export class ObjectC {
  constructor(readonly value: number) {}
}
```

```typescript
import { functionA, someConstantA } from 'module-a'
import { ModuleB } from 'module-b'
import { ObjectC } from 'object-c'

functionA()
ModuleB.functionB1()
const obj = new ObjectC(someConstantA)
```

### Use `const` and `let`

Default to `const` and immutable objects when possible, otherwise, use `let`.

```typescript
// Good
let a = 0
const b = 3
a = a + b

// Less Good
var c = 0
let d = 3 // Never updated
```