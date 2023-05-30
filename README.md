# ![Booster Framework](https://user-images.githubusercontent.com/175096/217907175-b81b3937-d773-45fd-85ca-716f9813432d.png)

[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.0-4baaaa.svg)](CODE_OF_CONDUCT.md)
[![Build Status](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Factions-badge.atrox.dev%2Fboostercloud%2Fbooster%2Fbadge%3Fref%3Dmain&style=flat)](https://actions-badge.atrox.dev/boostercloud/booster/goto?ref=main)
[![oclif](https://img.shields.io/badge/cli-oclif-brightgreen.svg)](https://oclif.io)
[![License](https://img.shields.io/npm/l/@boostercloud/cli)](https://github.com/boostercloud/booster/blob/main/package.json)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-yellow.svg)](https://conventionalcommits.org)
![Integration tests](https://github.com/boostercloud/booster/actions/workflows/integration-tests.yml/badge.svg)
[![Discord](https://img.shields.io/discord/763753198388510780.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bDY8MKx)
[![Docs](https://img.shields.io/badge/Docs-Booster-blue)](https://docs.booster.cloud)
---

# What is Booster Framework?

[Booster Framework](https://boosterframework.com) is a software development framework designed to create event-driven backend microservices that focus on extreme development productivity. It provides a highly opinionated implementation of the CQRS and Event Sourcing patterns in Typescript, using [DDD (Domain-Driven Design)](https://en.wikipedia.org/wiki/Domain-driven_design) semantics that makes business logic fit naturally within the code. Thanks to Booster, business, product, and technical teams can collaborate, sharing a much closer language.

Booster uses advanced static analysis techniques and takes advantage of the Typescript type system to understand the structure and semantics of your code and minimize the amount of glue code. It’s capable not just of building an entirely functioning GraphQL API for you, but also to build an optimal, production-ready and scalable cloud infrastructure for your application in your preferred cloud provider (Azure or AWS).

Combining these features, Booster provides an unprecedented developer experience. On the one hand, it helps you write simpler code, defining your application in terms of commands, events, entities, and read models. On the other hand, you don't have to worry about the tremendous amount of low-level configuration details of conventional tools. You write highly semantic code, and if it compiles, you can run it on the cloud at scale.

Booster is 100% open-source and designed with extensibility in mind. If your desired infrastructure doesn't match the existing implementations, you can easily fork and adapt them or create a new one using your infrastructure-as-code tool of preference. Booster also supports extensions (called “Rockets”) that allow users to implement additional functionalities.

If you want to help us to drive Booster forward or have questions, don't hesitate to ping us on [Discord](https://discord.gg/bDY8MKx)!

# Why Booster instead of X?

Booster is designed to maximize developer productivity, and every framework feature is carefully thought out to put your application in production as soon as possible. The CLI helps you to get up and running quickly, and the easy-to-comprehend abstractions and the opinionated architecture make it easy to understand how to organize your code and become productive sooner.

The no-boilerplate politics goes to the extreme, as Booster understands the semantics of your code to create a fully-working GraphQL API for you, as well as an optimal serverless cloud infrastructure and database integrations. And, of course, the API and infrastructure are transparently updated when the application changes.

It would be easier to understand Booster capabilities by listing the things that you won’t need to implement or maintain with Booster:

* You won’t need to maintain GraphQL schemas
* You won’t need to implement GraphQL resolvers
* You won’t have to manage URL paths
* You won’t have to design the API schemas
* You won’t have to deserialize or serialize JSON objects
* You won’t need to use DTOs
* You won’t need to deal with ORM mappings and/or database queries
* You won’t need to write infrastructure configuration or deployment scripts
* You won’t need to build WebSockets for subscriptions

All those things, and more, will be given to you by default and entirely for free, as Booster is open-source and runs in your own cloud account!

# Installation

You can develop with Booster using any of the following operating systems:

- Linux
- macOS
- Windows (Native and WSL)

## Booster Prerequisites

### Install Node.js

The minimal required Node.js version is `v14.14`. Download the installer
[from nodejs website](https://nodejs.org/en/), or install it using your system's package
manager.

<!-- For some strange reason, when using tabs, you need to remove the ident on the content -->

<Tabs groupId="os">
<TabItem value="windows" label="Windows">

Using [Chocolatey](https://chocolatey.org/) package manager, run the following command in your PowerShell

<TerminalWindow>

```shell
choco install nodejs
```

</TerminalWindow>


</TabItem>
<TabItem value="macos" label="macOS">

Using [Homebrew](https://brew.sh) package manager, run the following command on the terminal

<TerminalWindow>

```shell
brew install node
```

</TerminalWindow>

</TabItem>
<TabItem value="ubuntu" label="Ubuntu">
  
Just run the following commands on the terminal:

<TerminalWindow>

```shell
curl -sL https://deb.nodesource.com/setup_14.x | sudo -E bash -
sudo apt install nodejs
```

</TerminalWindow>

</TabItem>
</Tabs>

Verify that it was installed properly by checking so from your terminal:

<TerminalWindow>

```shell
node -v
```

> v14.14.0

```shell
npm -v
```

> 7.0.0

</TerminalWindow>

As soon as you have a Node.js version higher than `v14.14`, and an `npm` version higher than
`7`, you are good to go. Just note that `npm` comes with node, you don't have to install
it apart.

Alternatively, we recommend you to use a version manager for dealing with different Node.js
versions:

- [`nvm`](https://github.com/nvm-sh/nvm) - Works with macOS, Linux, and Windows Subsystem
  for Linux
- [`nvm-windows`](https://github.com/coreybutler/nvm-windows) - Works with native Windows

### Install Git

Booster will initialize a Git repository when you create a new project (unless you use the `--skipGit` flag), so it is required that you have it already installed in your system.

<Tabs groupId="os">
<TabItem value="windows" label="Windows">

<TerminalWindow>

```shell
choco install git
```

</TerminalWindow>

</TabItem>
<TabItem value="macos" label="macOS" default>

<TerminalWindow>

```shell
brew install git
```

</TerminalWindow>

</TabItem>
<TabItem value="ubuntu" label="Ubuntu">

<TerminalWindow>

```shell
sudo apt install git-all
```

</TerminalWindow>

</TabItem>
</Tabs>

#### Git configuration variables

After installing git in your machine, make sure that `user.name` and `user.email` are properly configured.
Take a look at the [Git configuration page](https://git-scm.com/docs/git-config) for more info.

To configure them, run in your terminal:

<TerminalWindow>

```shell
git config --global user.name "Your Name Here"
git config --global user.email "your_email@youremail.com"
```

</TerminalWindow>


## Installing the Booster CLI

Booster comes with a command-line tool that helps you generating boilerplate code,
testing and deploying the application, and deleting all the resources in the cloud. All
the stable versions are published to <CLInstallBooster>[`npm`](https://www.npmjs.com/package/@boostercloud/cli)</CLInstallBooster>,
these versions are the recommended ones, as they are well documented, and the changes are
stated in the release notes.

To install the Booster CLI run this:

<TerminalWindow>

```shell
npm install --global @boostercloud/cli
```

</TerminalWindow>


Verify the Booster CLI installation with the `boost version` command. You should get back
something like

<TerminalWindow>

```shell
boost version
```

> @boostercloud/cli/0.16.1 darwin-x64 node-v14.14.0

</TerminalWindow>

# Build a Booster app in minutes

In this section, we will go through all the necessary steps to have the backend up and
running for a blog application in just a few minutes.

Before starting, make sure to [have Booster CLI installed](/getting-started/installation). If you also want to deploy your application to your cloud provider, check out the [Provider configuration](../going-deeper/infrastructure-providers) section.

### 1. Create the project

First of all, we will use the Booster CLI tool generators to create a project.

In your favourite terminal, run this command `boost new:project boosted-blog` and follow
the instructions. After some prompted questions, the CLI will ask you to select one of the available providers to set up as the main provider that will be used.

<TerminalWindow>

```shell
? What's the package name of your provider infrastructure library? (Use arrow keys)
❯ @boostercloud/framework-provider-aws (AWS)
  @boostercloud/framework-provider-azure (Azure)
  Other
```

</TerminalWindow>

When asked for the provider, select AWS as that is what we have
configured [here](../going-deeper/infrastructure-providers#aws-provider-setup) for the example. You can use another provider if you want, or add more providers once you have created the project.

If you don't know what provider you are going to use, and you just want to execute your Booster application locally, you can select one and change it later!

After choosing your provider, you will see your project generated!:

<TerminalWindow>

```shell
> boost new:project boosted-blog

...

ℹ boost new 🚧
✔ Creating project root
✔ Generating config files
✔ Installing dependencies
ℹ Project generated!
```

</TerminalWindow>

:::tip
If you prefer to create the project with default parameters, you can run the command as `boost new:project booster-blog --default`. The default
parameters are as follows:

- Project name: The one provided when running the command, in this case "booster-blog"
- Provider: AWS
- Description, author, homepage and repository: ""
- License: MIT
- Version: 0.1.0

:::

In case you want to specify each parameter without following the instructions, you can use the following flags with this structure `<flag>=<parameter>`.

| Flag                    | Short version | Description                                                                                     |
| :---------------------- | :------------ | :---------------------------------------------------------------------------------------------- |
| `--homepage`            | `-H`          | The website of this project                                                                     |
| `--author`              | `-a`          | Author of this project                                                                          |
| `--description`         | `-d`          | A short description                                                                             |
| `--license`             | `-l`          | License used in this project                                                                    |
| `--providerPackageName` | `-p`          | Package name implementing the cloud provider integration where the application will be deployed |
| `--repository`          | `-r`          | The URL of the repository                                                                       |
| `--version`             | `-v`          | The initial version                                                                             |

Additionally, you can use the `--skipInstall` flag if you want to skip installing dependencies and the `--skipGit` flag in case you want to skip git initialization.

> Booster CLI commands follow this structure: `boost <subcommand> [<flags>] [<parameters>]`.
> Let's break down the command we have just executed:
>
> - `boost` is the Booster CLI executable
> - `new:project` is the "subcommand" part. In this case, it is composed of two parts separated by a colon. The first part, `new`, means that we want to generate a new resource. The second part, `project`, indicates which kind of resource we are interested in. Other examples are `new:command`, `new:event`, etc. We'll see a bunch of them later.
> - `boosted-blog` is a "parameter" for the subcommand `new:project`. Flags and parameters are optional and their meaning and shape depend on the subcommand you used. In this case, we are specifying the name of the project we are creating.

:::tip
You can always use the `--help` flag to get all the available options for each cli command.
:::

When finished, you'll see some scaffolding that has been generated. The project name will be the
project's root so `cd` into it:

<TerminalWindow>

```shell
cd boosted-blog
```

</TerminalWindow>

There you should have these files and directories already generated:

```text
boosted-blog
├── .eslintignore
├── .gitignore
├── .eslintrc.js
├── .prettierrc.yaml
├── package-lock.json
├── package.json
├── src
│   ├── commands
│   ├── common
│   ├── config
│   │   └── config.ts
│   ├── entities
│   ├── events
│   ├── event-handlers
│   ├── read-models
│   └── index.ts
├── tsconfig.eslint.json
└── tsconfig.json
```

Now open the project in your favorite editor, e.g. [Visual Studio Code](https://code.visualstudio.com/).

### 2. First command

Commands define the input to our system, so we'll start by generating our first
[command](/architecture/command) to create posts. Use the command generator, while in the project's root
directory, as follows:

<TerminalWindow>

```bash
boost new:command CreatePost --fields postId:UUID title:string content:string author:string
```

</TerminalWindow>

The `new:command` generator creates a `create-post.ts` file in the `commands` folder:

```text
boosted-blog
└── src
    └── commands
        └── create-post.ts
```

As we mentioned before, commands are the input of our system. They're sent
by the users of our application. When they are received you can validate its data,
execute some business logic, and register one or more events. Therefore, we have to define two more things:

1. Who is authorized to run this command.
1. The events that it will trigger.

Booster allows you to define authorization strategies (we will cover that
later). Let's start by allowing anyone to send this command to our application.
To do that, open the file we have just generated and add the string `'all'` to the
`authorize` parameter of the `@Command` decorator. Your `CreatePost` command should look like this:

```typescript
@Command({
  authorize: 'all', // Specify authorized roles here. Use 'all' to authorize anyone
})
export class CreatePost {
  public constructor(
    readonly postId: UUID,
    readonly title: string,
    readonly content: string,
    readonly author: string
  ) {}

  public static async handle(command: CreatePost, register: Register): Promise<void> {
    register.events(/* YOUR EVENT HERE */)
  }
}
```

### 3. First event

Instead of creating, updating, or deleting objects, Booster stores data in the form of events.
They are records of facts and represent the source of truth. Let's generate an event called `PostCreated`
that will contain the initial post info:

<TerminalWindow>

```bash
boost new:event PostCreated --fields postId:UUID title:string content:string author:string
```

</TerminalWindow>

The `new:event` generator creates a new file under the `src/events` directory.
The name of the file is the name of the event:

```text
boosted-blog
└── src
    └── events
        └── post-created.ts
```

All events in Booster must target an entity, so we need to implement an `entityID`
method. From there, we'll return the identifier of the post created, the field
`postID`. This identifier will be used later by Booster to build the final state
of the `Post` automatically. Edit the `entityID` method in `events/post-created.ts`
to return our `postID`:

```typescript
// src/events/post-created.ts

@Event
export class PostCreated {
  public constructor(
    readonly postId: UUID,
    readonly title: string,
    readonly content: string,
    readonly author: string
  ) {}

  public entityID(): UUID {
    return this.postId
  }
}
```

Now that we have an event, we can edit the `CreatePost` command to emit it. Let's change
the command's `handle` method to look like this:

```typescript
// src/commands/create-post.ts::handle
public static async handle(command: CreatePost, register: Register): Promise<void> {
  register.events(new PostCreated(command.postId, command.title, command.content, command.author))
}
```

Remember to import the event class correctly on the top of the file:

```typescript
import { PostCreated } from '../events/post-created'
```

We can do any validation in the command handler before storing the event, for our
example, we'll just save the received data in the `PostCreated` event.

### 4. First entity

So far, our `PostCreated` event suggests we need a `Post` entity. Entities are a
representation of our system internal state. They are in charge of reducing (combining) all the events
with the same `entityID`. Let's generate our `Post` entity:

<TerminalWindow>

```bash
boost new:entity Post --fields title:string content:string author:string --reduces PostCreated
```

</TerminalWindow>

You should see now a new file called `post.ts` in the `src/entities` directory.

This time, besides using the `--fields` flag, we use the `--reduces` flag to specify the events the entity will reduce and, this way, produce the Post current state. The generator will create one _reducer function_ for each event we have specified (only one in this case).

Reducer functions in Booster work similarly to the `reduce` callbacks in Javascript: they receive an event
and the current state of the entity, and returns the next version of the same entity.
In this case, when we receive a `PostCreated` event, we can just return a new `Post` entity copying the fields
from the event. There is no previous state of the Post as we are creating it for the first time:

```typescript
// src/entities/post.ts
@Entity
export class Post {
  public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}

  @Reduces(PostCreated)
  public static reducePostCreated(event: PostCreated, currentPost?: Post): Post {
    return new Post(event.postId, event.title, event.content, event.author)
  }
}
```

Entities represent our domain model and can be queried from command or
event handlers to make business decisions or enforcing business rules.

### 5. First read model

In a real application, we rarely want to make public our entire domain model (entities)
including all their fields. What is more, different users may have different views of the data depending
on their permissions or their use cases. That's the goal of `ReadModels`. Client applications can query or
subscribe to them.

Read models are _projections_ of one or more entities into a new object that is reachable through the query and subscriptions APIs. Let's generate a `PostReadModel` that projects our
`Post` entity:

<TerminalWindow>
```bash
boost new:read-model PostReadModel --fields title:string author:string --projects Post:id
```
</TerminalWindow>

We have used a new flag, `--projects`, that allow us to specify the entities (can be many) the read model will
watch for changes. You might be wondering what is the `:id` after the entity name. That's the [joinKey](/architecture/read-model#the-projection-function),
but you can forget about it now.

As you might guess, the read-model generator will create a file called
`post-read-model.ts` under `src/read-models`:

```text
boosted-blog
└── src
    └── read-models
        └── post-read-model.ts
```

There are two things to do when creating a read model:

1. Define who is authorized to query or subscribe it
1. Add the logic of the projection functions, where you can filter, combine, etc., the entities fields.

While commands define the input to our system, read models define the output, and together they compound
the public API of a Booster application. Let's do the same we did in the command and authorize `all` to
query/subscribe the `PostReadModel`. Also, and for learning purposes, we will exclude the `content` field
from the `Post` entity, so it won't be returned when users request the read model.

Edit the `post-read-model.ts` file to look like this:

```typescript
// src/read-models/post-read-model.ts
@ReadModel({
  authorize: 'all', // Specify authorized roles here. Use 'all' to authorize anyone
})
export class PostReadModel {
  public constructor(public id: UUID, readonly title: string, readonly author: string) {}

  @Projects(Post, 'id')
  public static projectPost(entity: Post, currentPostReadModel?: PostReadModel): ProjectionResult<PostReadModel> {
    return new PostReadModel(entity.id, entity.title, entity.author)
  }
}
```

### 6. Deployment

At this point, we've:

- Created a publicly accessible command
- Emitted an event as a mechanism to store data
- Reduced the event into an entity to have a representation of our internal state
- Projected the entity into a read model that is also publicly accessible.

With this, you already know the basics to build event-driven, CQRS-based applications
with Booster.

You can check that code compiles correctly by running the build command:

<TerminalWindow>

```bash
boost build
```

</TerminalWindow>

You can also clean the compiled code by running:

<TerminalWindow>

```bash
boost clean
```

</TerminalWindow>

#### 6.1 Running your application locally

Now, let's run our application to see it working. It is as simple as running:

<TerminalWindow>

```bash
boost start -e local
```

</TerminalWindow>

This will execute a local `Express.js` server and will try to expose it in port `3000`. You can change the port by using the `-p` option:

<TerminalWindow>

```bash
boost start -e local -p 8080
```

</TerminalWindow>

#### 6.2 Deploying to the cloud

Also, we can deploy our application to the cloud with no additional changes by running
the deploy command:

<TerminalWindow>

```bash
boost deploy -e production
```

</TerminalWindow>

This is the Booster magic! ✨ When running the start or the deploy commands, Booster will handle the creation of all the resources, _like Lambdas, API Gateway,_ and the "glue" between them; _permissions, events, triggers, etc._ It even creates a fully functional GraphQL API!

:::note
Deploy command automatically builds the project for you before performing updates in the cloud provider, so, build command it's not required beforehand.
:::

> With `-e production` we are specifying which environment we want to deploy. We'll talk about them later.

:::tip
If at this point you still don’t believe everything is done, feel free to check in your provider’s console. You should see, as in the AWS example below, that the stack and all the services are up and running! It will be the same for other providers. 🚀
:::

![resources](/img/aws-resources.png)

When deploying, it will take a couple of minutes to deploy all the resources. Once finished, you will see
information about your application endpoints and other outputs. For this example, we will
only need to pick the output ending in `httpURL`, e.g.:

```text
https://<some random name>.execute-api.us-east-1.amazonaws.com/production
```

:::note
By default, the full error stack trace is send to a local file, `./errors.log`. To see the full error stack trace directly from the console, use the `--verbose` flag.
:::

### 7. Testing

Let's get started testing the project. We will perform three actions:

- Add a couple of posts
- Retrieve all posts
- Retrieve a specific post

Booster applications provide you with a GraphQL API out of the box. You send commands using
_mutations_ and get read models data using _queries_ or _subscriptions_.

In this section, we will be sending requests by hand using the free [Altair](https://altair.sirmuel.design/) GraphQL client,
which is very simple and straightforward for this guide. However, you can use any client you want. Your endpoint URL should look like this:

```text
<httpURL>/graphql
```

#### 7.1 Creating posts

Let's use two mutations to send two `CreatePost` commands.

```graphql
mutation {
  CreatePost(
    input: {
      postId: "95ddb544-4a60-439f-a0e4-c57e806f2f6e"
      title: "Build a blog in 10 minutes with Booster"
      content: "I am so excited to write my first post"
      author: "Boosted developer"
    }
  )
}
```

```graphql
mutation {
  CreatePost(
    input: {
      postId: "05670e55-fd31-490e-b585-3a0096db0412"
      title: "Booster framework rocks"
      content: "I am so excited for writing the second post"
      author: "Another boosted developer"
    }
  )
}
```

The expected response for each of those requests should be:

```json
{
  "data": {
    "CreatePost": true
  }
}
```

:::note
In this example, the IDs are generated on the client-side. When running production applications consider adding validation for ID uniqueness. For this example, we have used [a UUID generator](https://www.uuidgenerator.net/version4)
:::

#### 7.2 Retrieving all posts

Let's perform a GraphQL `query` that will be hitting our `PostReadModel`:

```graphql
query {
  PostReadModels {
    id
    title
    author
  }
}
```

It should respond with something like:

```json
{
  "data": {
    "PostReadModels": [
      {
        "id": "05670e55-fd31-490e-b585-3a0096db0412",
        "title": "Booster framework rocks",
        "author": "Another boosted developer"
      },
      {
        "id": "95ddb544-4a60-439f-a0e4-c57e806f2f6e",
        "title": "Build a blog in 10 minutes with Booster",
        "author": "Boosted developer"
      }
    ]
  }
}
```

#### 7.3 Retrieving specific post

It is also possible to retrieve specific a `Post` by adding the `id` as input, e.g.:

```graphql
query {
  PostReadModel(id: "95ddb544-4a60-439f-a0e4-c57e806f2f6e") {
    id
    title
    author
  }
}
```

You should get a response similar to this:

```json
{
  "data": {
    "PostReadModel": {
      "id": "95ddb544-4a60-439f-a0e4-c57e806f2f6e",
      "title": "Build a blog in 10 minutes with Booster",
      "author": "Boosted developer"
    }
  }
}
```

### 8. Removing the stack

It is convenient to destroy all the infrastructure created after you stop using
it to avoid generating cloud resource costs. Execute the following command from
the root of the project. For safety reasons, you have to confirm this action by
writing the project's name, in our case `boosted-blog` that is the same used when
we run `new:project` CLI command.

<TerminalWindow>

```bash
> boost nuke -e production

? Please, enter the app name to confirm deletion of all resources: boosted-blog
```

</TerminalWindow>

> Congratulations! You've built a serverless backend in less than 10 minutes. We hope you have enjoyed discovering the magic of the Booster Framework.

### 9. More functionalities

This is a really basic example of a Booster application. The are many other features Booster provides like:

- Use a more complex authorization schema for commands and read models based on user roles
- Use GraphQL subscriptions to get updates in real-time
- Make events trigger other events
- Deploy static content
- Reading entities within command handlers to apply domain-driven decisions
- And much more...

Continue reading to dig more. You've just scratched the surface of all the Booster
capabilities!

## Examples and walkthroughs

### Creation of a question-asking application backend

In the following video, you will find how to create a backend for a question-asking application from scratch. This application would allow
users to create questions and like them. This video goes from creating the project to incrementally deploying features in the application.
You can find the code both for the frontend and the backend in <CLAskMeRepo>[this GitHub repo](https://github.com/boostercloud/examples/tree/master/askme)</CLAskMeRepo>.

<div align="center">
  <iframe
    width="560"
    height="315"
    src="https://www.youtube.com/embed/C4K2M-orT8k"
    title="YouTube video player"
    frameBorder="0"
    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
    allowFullScreen
  ></iframe>
</div>

### All the guides and examples

Check out the <CLExampleApps>[example apps repository](https://github.com/boostercloud/examples)</CLExampleApps> to see Booster in use.

# Current state and roadmap

[The roadmap](https://github.com/orgs/boostercloud/projects/2/views/2) is community-driven; the core team actively participates in the Booster community, listening to real users and prioritizing those issues and ideas that provide the most value for the majority. So don't hesitate to create issues or leave comments in [Discord](https://discord.gg/k7b4B8CDtT) and tell us about your questions and ideas.

AWS and Azure integrations are thoroughly tested (with unit and integration tests running automatically before every release), and are currently used in production in projects of all-sized organizations, from startups to massive enterprises.

# The "Booster Way"

Booster Framework follows the next principles:

* *Play nicely*: Booster is not here to replace your toolkit but to expand it. Booster's goal is to get along well with your existing auth, queues, databases, and services, providing a modern and swift tool to build new functionalities that take full advantage of the cloud. Booster is still a Node.js application that you can extend with any tool from your Node.js environment.
* *Domain Driven Design first:* Software should be designed around business-level concepts to enhance the team's communication. All code in Booster is defined in terms of Commands, Events, Handlers, and Entities, limiting the need for artificial developer-only constructs.
* *CQRS and Event-Sourcing:* Booster is designed around the concepts of CQRS and Event-Sourcing. This design has many advantages regarding scalability and data management. It even allows you to travel back in time!
* *The cloud is the machine:* We believe that the developers' tools should create infrastructure transparently in the same way that a compiler hides the details of the target processor. We often think about Booster as the "TypeScript-to-Cloud compiler."
* *True Serverless*: Serverless is about to stop caring about your servers, but many implementations still require long YAML files to describe your infrastructure, and you need to know what you're doing. True Serverless means that you don't even care about cloud configuration. Booster will figure it out for you based on the code structure you write.
* *Convention over Configuration:* We prefer to provide standardized highly-opinionated modules than highly-configurable ones. This helps us to keep your code simple and follow the best practices when deploying your applications to the cloud. Decorating your classes with the provided semantic decorators also helps abstract most of the boilerplate code.
* *Don't Repeat Yourself (Extreme edition):* /The only code that matters is the one that makes your application different/. We push the TypeScript structure and type system to the limit to avoid writing repetitive code, like object-to-JSON serializations, API or database schemas, or redundant architecture layers. Boster understands the semantics of your code and connects the dots.
* *Self-documenting APIs* We adopted GraphQL because it's a self-documenting standard. You can grab a standard GraphQL client like [ApolloClient](https://github.com/apollographql/apollo-client) and start using a Booster backend right away with no complicated integrations.
* *Developer productivity:* Software development is fun, and a modern tool should make it even more fun, reducing the need for mundane tasks. Booster provides code generators to help you quickstart new projects and objects, and the framework types and APIs are hand-crafted to help your IDE help you.

# Contributing

You can join the conversation and start contributing in any of the following ways:
* [Say hello in Discord](https://discord.gg/bDY8MKx)
* [Create a new issue in Github](https://github.com/boostercloud/booster/issues/new/choose)
* [Try the framework and let us know how you liked it!](https://docs.booster.cloud/#/chapters/02_getting-started)

Please refer to [`CONTRIBUTING.md`](./CONTRIBUTING.md) for more details. Pull requests are welcome. For major changes, please
open an issue first to discuss what you would like to change.

# License

The Booster Framework is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for more details.

# Resources

* [Website](https://boosterframework.com)
* [Documentation](https://docs.booster.cloud)
* [Step-by-step guides and examples](docs/examples)
* [Join the conversation in Discord](https://discord.gg/k7b4B8CDtT)
* [Twitter](https://twitter.com/boostthecloud)
* [Demos and more on Youtube](https://www.youtube.com/channel/UCpUTONI8OG19pr9A4cn35DA)
* [Rocket to the Cloud Podcast](https://www.youtube.com/channel/UCxUYk1SVyNRCGNV-9SYjEFQ)
* [Booster in Dev.to](https://dev.to/boostercloud)





