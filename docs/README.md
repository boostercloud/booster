# Booster Documentation

## Introduction

> _Progress isn't made by early risers. It's made by lazy men trying to find easier ways to do something._ — [Robert A. Heinlein](https://en.wikipedia.org/wiki/Robert_A._Heinlein)

### What is Booster?

Booster is a new kind of framework to build scalable and reliable event-driven systems faster, reimagining the software development experience to maximize your team’s speed and reduce friction on every level.

Booster follows a Domain-Driven Design approach in which you define your application in terms that are understandable by anyone in your company. From a bird’s eye view your project is organized into:

* **Commands**: Define what a user can request from the system (i.e: Add an item to the cart)
* **Events**: Simple records of facts (i.e: User X added item Y to the cart Z)
* **Entities**: Data about the things that the people in your company talk about (i.e: Orders, Customers, etc.)
* **Handlers**: Code that processes commands, reacts to events to trigger other actions, or update the entities after new events happen.

Events are the cornerstone of a Booster application, and that’s why we say that Booster is an event-driven framework. Events bring us many of the differentiating characteristics of Booster:

* **Real-time**: Events can trigger other actions when they’re created, and updates can be pushed to the connected clients without extra requests.
* **High data resiliency**: Events are stored by default in an append-only database, so the data is never lost and it’s possible to recover any previous state of the system.
* **Decoupled by nature**: Dependencies only happen at data level, so it’s easier to evolve the code without affecting other parts of the system.

Before Booster, building an event-driven system with the mentioned characteristics required huge investments in hiring engineers with the needed expertise. Booster packs this expertise, acquired from real-case scenarios in high-scale companies, into a very simple tool that handles with the hard parts for you, even provisioning the infrastructure! 

We have redesigned the whole developer experience from scratch, taking advantage of the advanced TypeScript type system and Serverless technologies to go from project generation to a production-ready real-time GraphQL API that can ingest thousands of concurrent users in a matter of minutes.

Booster's ultimate goal is fulfilling the developer's dream of writing code at the application layer, in a domain-driven way that eases communications for the whole team, without caring about how anything else is done at the infrastructure level!

### Booster Principles

Booster takes a holistic and highly-opinionated approach at many levels:

* **Focus on business value**: The only code that makes sense is the code that makes your application different from any other.
* **Convention over configuration**: All the supporting code and configuration that is similar in all applications should be out of programmers’ sight.
* **Serverless-less**: Why go Serverless to avoid managing infrastructure when you can implicitly infer your Serverless architecture from your code and not even deal with that?
* **Scale smoothly**: A modern project shouldn't need to change their software architecture or rewrite their code in a different language just because they succeed and get a lot of users.
* **Event-source and CQRS**: Our world is event-driven, businesses are event-driven, and modern software maps better to reality when it’s event-driven. We have enough MVC frameworks already!
* **Principle of Abstraction**: Building an application is hard enough to have to deal with recurring low-level details like SQL, API design, or authentication mechanisms, so we tend to build more semantic abstractions on top of them.
* **Real-time first**: Client applications must be able to react to events happening in the backend and notice data changes.

### Why use Booster

Booster will fit like a glove in applications that are naturally event-driven like:

* Commerce applications (retail, e-commerce, omnichannel applications, warehouse management, etc.)
* Business management applications
* Communication systems

But it's a general-purpose framework that has several advantages over other solutions:

* **Faster time-to-market**: Booster can deploy your application to a production-ready environment from minute one, without complicated configurations or needing to invest any effort to design it. In addition to that, it features a set of code generators to help developers build the project scaffolding faster and focus on actual business code in a matter of seconds instead of dealing with complicated framework folklore.
* **Write less code**: Booster conventions and abstractions require less code to implement the same features. This not only speeds up development but combined with clear architecture guidelines also makes Booster projects easier to understand, iterate, and maintain.
* **All the advantages of Microservices, none of its cons**: Microservices are a great way to deal with code complexity, at least on paper. Services are isolated and can scale independently, and different teams can work independently, but that usually comes with a con: interfaces between services introduce huge challenges like delays, hard to solve cyclic dependencies, or deployment errors. In Booster, every handler function works as an independent microservice, it scales separately in its own lambda function, and there are no direct dependencies between them, all communication happens asynchronously via events, and all the infrastructure is compiled, type-checked and deployed atomically to avoid issues.
* **All the advantages of Serverless, without needing a degree in cloud technologies**: Serverless technologies are amazing and have made a project like Booster possible, but they're relatively new technologies, and while day after day new tools appear to make them easier, the learning curve is still quite steep. With Booster you'll take advantage of Serverless’ main selling points of high scalability and reduced hosting costs, without having to learn every detail from minute one.
* **Event-sourcing by default**: Similarly to Git repositories, Booster keeps all data changes as events indefinitely. This means that any previous state of the system can be recreated and replayed at any moment. This enables a whole world of possibilities for troubleshooting and auditing your system, or syncing development or staging environments with the production data to perform tests and simulations.
* **Booster makes it easy to build enterprise-grade applications**: Implementing an event-sourcing system from scratch is a challenging exercise that usually requires highly specialized experts. There are some technical challenges like eventual consistency, message ordering, and snapshot building. Booster takes care of all of that and more for you, lowering the curve for people that are starting and making expert lives easier.

## Getting started

### Installing Booster

You can develop with Booster using any of the following operating systems:

- Linux
- macOS
- Windows (Native and WSL)

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

- [`nvm`](https://github.com/nvm-sh/nvm) - Works with macOS, Linux, and Windows Subsystem
  for Linux
- [`nvm-windows`](https://github.com/coreybutler/nvm-windows) - Works with native Windows

##### Set up an AWS account

This step is optional; Booster is a cloud-native framework, meaning that your application
will be deployed to the cloud using different cloud providers. By default, Booster uses the
[AWS Provider](framework-providers-aws), so you need an AWS account. You can always omit
this step if you only want to get a grip of Booster or test it locally without making a
deployment.

Note:
> Booster is free to use, but notice that the resources deployed to your cloud provider
> might generate some expenses.
>
> In AWS, all the resources generated by Booster are part of the [AWS free tier](https://aws.amazon.com/free).
> When you're not eligible for the free tier, resources are charged on-demand. Deploying a
> Booster project and sending a few commands and queries should cost just a few cents.
>
> In any case, make sure to un-deploy your application with the command `boost nuke -e production`
> if you're not planning to keep using it.

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
project's root so `cd` into it:

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

Commands define the input to our system, so we'll start by generating our first
[command](#commands) to create posts. Use the command generator in the project's root
directory as follows:

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

As we mentioned before, commands are the input of our system. they're requested
by the users, validate the input, and store one or more events, so we have to
define two more things:

1. Who is authorized to run this command.
1. And what events are being triggered when the command is executed.

Booster allows you to define authorization strategies. We will cover that
later so, let's start by allowing anyone to send this command to our application
. To do that, add the string `'all'` to the the `authorize` parameter of the
`@command` decorator. Your `CreatePost` command should look like this:

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

Instead of storing full objects, Booster stores data in the form of events, which are
records of facts and the source of truth. We will save an event called `PostCreated`
containing the initial post info. Any change to a given `Post` will be a new event
emitted, for example, `PostUpdated`.

For now, let's emit our `PostCreated` event once we have successfully handled
our `CreatePost` command. You can generate the event with this generator:

```bash
boost new:event PostCreated --fields postId:UUID title:string content:string author:string
```

The `new:event` command generates a new file under the `src/events` directory.
The name of the file is the name of the event:

```text
boosted-blog
└── src
    └── events
        └── PostCreated.ts
```

All events in Booster must target an entity, so we need to implement an `entityID`
method. From there, We'll return the identifier of the post created, the field
`postID`. This identifier will be used by Booster later to build the final state
of the `Post` automatically. Edit the `entityID` method in `events/PostCreated.ts`
to return our `postID`:

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

We can do any validation in the command handler before storing the event, for our
example, we'll just save the received data in a `PostCreated` event.

#### 4. First entity

So far, our `PostCreated` event suggests we need a `Post` entity. Entities are a
representation of the current state, so an Entity reduces all the events with the same
`entityID`. Let's then use the entities generator:

```bash
boost new:entity Post --fields title:string content:string author:string --reduces PostCreated
```

This time Booster has created a file called `Post.ts` in the `src/entities` directory.

The generator creates one reducer function for each kind of event. As we only have one
event yet, it will create one function. The reducer functions in Booster work similarly to
the `reduce` callback functions in Node: they receive an event and the previous state and
generate a new version of the state. When we receive a `PostCreated` event, we just return
a new `Post` copying the fields from the event:

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

Entities represent the internal state of our system and can be queried from command or
event handlers to make business decisions or enforcing business rules.

#### 5. First read model

In a real application, we rarely want to publish our entire domain model (entities)
including all their fields. Also, different people may access one or other data depending
on their permissions. That's the goal of `ReadModels`. Client applications can query or
subscribe to them. A read model projects an entity so, let's project our `Post` and
produce a `PostReadModel`:

```bash
boost new:read-model PostReadModel --fields title:string author:string --projects Post:id
```

As you might guess, the read-model generator will generate a file called
`PostReadModel.ts` under `src/read-models`:

```text
boosted-blog
└── src
    └── read-models
        └── PostReadModel.ts
```

There are two things to do when creating a read model:

1. Define who is authorized to query or subscribe it
1. and filter out unneeded fields from the entity

Read models and commands compound the public API of a Booster application. With the
`CreatePost` command we authorized `all` to execute it, and this time we'll do the same
for the `PostReadModel`.

Just for learning, We also will exclude the `content` field from the `Post` entity so it
won't be visible.

To authorize anyone to query this read model, and filter out the content, edit the file to
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

At this point, we've learned:

- how to create a publicly accessible command
- we emitted an event to store the data
- we reduced the event into an entity
- and finally, we projected the entity into a read model that is also publicly accessible.

That's all; you already know the basics to build event-driven, CQRS-based applications
with Booster.

Let's deploy our application to the cloud to see it working. It is as simple as running
the deploy command:

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

Booster applications provide you with a GraphQL API out of the box. Commands are
mutations, and read models are queries. To perform calls to the GraphQL API, you can use
any HTTP client you want; we recommend you to use
[Postwoman](https://postwoman.io/graphql), which is free and includes great support for
GraphQL.

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

Note:

> In Booster, the IDs are generated on the client-side. When running production applications
> consider adding validation for ID uniqueness. For this example, we have used [a UUID generator](https://www.uuidgenerator.net/version4)

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

#### 8. Removing the stack

It is convenient to destroy all the infrastructure created after you stop using
it to avoid generating cloud resource costs. Execute the following command from
the root of the project. For safety reasons, you have to confirm this action by
writing the project's name, in our case `boosted-blog` that is the same used when
we run `new:project` CLI command.

```bash
> boost nuke -e production

? Please, enter the app name to confirm deletion of all resources: boosted-blog
```

Note:
> Congratulations! You've built a serverless backend in less than 10 minutes. We hope you
> have enjoyed discovering the magic of the Booster Framework.

#### 9. More functionalities

The are many other options for your serverless backend built with Booster Framework:

- Build more complex authorization schemas for commands and read models based on user roles
- Use GraphQL subscriptions to get updates in real-time
- Make events trigger other events
- Deploy static content
- and much more...

- Authorize commands and read models based on different roles
- Use GraphQL subscriptions
- Make events to trigger other events
- Serving static content
- Reading entities within command handlers to apply domain-driven decisions
- and much more...

Continue reading to dig more; you've just scratched the surface of all the Booster capabilities!
