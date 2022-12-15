# Booster GitHub Workflows

This folder uses the following convention:

- Files prefixed with `re_` are [reusable workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows) and are meant to be used instead of copy-pasting steps/jobs
- Files prefixed with `wf_` are regular workflows that define workflows in the GitHub Actions CI/CD pipeline

There are some special workflow files like `codeql-analysis` or `codesee-arch-diagram` that are handled by 3rd party services and are left
with their default name.
