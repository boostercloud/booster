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
- It is a multi-cloud framework, and behavior is double-checked both on AWS and Azure, as they are the officially supported cloud
  providers. Ensuring everything runs smoothly, regardless of the choice of the user.

We always keep improving our CI/CD processes, but we always make sure that we have the above covered.

The two main folders you have to look at are:

- `.github/actions`
- `.github/workflows`

Here we define the components that we reuse to simplify the CI/CD as much as possible.

## The Actions folder




## Workflows

This folder uses the following convention:

- Files prefixed with `re_` are [reusable workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows) and are meant to be used instead of copy-pasting jobs
- Files prefixed with `wf_` are regular workflows that define workflows in the GitHub Actions CI/CD pipeline
- Files get their name in descending order, in the sense of the things they do. E.g.
  - `test-unit` instead of `unit-tests`
  - `test-integration-aws` instead of `aws-integration`

There are some special workflow files like `codeql-analysis` or `codesee-arch-diagram` that are handled by 3rd party services and are left
with their default name.
