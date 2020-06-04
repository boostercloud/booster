# Booster Documentation

## Introduction

### What is Booster?

### Booster Principles

### Why using Booster

## Getting started

### Installing Booster

You can develop with Booster using any of the following operating systems:

- Linux
- macOS
- Windows (Native and WSL)

Booster hasn't been tested under other platforms like BSD. By using them, you may face
unknown issues, so proceed at your own risk.

#### Prerequisites

##### Install Node.js

The minimal required Node.js version is `v12`. Download the installer
[from nodejs website](https://nodejs.org/en/), or install it using your system's package
manager.

###### Ubuntu

```shell
curl -sL https://deb.nodesource.com/setup_14.x | sudo -E bash -
sudo apt install nodejs
```

###### macOS

```shell
brew install node
```

###### Windows

```shell
choco install nodejs
```

Verify that it was installed properly by checking so from your terminal:

```shell
$ node -v
v13.12.0

$ npm -v
6.14.4
```

As soon as you have a Node.js version higher than `v12`, and an `npm` version higher than
`6`, you are good to go. Just note that `npm` comes with node, you don't have to install
it apart.

Alternatively, we recommend you to use a version manager for dealing with different Node.js
versions:

- [`nvm`](https://github.com/nvm-sh/nvm) - Works with macOS, Linux, and Windows Subsystem for Linux
- [`nvm-windows`](https://github.com/coreybutler/nvm-windows) - Works with native Windows

##### Set up an AWS account

This step is optional; Booster is a cloud-native framework, meaning that your application
will be deployed to the cloud using different cloud providers. By default, Booster uses the
[AWS Provider](framework-providers-aws), so you need an AWS account. You can always omit
this step if you only want to get a grip of Booster or test it locally without making a
deployment.

Note:

> Booster is and will always be free, but the resources used by the cloud providers are
> not. All the resources used by the AWS Provider are part of the
> [AWS free tier](https://aws.amazon.com/free). Even if you are not eligible for it,
> you can still test your app, and it shouldn't cost more than a few cents. Still,
> **we recommend you to un-deploy your application after finishing the tests if you don't
> plan to use it anymore**.

Now it is a good time to create that AWS account, you can do so from
[the AWS console registration](https://portal.aws.amazon.com/billing/signup).

Once you've registered yourself, you have to generate an access key for Booster. To do so,
login into the [AWS Console](https://console.aws.amazon.com), and click on your account
name on the top-right corner.

![aws account menu location](./img/aws-account-menu.png)

A menu will open, click on **My security credentials** and it will take you to the
Identity and Access Management panel. Once there, create an access key:

![create access key button location](./img/aws-create-access-key.png)

A pop-up will appear, **don't close it!**. Create a folder called `.aws` under your home
folder, and a file called `credentials` with this template:

```ini
# ~/.aws/credentials
[default]
aws_access_key_id = <YOUR ACCESS KEY ID>
aws_secret_access_key = <YOUR SECRET ACCESS KEY>
```

#### Installing the Booster CLI

Booster comes with a command-line tool that helps you generating boilerplate code,
testing and deploying the application, and deleting all the resources in the cloud. All
the stable versions are published to [`npm`](https://www.npmjs.com/package/@boostercloud/cli),
these versions are the recommended ones, as they are well documented, and the changes are
stated in the release notes.

To install the Booster CLI run this:

```shell
npm install --global @boostercloud/cli
```

Verify the Booster CLI installation with the `boost version` command. You should get back
something like

```shell
$ boost version
@boostercloud/cli/0.4.1 darwin-x64 node-v13.12.0
```

### Your first Booster app in 10 minutes

In this section, we will go through all the necessary steps to have the backend up and
running for a blog application in just a few minutes. The steps to follow will be:

- [Create project](#1-create-the-project)
- [First command](#2-first-command)
- [First event](#3-first-event)
- [First entity](#4-first-entity)
- [First read model](#5-first-read-model)
- [Deployment](#6-deployment)
- [Testing](#7-testing)
  - [Creating posts](#71-creating-posts)
  - [Retrieving all posts](#72-retrieving-all-posts)
  - [Retrieving specific post](#73-retrieving-specific-post)
- [Removing stack](#8-removing-stack)
- [More functionalities](#9-more-functionalities)

#### 1. Create the project

First of all, we will use the Booster CLI to create a project. Run this command and follow
the instructions, when asked for the provider, select AWS as that is what we have
configured [here](#set-up-an-aws-account)

```shell
> boost new:project boosted-blog

...

ℹ boost new 🚧
✔ Creating project root
✔ Generating config files
✔ Installing dependencies
ℹ Project generated!
```

> Booster's new: commands follows this structure
>
> - `boost` is the Booster CLI
> - `new:<resource>` new is a CLI command, :project tells booster the kind of resource
> - `boosted-blog` is a parameter for the `new:project` command
> project name

The `new:project` command generates some scaffolding for you. The project name will be the
project's root so `cd` to it:

```shell
cd boosted-blog
```

There you should have these files and directories already generated:

```text
boosted-blog
├── .eslintignore
├── .gitignore
├── package-lock.json
├── package.json
├── src
│   ├── commands
│   ├── common
│   ├── config
│   │   └── config.ts
│   ├── entities
│   ├── events
│   └── index.ts
└── tsconfig.json
```

Now open the project in your favorite editor, e.g. [Visual Studio Code](https://code.visualstudio.com/).

#### 2. First command

Commands define the input to our system, so we'll start by generating our first [command](#commands) to create posts. Use the command generator in the project's root directory as follows:

```bash
boost new:command CreatePost --fields postId:UUID title:string content:string author:string
```

The `new:command` generator creates a `CreatePost.ts` file in the `commands` folder:

```text
boosted-blog
└── src
    └── commands
        └── CreatePost.ts
```

We still need to define two more things:

1. Who is authorized to run this command.
1. And what events are being triggered when the command is executed.

We will create the first Event later on this guide so, let's start authorizing `'all'` to
run this command, to do so edit `src/commands/CreatePost.ts` like this:

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

  public handle(register: Register): void {
    register.events(/* YOUR EVENT HERE */)
  }
}

```

#### 3. First event

It makes sense to emit an event after executing the command `CreatePost`, something like
`PostCreated`. Let's do it by running this command:

```bash
boost new:event PostCreated --fields postId:UUID title:string content:string author:string
```

The `new:event` command generates a new file under the `src/events` directory. The name of
the file is the name of the Event:

```text
boosted-blog
└── src
    └── events
        └── CreatePost.ts
```

Edit the `entityID` method to return the id of the entity this Event is referring to. It
should look like this:

```typescript
// src/events/PostCreated.ts

@Event
export class PostCreated {
  public constructor(
    readonly postId: UUID,
    readonly title: string,
    readonly content: string,
    readonly author: string
  ) {}

  public entityID(): UUID {
    return this.postId;
  }
}
```

Now that we have an event, we can edit the `CreatePost` command to emit it. Let's change
the command's `handle` method to look like this:

```typescript
// src/commands/CreatePost.ts::handle
public handle(register: Register): void {
  register.events(new PostCreated(this.postId, this.title, this.content, this.author))
}
```

#### 4. First entity

Now we have a command and an event, however, we do not have any data representation of a
`Post`. To do so, we will create an `entity`.

```bash
boost new:entity Post --fields title:string content:string author:string --reduces PostCreated
```

This time Booster has created a file called `Post.ts` in the `src/entities` directory.
An entity is a reduction function applied to a series of events. For this example, we won't
take into account the previous state of the `Post`, so we will create a new one
from the Event's data.

```typescript
// src/entities/Posts.ts
@Entity
export class Post {
  public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}

  @Reduces(PostCreated)
  public static reducePostCreated(event: PostCreated, currentPost?: Post): Post {
    return new Post(event.postId, event.title, event.content, event.author)
  }
}
```

#### 5. First read model

So far, we have been adding data to our blog. Now, we will retrieve our data using read
models. A read model allows us to query our entire entities or just a subset of their
attributes. Let's project our `Post` Entity:

```bash
boost new:read-model PostReadModel --fields title:string author:string --projects Post:id
```

As you might guess, the command will generate a file called `PostReadModel.ts` under
`src/read-models`:

```text
boosted-blog
└── src
    └── read-models
        └── PostReadModel.ts
```

There are two things to do when creating a read model:

1. Define who is authorized to query it
1. and filter out the entity fields that are not needed in the read model

Read models and commands constitute the public API of a Booster application. With the
`CreatePost` command, we authorized `all` to execute it. This time we will be doing the
same for the read model. Furthermore, We will exclude the `content` field from the `Post`
entity so it won't be visible when querying this read-model. To do so, edit the class to
look like this:

```typescript
// src/read-models/PostReadModel.ts
@ReadModel({
  authorize: 'all'// Specify authorized roles here. Use 'all' to authorize anyone
})
export class PostReadModel {
  public constructor(
    public id: UUID,
    readonly title: string,
    readonly author: string,
  ) {}

  @Projects(Post, "postId")
  public static projectPost(entity: Post, currentPostReadModel?: PostReadModel): PostReadModel {
      return new PostReadModel(entity.id, entity.title, entity.author);
  }

}
```

#### 6. Deployment

We have everything we need to deploy our application to the cloud. It is as simple as
running this command:

```bash
boost deploy -e production
```

It will take a couple of minutes to deploy all the resources. Once finished, you will see
information about your stack endpoints. For this example, we will only need to pick the
API endpoint. Look into the output for something like `backend-application-stack.baseRESTURL`
e.g.

```text
https://<some random number>.execute-api.us-east-1.amazonaws.com/production/graphql
```

We are close to testing our app, and to do so, we will use the GraphQL API endpoint.
Let's address that in the next section.

#### 7. Testing

Let's get started testing the project. We will perform three actions:

- Add a couple of posts
- Retrieve all posts
- Retrieve a specific post

Booster applications provide you with a GraphQL API out of the box. Commands are mapped
to mutations and read models to queries. To perform calls to the GraphQL API, you can use
any HTTP client you want; we recommend you to use [Postwoman](https://postwoman.io/graphql).

##### 7.1 Creating posts

Use your favorite GraphQL client to run these mutations. No authorization header is
required since we have allowed `all` to execute our commands and query the read models.

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
      content: "I am so excited for writting the second post"
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

Note:

> The IDs are generated on the client-side. For this example, we are not validating ID
> uniqueness, consider using [a UUID generator](https://www.uuidgenerator.net/version4)
> when creating more Posts.

##### 7.2 Retrieving all posts

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

It should response something like:

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

##### 7.3 Retrieving specific post

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

#### 8. Removing stack

Now, let's destroy all the infrastructure created for running the application in the cloud.
Execute the following command from the root of the project. For security reasons, you have
to confirm this action by writing the project's name, in our case `boosted-blog` that is
the same used when we run `new:project` CLI command.

```bash
> boost nuke -e production

? Please, enter the app name to confirm deletion of all resources: boosted-blog
```

Note:
> Congratulations! You've built a serverless backend in less than 10 minutes. We hope you
> have enjoyed discovering the magic of the Booster Framework.

#### 9. More functionalities

The are many other options for your serverless backend built with Booster Framework:

- Authorize commands and read models based on different roles
- Use GraphQL subscriptions
- Make events to trigger other events
- Serving static content
- Reading entities within command handlers to apply domain-driven decisions
- and much more...

Please continue reading to get more details about Booster and its capabilities.
