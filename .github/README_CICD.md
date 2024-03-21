# Booster GitHub CI/CD

This document describes the process and structure of the configuration of the project's GitHub actions and workflows.

Booster as a project has some special needs in terms of CI/CD compared to your regular project because it is a framework,
and it handles so much complexity. So we have to make sure that everything works flawlessly as much as possible. Take into
account that:

- Because it is a framework and not only a library, the framework will take decisions on behalf of the user, in terms of design
  and other things, so in case of failure, we make sure that we have done as much as possible to prevent it so
  the user is not confused.
- It handles the creation and wiring of many cloud components, which are lots of moving pieces, so everything is double-checked
  to prevent errors in deployed environments.
- It is a multi-cloud framework, and behavior is double-checked both on AWS and Azure. Ensuring everything runs smoothly, regardless of the choice of the user.

We always keep improving our CI/CD processes, but we always make sure that we have the above covered.

The two main folders you have to look at are:

- `.github/actions`
- `.github/workflows`

Here we define the components that we reuse to simplify the CI/CD as much as possible.

## The Actions folder

The actions folder defines a couple of reusable actions that we use throughout the CI/CD process:

- `build`
  - This action ensures that the dependencies are properly cached and then tries
    to build the project.
- `call-rush`
  - This action uses the command passed as a parameter to call `rush`. It will install rush if it's not installed on the current CI/CD machine.
- `test-integration-run-one`
  - This one is a bit more complex. It is the foundation of our integration tests, as all
    jobs that run those will use this action.
  - It does a fork-based checkout if it was triggered by a `/integration` command.
  - It will build the project using the action above.
  - It will set the `BOOSTER_APP_SUFFIX` environment variable to the appropriate SHA (either the one from the fork or the current one in the branch, in that order).
  - It will download the packed project from the GitHub cache (more on this later).
  - Will log in to Azure, if the Azure credentials are defined.
  - Finally, it will run the integration test that was passed by parameters, using all the secrets required for that.

## Workflows

This folder uses the following convention:

- Files prefixed with `re_` are [reusable workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows) and are meant to be used instead of copy-pasting jobs
- Files prefixed with `wf_` are regular workflows that define workflows in the GitHub Actions CI/CD pipeline
- Files get their name in descending order, in the sense of the things they do. E.g.
  - `test-unit` instead of `unit-tests`
  - `test-integration-aws` instead of `aws-integration`

There are some special workflow files like `codeql-analysis` or `codesee-arch-diagram` that are handled by 3rd party services and are left
with their default name.

The most complex one, which requires more explanation is the `wf_test-integration` one, it:

1. Will run in these conditions
   1. Triggered by a `/integration` command in a PR
   2. Triggered by `wf_publish-npm` when a push happens in `main`
2. If it was triggered in a PR, it will send a comment with a link to the integration tests run.
3. Will compile and pack the Booster packages into some `.tar.gz` and upload them to the GitHub cache.
4. Run in parallel the integration tests for
   1. AWS
   2. CLI
   3. Local Provider
   4. Azure
5. Notify the outcome of the integration tests as a comment in the PR (if applicable).

### `re_test-integration-*`

These files are the ones responsible for running the integration tests for each of the different packages of Booster.

They are pretty straightforward, but perhaps the cloud related ones (e.g. AWS) are a bit more
complex. They will:

1. Deploy the project
2. Will run the following integration tests in parallel
   1. Functionality
   2. End-to-end
   3. Load tests
3. Nuke the cloud resources
