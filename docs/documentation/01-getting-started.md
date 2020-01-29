# Getting Started

Let's install the Booster CLI and bootstrap your first project:

1. Install the Booster CLI tool if you haven’t yet:

   ```shell script
   npm install -g @boostercloud/cli
   ```

2. Create a new Booster project

   ```shell script
   boost new:project <name of your project>
   ```

3. Go to your new project folder (`cd <name of your project>`) to see the project structure. You'll see something like this:

    ```
    $ tree
    .
    |____package.json
    |____tsconfig.json
    |____src
    | |____migrations
    | |____config
    | | |____config.ts
    | |____read-models
    | |____commands
    | |____events
    | |____entities
    ```

Project structure or code generators are not enforced, but they're recommended to build a community-shared set of conventions.

Now that you have an empty project you can:
- [Learn more about the Booster Cloud Framework high-level Architecture](02-architecture.md)
- [Start building your write API creating your first Commands](03-commands.md)
- [See code examples and step-by-step guides](../examples)