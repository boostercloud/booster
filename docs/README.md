# Booster Documentation

- [Booster Documentation](#booster-documentation)
- [A bird's eye view of Booster](#a-birds-eye-view-of-booster)
  - [Think about user actions, not endpoints](#think-about-user-actions-not-endpoints)
  - [Time travel through your data](#time-travel-through-your-data)
  - [Data modelling](#data-modelling)
  - [Combining and transforming your data](#combining-and-transforming-your-data)
  - [GraphQL is hard? Who said that?](#graphql-is-hard-who-said-that)
  - [Fasten your seatbelts](#fasten-your-seatbelts)
- [Installing Booster](#installing-booster)
  - [Supported operating systems](#supported-operating-systems)
  - [Install Node.js](#install-nodejs)
  - [Set up your AWS account](#set-up-your-aws-account)
  - [Installing the Booster CLI](#installing-the-booster-cli)
    - [Installing using `npm`](#installing-using-npm)
    - [Installing the development version](#installing-the-development-version)
    - [Verify that you have Booster installed](#verify-that-you-have-booster-installed)
- [Your first Booster app in 10 minutes](#your-first-booster-app-in-10-minutes)
  - [1. Create project](#1-create-project)
  - [2. First command](#2-first-command)
  - [3. First event](#3-first-event)
  - [4. First entity](#4-first-entity)
  - [5. First read model](#5-first-read-model)
  - [6. Deployment](#6-deployment)
  - [7. Testing](#7-testing)
    - [7.1 Creating posts](#71-creating-posts)
    - [7.2 Retrieving all posts](#72-retrieving-all-posts)
    - [7.3 Retrieving specific post](#73-retrieving-specific-post)
  - [8. Removing stack](#8-removing-stack)
  - [9. More functionalities](#9-more-functionalities)
- [Architecture and core concepts](#architecture-and-core-concepts)
- [Commands and Command Handlers - The Write Pipeline](#commands-and-command-handlers---the-write-pipeline)
- [Events](#events)
  - [Event Handlers](#event-handlers)
- [Entities](#entities)
  - [Reading Entity "state"](#reading-entity-%22state%22)
- [Read Models - The Read Pipeline](#read-models---the-read-pipeline)
- [Authentication and Authorization](#authentication-and-authorization)
- [Deploying](#deploying)
  - [Configure your provider credentials](#configure-your-provider-credentials)
  - [Deploy your project](#deploy-your-project)
  - [Deleting your cloud stack](#deleting-your-cloud-stack)
- [Booster Cloud Framework REST API](#booster-cloud-framework-rest-api)
  - [Authentication and Authorization API](#authentication-and-authorization-api)
    - [Sign-up](#sign-up)
          - [Endpoint](#endpoint)
          - [Request body](#request-body)
          - [Response](#response)
          - [Errors](#errors)
    - [Sign-in](#sign-in)
          - [Endpoint](#endpoint-1)
          - [Request body](#request-body-1)
          - [Response](#response-1)
          - [Errors](#errors-1)
    - [Sign-out](#sign-out)
          - [Endpoint](#endpoint-2)
          - [Request body](#request-body-2)
          - [Response](#response-2)
          - [Errors](#errors-2)
  - [Write API (commands submission)](#write-api-commands-submission)
      - [Request body:](#request-body-3)
  - [Read API (retrieve a read model)](#read-api-retrieve-a-read-model)
    - [Get a list](#get-a-list)
    - [Get a specific read model](#get-a-specific-read-model)
- [Frequently asked questions](#frequently-asked-questions)

# A bird's eye view of Booster

Booster was synthesized from years of experience in high availability,
and high performance software scenarios. Most implementation details in
these situations repeat time after time. Booster abstracts them in order
for you to focus on what matters: The domain of your application.

In this section you'll get a small grasp of how Booster works, but this is
not a full guide.

Just get a taste of Booster, and when you're ready, you can
[install Booster](#installing-booster),
begin [writing your first Booster app](#your-first-booster-app),
or even get into the [in-depth reference documentation](#booster-in-depth).

## Think about user actions, not endpoints

```typescript
@Command({
  authorize: 'all',
})
export class SendMessage {
  public constructor(readonly chatroomID: UUID, readonly messageContents: string) {}

  public handle(register) {
    const timestamp = new Date()
    register.events(new MessageSent(this.chatroomID, this.messageContents, timestamp))
  }
}
```

A user action is modeled in Booster as a Command.

Similar to controllers, command handlers serve as one of the entry points to your system,
they scale horizontally automatically.

Commands are defined as decorated TypeScript classes with some fields and a `handle` method.

## Time travel through your data

```typescript
@Event
export class MessageSent {
  public constructor(readonly chatroomID: UUID, readonly messageContents: string, readonly timestamp: Date) {}

  public entityID(): UUID {
    return this.chatroomID
  }
}
```

Instead of mutating your data in a database, Booster stores an infinite
stream of events. You get the possibility of seeing how your data changes
through time and space.

Need to fix a bug that happened one year ago? Just change the event generation
and re-run it from the past.

Events, like Commands, are just TypeScript classes. No strings attached.

## Data modelling

```typescript
interface Message {
  contents: string
  hash: string
}

@Entity
export class Chatroom {
  public constructor(readonly id: UUID, readonly messages: Array<Message>, readonly lastActivity: Date) {}

  @Reduces(MessageSent)
  public static reduceMessageSent(event: MessageSent, prev?: Chatroom): Chatroom {
    const message = {
      contents: event.messageContents,
      hash: md5sum.digest(event.messageContents),
    }

    if (prev) {
      prev.messages.push(message)
      prev.lastActivity = event.timestamp > prev.lastActivity ? event.timestamp : prev.lastActivity
      return prev
    }

    return new Chatroom(event.chatroomID, [message], event.timestamp)
  }
}
```

Define your data with TypeScript types, without having to learn a new
data definition language.

Entities are the central part of your domain. They are a representation
of your event stream at some point in time.

You specify the fields of your entity as all the important things that
will be generated from your events.

No alien libraries, no need about thinking in the state of the database,
just plain TypeScript and some cool decorators.

Database failure? Don't fret! The entities will be regenerated from the
events.

On top of that, Entities serve as automatic snapshots, so your app is
very fast!

## Combining and transforming your data

```typescript
@ReadModel
export class ChatroomActivity {
  public constructor(readonly id: UUID, readonly lastActivity: Date) {}

  @Projection(Chatroom, 'id')
  public static updateWithChatroom(chatroom: Chatroom, prev?: ChatroomActivity): ChatroomActivity {
    return new ChatroomActivity(chatroom.id, chatroom.lastActivity)
  }
}
```

Most of the time, you don't want to expose all of the data you are storing
in your system. You might want to hide some parts, transform others.

Also, you might want to combine some entities into one object so the
client can read them more efficiently.

Get your data delivered in the shape that you want, instantly to your
client. Booster will push the changes live, so you only have to focus
in rendering it or consuming in the way you require.

## GraphQL is hard? Who said that?

```graphql
# Send a command
mutation {
  SendMessage(input: { chatroomID: 1, messageContents: "Hello Booster!" })
}

# Subscribe to a read model
subscription {
  ChatroomActivity(id: 1) {
    id
    lastActivity
  }
}
```

GraphQL is nice on the client side, but on the backend, it requires you
to do quite some work. Defining resolvers, schema, operations, and
friends, takes some time, and it is not the most thrilling work you can
do. Especially when your domain has nothing to do with managing a GraphQL
API.

Each Command is mapped to a GraphQL mutation, and each ReadModel, is mapped
to a GraphQL query or subscription.

Just write your Booster app as you would do normally, and enjoy a GraphQL
API for free, with it's schema, operations and everything.

## Fasten your seatbelts

This is a simplified view of Booster. It supports more other features
that will definitely speed-up your development process. Among them:

- Automatic Migrations - with them you can easily introduce changes in your data
- Versioning - to do stuff like A/B testing
- Authentication - integrated with your cloud provider, so you don't have to manage security yourself

All of this under the best practices of security and privacy of your cloud provider.
Booster defaults to the most strict option, so you don't have to worry about security
configuration beforehand.

Thrilled already? Jump to [the installation steps](#installing-booster), read
[how to write your first Booster app](#your-first-booster-app), and
join the community in the [Spectrum chat](https://spectrum.chat/boostercloud).

---

# Installing Booster

## Supported operating systems

You can develop with Booster using any of the following operating systems:

- Linux
- MacOS
- Windows (Native and WSL)

Booster hasn't been tested under other platforms like BSD, if you want to
develop under those, proceed at your own risk!

## Install Node.js

```shell
# Ubuntu
$ curl -sL https://deb.nodesource.com/setup_14.x | sudo -E bash -
$ sudo apt install nodejs

# MacOS
$ brew install node

# Windows
> choco install nodejs
```

Booster is a TypeScript framework that benefits from the Node.js ecosystem, it
has been tested under versions newer than `v12`, so make sure that you install one
accordingly.

If you don't have Node.js installed, you can download an installer [from it's website](https://nodejs.org/en/), or you can install it using your system's
package manager.

- **Ubuntu** - using [`apt`](https://wiki.debian.org/Apt)
- **MacOS** - using [`brew`](https://brew.sh/)
- **Windows** - using [`chocolatey`](https://chocolatey.org/)

If for some reason you are working with other projects that require a different
Node.js version, we recommend that you use a version manager like:

- [`nvm`](https://github.com/nvm-sh/nvm) - Works with MacOS, Linux and WSL
- [`nvm-windows`](https://github.com/coreybutler/nvm-windows) - Works with native Windows

> Verify your Node.js and `npm` versions

```shell
$ node -v
v13.12.0

$ npm -v
6.14.4
```

After you've installed Node.js, you can verify that it was installed properly by
checking so from your terminal.

Make sure that Node.js is newer than `v12` and `npm` (comes installed with Node.js) is newer than `6`.

## Set up your AWS account

Booster is a cloud-native framework, meaning that your application will be deployed
to the cloud, using different cloud services. Right now, it only supports AWS, but
given Booster's abstractions, a provider package can be easily created to support
other cloud providers.

To follow the documentation locally and get a grip of Booster, you don't need a
cloud provider, but to deploy, and test your application, you will need it.

<aside class="warning">
<b>Note:</b> Booster is and always will be free, but the resources you use in AWS
are not. All of the resources are part of the AWS free tier, and even if you are not
eligible for it, for testing your app it shouldn't cost you more than a few cents.
Still, <b>we recommend you undeploy your app
after you finished testing it, and you don't plan using it anymore.</b>
</aside>

Now it is a good time to create that AWS account, you can do so from
[the AWS console registration](https://portal.aws.amazon.com/billing/signup).

Once you've registered yourself, you will need to configure your system to use your
account. To do so, login into the [AWS Console](https://console.aws.amazon.com), and
click on your account name on the top-right corner.

![aws account menu location](./img/aws-account-menu.png)

A menu will open, click on **My security credentials** and it will take you to the
Identity and Access Management panel. Once there, create an access key:

![create access key button location](./img/aws-create-access-key.png)

A pop-up will appear, **don't close it!**.

```ini
[default]
aws_access_key_id = <YOUR ACCESS KEY ID>
aws_secret_access_key = <YOUR SECRET ACCESS KEY>
```

Now create a folder called `.aws` under your home folder, and a file called
`credentials` inside of it.

Paste the template you see on the right, and fill with the keys that appeared
in the popup of the website. Save the file. You are ready to go!

## Installing the Booster CLI

Booster comes with a command line tool that generates boilerplate code, and also,
deploys, and deletes your application resources in the cloud.

### Installing using `npm`

```shell
npm install --global @boostercloud/cli
```

All stable versions are published to [`npm`](https://npmjs.com), to install the
Booster CLI, use the command on the right.

These versions are the recommended ones, as they are well documented, and the
changes are stated in the release notes.

### Installing the development version

If you like to live on the bleeding edge, you might want to install the development
version, but beware, **here might be bugs and unstable features!**

```shell
# Inside a terminal
$ npm install -g verdaccio

# Open a new terminal, and run this command
$ verdaccio

# Go back to the first terminal
$ npm adduser --registry http://localhost:4873
$ git clone git@github.com:boostercloud/booster.git
$ cd booster
$ lerna publish --registry http://localhost:4873 --no-git-tag-version --canary
# Specify some version that you will remember here, i.e. 0.3.0-my-alpha
$ git stash -u
$ npm install --registry http://localhost:4873 @boostercloud/cli
```

Make sure that you have [Git](https://git-scm.com/) installed. You can verify this
by running `git help`.

Follow the steps on the right, they will:

- Install [`verdaccio`](https://verdaccio.org/), an `npm` local proxy
- Run `verdaccio`, and register yourself locally
- Get the Booster source code
- Install `lerna`, the tool that manages all the Booster packages
- Publish the Booster version locally
- Install the Booster development version

If everything went correctly, you should have the Booster CLI installed.

<aside class="notice">
Remember to change the dependency versions in your project's <code>package.json</code> to the version you've specified by following the steps.
</br>
</br>
Also, when installing the dependencies, you have to specify the registry like so:
</br>
<code>
$ npm install --registry http://localhost:4873
</code>
</aside>

### Verify that you have Booster installed

To verify that the Booster installation was successful, enter the following
command into your terminal: `boost version`

If everything went well, you should get something like `@boostercloud/cli/0.3.0`

You are now ready to [write your first Booster app](#your-first-booster-app)!

---

# Your first Booster app in 10 minutes

In this section, we will go through all the necessary steps to have the backend for a blog application up and running in a few minutes.

The steps to follow will be:

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
- [Further improvements](#9-further-improvements)

## 1. Create project

> Create project

```bash
boost new:project <project-name>
```

First of all, we need to create a base Booster project. To do so, we will use the Booster CLI, which
can be invoked by typing `boost` inside of a terminal.

```text
|- <your-project-name>
  |- src
    |- commands
    |- common
    |- entities
    |- events
    |- read-models
    ...
```

It will generate a folder
with your selected project name.

You will need to answer a few questions in order to configure the project. The last step asks you about a _provider package_, for this tutorial, select _AWS_.

Once the project has been successfully created, you will need to move to the new directory, you can do so by typing `cd <your project name>` in a terminal.

Now open the project in your most preferred IDE, e.g. [Visual Studio Code](https://code.visualstudio.com/).

## 2. First command

We will now define our first command, which will allow us to create posts in our blog.

> New command

```bash
boost new:command CreatePost --fields postId:UUID title:string content:string author:string
```

In a terminal, from the root of your project, type:

```text
|- <your-project-name>
  |- src
    |- commands/CreatePost.ts
```

These commands creates most of the code for us, which can be seen in

However, we still need to define a couple of things in this file:

- Who can trigger our command
- What events should be triggered

For the first part, we will let anyone to trigger it. To do so, configure the `authorize` command option to `"all"` (yes, between quotes, it is a string). If you cannot find it, it is right after the `@Command` decorator.

Additionally, the current `CreatePost` command will not trigger any event, so we will have to come back later to set the event that this command will fire up. This is done in the `handle` method of the command class. Leave it as it is for now.

> `CreatePost` command

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

If everything went well, you should have now the code you can see on the right.

## 3. First event

> New event

```bash
boost new:event PostCreated --fields postId:UUID title:string content:string author:string
```

In this type of backend architectures, events can be triggered by commands or by other events. We will create an event that defines a `Post` creation.

```text
|- <your-project-name>
  |- src
    |- events/PostCreated.ts
```

You will realize that a new file has been created:

> Define entity id

```typescript
public entityID(): UUID {
  return this.postId
}
```

There is one small thing that we have to define in the above file, which is the returned value for `EntityID()`. We will set the post `UUID`. It should look like this:

> `PostCreated` event

```typescript
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

Your event should look like this:

> Add event to `CreatePost` Command

```typescript
public handle(register: Register): void {
  register.events(new PostCreated(this.postId, this.title, this.content, this.author))
}
```

Now we can go back to the command we created before and add our new event `PostCreated` to the register of events. Your `handle` should look like this:

## 4. First entity

> New entity

```bash
boost new:entity Post --fields title:string content:string author:string --reduces PostCreated
```

We have now created a command and an event, however, we do not have any data representation of a `Post`. As a result, we will create an `entity`.

```text
|- <your-project-name>
  |- src
    |- entities/Post.ts
```

Another file has been created in your project, you will need to add the implementation of its reduction:

> Reduction

```typescript
@Reduces(PostCreated)
public static reducePostCreated(event: PostCreated, currentPost?: Post): Post {
return new Post(event.postId, event.title, event.content, event.author)
}
```

In the future, we may want to _project_ events for this `Post` entity that require retrieving current `Post` values. In that case we would need to make use of `currentPost` argument.

> `Post` entity

```typescript
@Entity
export class Post {
  public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}

  @Reduces(PostCreated)
  public static reducePostCreated(event: PostCreated, currentPost?: Post): Post {
    return new Post(event.postId, event.title, event.content, event.author)
  }
}
```

The full code for the entity can be seen on the right.

## 5. First read model

> New read model

```bash
boost new:read-model PostReadModel --fields title:string content:string author:string --projects Post
```

Almost everything is set-up. We just need to provide a way to view the `Posts` of our blog. For that, we will create a `read model`.

```text
|- <your-project-name>
  |- src
    |- read-models/PostReadModel.ts
```

Once the read-model code has been generated, we will also need to define a few things:

- Who can read the `Posts`
- How the data is manipulated before returning it

```typescript
@ReadModel({
  authorize: 'all'// Specify authorized roles here. Use 'all' to authorize anyone
})
```

To make it easy, we will allow anyone to read it:

```typescript
@Projects(Post, "id")
public static projectPost(entity: Post, currentPostReadModel?: PostReadModel): PostReadModel {
  return new PostReadModel(entity.id, entity.title, entity.content, entity.author)
}
```

and we will project the whole entity

> `PostReadModel` read model

```typescript
@ReadModel({
  authorize: 'all', // Specify authorized roles here. Use 'all' to authorize anyone
})
export class PostReadModel {
  public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}

  @Projects(Post, 'id')
  public static projectPost(entity: Post, currentPostReadModel?: PostReadModel): PostReadModel {
    return new PostReadModel(entity.id, entity.title, entity.content, entity.author)
  }
}
```

The read model should look like the code on the right:

## 6. Deployment

```bash
boost deploy -e production
```

Everything we need for a basic project is set. It is time to deploy it:

It will take a couple of minutes to deploy all the resources.

> Example GraphQL endpoint

```text
https://<some random number>.execute-api.us-east-1.amazonaws.com/production/graphql
```

When the the serverless backend is successfully deployed you will see information about your stack endpoints. For this basic project we will only need to pick the REST API endpoint, reflected in the output as `backend-application-stack.baseRESTURL`, and append `/graphql` at the end, e.g.:

We will use this GraphQL API endpoint to test our backend.

## 7. Testing

Let's get started testing the project. We will perform three actions:

- Add a couple of posts
- Retrieve all posts
- Retrieve a specific post

To perform the GraphQL queries, you might want to use something like [Postwoman](https://postwoman.io/graphql), although `curl` would also work.

### 7.1 Creating posts

> The first GraphQL mutation:

```graphql
mutation {
  CreatePost(
    input: {
      postId: "95ddb544-4a60-439f-a0e4-c57e806f2f6e"
      title: "This is my first post"
      content: "I am so excited to write my first post"
      author: "Some developer"
    }
  )
}
```

> The second GraphQL mutation:

```graphql
mutation {
  CreatePost(
    input: {
      postId: "05670e55-fd31-490e-b585-3a0096db0412"
      title: "This is my second post"
      content: "I am so excited to write my second post"
      author: "The other developer"
    }
  )
}
```

We will perform two GraphQL `mutation` queries in order to add information:

> The expected response for each of the requests above should be:

```json
{
  "data": {
    "CreatePost": true
  }
}
```

We should now have two `Posts` in our backend, no authorization header is required since we have allowed `all` access to our commands and read models.

> GraphQL query, all posts

```graphql
query {
  PostReadModels {
    id
    title
    content
    author
  }
}
```

### 7.2 Retrieving all posts

In order to retrieve the information we just sent, lets perform a GraphQL `query` that will be hitting our read model `PostReadModel`:

> Query all posts response

```json
{
  "data": {
    "PostReadModels": [
      {
        "id": "05670e55-fd31-490e-b585-3a0096db0412",
        "title": "This is my second post",
        "content": "I am so excited to write my second post",
        "author": "The other developer"
      },
      {
        "id": "95ddb544-4a60-439f-a0e4-c57e806f2f6e",
        "title": "This is my first post",
        "content": "I am so excited to write my first post",
        "author": "Some developer"
      }
    ]
  }
}
```

You should expect a response similar to this:

> GraphQL query, specific posts

```graphql
query {
  PostReadModel(id: "95ddb544-4a60-439f-a0e4-c57e806f2f6e") {
    id
    title
    content
    author
  }
}
```

### 7.3 Retrieving specific post

It is also possible to retrieve specific a `Post` by adding the `id` as input, e.g.:

> Query specific posts response

```json
{
  "data": {
    "PostReadModel": {
      "id": "95ddb544-4a60-439f-a0e4-c57e806f2f6e",
      "title": "This is my first post",
      "content": "I am so excited to write my first post",
      "author": "Some developer"
    }
  }
}
```

You should expect a response similar to this:

## 8. Removing stack

Now, let's undeploy our backend.

<aside class="warning">
<b>Remember:</b> It costs you money to have it on idle!
</aside>
> Undeploy stack

```bash
boost nuke -e production
```

To do so, execute the following command from the root of your project, in a terminal:

It will ask you to verify the project name, it will be the same one that it was written when we created the project. If you don't remember the name, go to `config/production.ts` and copy the `name` field.

## 9. More functionalities

The are many other options for your serverless backend built with Booster Framework:

- GraphQL subscriptions
- Securing requests depending on user roles
- Events that trigger more events
- Reading entity snapshots in handlers to apply domain-driven decisions
- and much more...

But we won't be covering them in this section. Keep reading if you want to know more!

<aside class="success">
Congratulations! you've built a serverless backend in less than 10 minutes. We hope you have enjoyed discovering the magic of Booster Framework, please keep reading if you want to know more about it.
</aside>

---

# Architecture and core concepts

Booster’s architecture is heavily inspired by the [CQRS](https://www.martinfowler.com/bliki/CQRS.html) and [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) patterns.
These patterns have proven to work well for highly-distributed high available systems, being a tool to make resilient
software that is fast and scales very well, especially in distributed scenarios.

The Booster high-level architecture diagram looks like this:
![Booster architecture](./img/booster-arch.png)

With these patterns combined, in a Booster Application:

- The _write operations_ are separated from the _read operations_ (called [_commands_](#commands-and-command-handlers-the-write-pipeline) and [_queries_](#read-models-the-read-pipeline), respectively) and dependencies are limited to data.
- Instead of storing and mutating the data in a single stateful database, Booster stores the state as a virtually infinite append-only list of events (think of your bank account, where all the movements are stored as individual movements).
- The event stream is the system source of truth, and the "current state" can be queried anytime reducing it on the fly as [entities](#entities).

This architecture has many advantages:

- The whole architecture is designed for high availability and eventual consistency.
- The code is much easier to change because modules are loosely coupled.
- Old code can live along with new code without affecting each other, so it's easier to smoothly test and introduce new features.
- System boundaries are clearly defined and are easy to maintain.

It's usually non-trivial to get event-driven architecture design right and implement a maintainable event-driven solution that scales, but Booster has been built around these concepts and will greatly help you and your team to keep things under control. Booster integrates event-driven design in a way that simplifies their usage and understanding.

---

# Commands and Command Handlers - The Write Pipeline

> You can create a command manually or using the generator provided with the `boost` CLI tool. Let's create a command to confirm a payment:

```shell
boost new:command ConfirmPayment --fields cartID:UUID confirmationToken:string
```

> You can specify as many fields as you want, and Booster will generate a class for you in the `src/commands` folder that more or less will look like this:

```typescript
@Command({
  authorize: 'all',
})
export class ConfirmPayment {
  public constructor(readonly cartID: UUID, readonly confirmationToken: string) {}

  public handle(register: Register): void {
    // The `register` parameter injected can be used to register any number of events.
    register.events(new CartPaid(this.cartId, this.confirmationToken))
  }
}
```

Commands and Command Handlers define the **write** API of your application (highlighted in yellow in the diagram). Commands are objects that are sent to the `/commands` endpoint. The usage of this endpoint is explained [in the REST API section](#booster-cloud-framework-rest-api).

Similarly to controllers in a traditional [MVC](https://www.martinfowler.com/eaaCatalog/modelViewController.html) architecture, commands are synchronously dispatched by a _handler_ method, which will be in charge of validating the input and registering one or more [events](#events) in the system. While command handlers can run arbitrary code, it is recommended to keep them small, focusing on data acceptance and delegating as much logic to [event handlers](#events).

<aside class="notice">
Event registration is not mandatory, but we <b>strongly</b> recommend registering at least one event for any possible final state, even in the case of a failure, to make your application activity easier to trace and debug.
</aside>

A command is a class, decorated with the `@Command` decorator, that defines a data structure
and a `handle` method. The method will process the commands and optionally generate and persist
one or more events to the event store.

Note how no magic happened in the generator. The only thing that required for Booster to know that this class is a
command, is the `@Command` decorator. You could get the same result by writing the class yourself 😉

---

# Events

> To create an event class, you can do the same thing that you did with a command, either manually, or with the generator, using the `boost` command line tool:

```shell
boost new:event <name of the event> --fields fieldName:fieldType
```

> Booster will generate a class for you in the `src/events` folder:

```typescript
@Event
export class CartPaid {
  public constructor(readonly cartID: UUID, readonly confirmationToken: string) {}

  public entityID(): UUID {
    return this.cartId
  }
}
```

An event is a data structure that represents a **fact** and is the source of truth for your application. Instead of mutating your database, you store an event representing that mutation. Think of your bank account, instead of storing your balance in some database table, mutating the value every time you perform an operation, it stores events for each of them. The balance is then calculated on the fly and shown to you any time you request it. Two examples of events in your bank account would be:

- `WithdrawMoney`
- `DepositMoney`

You can define as many event handler classes as you want to react to them. For example, imagine that a specific event represents that your account has reached zero. You can write a handler to notify the user by email. In a Booster application, it is recommended to write most your domain logic in event handlers.

Notice the required `entityID` method. All events are grouped by their event type and the value returned by `entityID`. All events are somehow tied to a concept in your domain model, in our bank account example, this could be the account number.

In the previous example, the `CartPaid` event has a `cartID` field, which then you will return in the `entityID` method. This allows booster to find this event when the system requests to build the state of a specific Cart.

In most situations your event stream will be reduced to a domain model object, like that Cart (An [Entity](#entities)), but there are some use cases on which the event stream is just related to a specific entity, for example, a register of sensor values in a weather station, which are related to the station, but the station has no specific value that needs to be reduced. You can implement the semantics that best suit your needs.

## Event Handlers

```typescript
@EventHandler(CartPaid)
export class CartPaidHandler {
  public static handle(event: CartPaid, register: Register) {
    register.events(new OrderPreparationStarted(event.cartID))
  }
}
```

You can react to events implementing an **Event Handler** class. An Event Handler is a regular class that is subscribed to an event with the decorator `@EventHandler(<name of the event class>`. Any time that a new event is added to the event store, the `handle` method in the event handler will be called with the instance of the event and the `register` object that can be used to emit new events. Event handlers can run arbitrary code and is where it is recommended to write most of the business logic in a reactive way.

---

# Entities

> To create an entity... You guessed it! We use the `boost` tool:

```shell
boost new:entity <name of the entity> --fields fieldName:fieldType --reduces EventOne EventTwo EventThree
```

> For instance, running the following command:

```shell
boost new:entity Order --fields shippingAddress:Address orderItems:"Array<OrderItem>" --reduces OrderCreated
```

> It will generate a class in the `src/entities` folder with the following structure:

```typescript
@Entity
export class Order {
  public constructor(readonly id: UUID, readonly shippingAddress: Address, readonly orderItems: Array<OrderItem>) {}

  @Reduces(OrderCreated)
  public static createOrder(event: OrderCreated, previousOrder?: Order): Order {
    return event.order
  }
}
```

Entities are not shown in the diagram because they're just a different view of the data in the events store.

Entities represent domain model objects, that is, something that can be mapped to an object with semantics in your domain. Entities only exist conceptually, they're not explicitly stored in any database, but generated on the fly from a list of related [events](#events).

Booster creates snapshots of the entities automatically under the hoods to reduce access times, but the developer doesn't has to worry about that.

Examples of entities are:

- A Cart
- An Account
- A User

As you can see, entities are also regular TypeScript classes, like the rest of the Booster artifacts.

Take a look, entities have a special **reducer function** decorated with `@Reduces`,
that will be triggered each time that a specific kind of event is generated.

All projection functions receive:

- The event
- A possible previous state (note the `?` meaning that there could be no previous state. i.e. when the app is just starting)

And it **always** must return a new entity. This function **must be pure**, which means that it cannot perform any side effects, only create a new object based on some conditions on the input data, and then return it.

## Reading Entity "state"

```typescript
@Command({
  authorize: 'all',
})
export class MoveStock {
  public constructor(readonly productSKU: UUID, readonly fromLocationId: UUID, readonly toLocationId: UUID, readonly quantity: number) {}

  public handle(register: Register): void {
    const productStock = fetchEntitySnapshot('ProductStock', this.productSKU)

    if (productStock.locations[this.fromLocationId].count >= this.quantity) {
      // Enough stock, we confirm the movement
      register.events(new StockMovement(this.productSKU, this.fromLocationId, this.toLocationID, quantity))
    } else {
      // Not enough stock, we register this fact
      register.events(new FailedCommand({
        command: this,
        reason: `Not enough stock in origin location`
      ))
    }
  }
}
```

Booster provides a handy `fetchEntitySnapshot` method to check the value of an entity from any handler method in order to make domain-driven decisions.

---

# Read Models - The Read Pipeline

> TODO: (Not in the current release) To generate a Read Model you can use a read model generator. It works similarly than the entities generator:

```shell
boost new:read-model <name of the read model class> --fields fieldName:fieldType --projects EntityOne EntityTwo
```

> Using the generator will generate a class with the following structure in `src/read-models/<name-of-the-read-model>.ts`. For instance:

```shell
boost new:read-model CartReadModel --fields id:UUID cartItems:"Array<CartItem>" paid:boolean --projects Cart
```

> It will generate a class with the following structure:

```typescript
@ReadModel
export class CartReadModel {
  public constructor(readonly id: UUID, readonly cartItems: Array<CartItem>, public paid: boolean) {}

  @Projection(Cart, 'id')
  public static updateWithCart(cart: Cart, oldCartReadModel?: CartReadModel): CartReadModel {
    return new CartReadModel(cart.id, cart.cartItems, cart.paid)
  }
}
```

Read Models are cached data optimized for read operations and they're updated reactively when [Entities](#entities) are updated by new [events](#events). They also define the _Read_ API, the available REST endpoints and their structure.

Read Models are classes decorated with the `@ReadModel` decorator that have one or more projection methods. A Projection is a method decorated with the `@Projection` decorator that, given a new entity value and (optionally) a previous read model state, generate a new read model value.

Read models can be projected from multiple [entities](#entities) as soon as they share some common key called `joinKey`.

Read Model classes can also be created by hand and there are no restrictions regarding the place you put the files. The structure of the data is totally open and can be as complex as you can manage in your projection functions.

Defining a read models enables a new REST Read endpoint that you can use to query or poll the read model records [see the API documentation](#booster-cloud-framework-rest-api).

---

# Authentication and Authorization

> For example, the following command can be executed by anyone:

```typescript
@Command({
  authorize: 'all',
})
export class CreateComment {
  ...
}
```

> While this one can be executed by authenticated users that have the role `Admin` or `User`:

```typescript
@Command({
  authorize: [Admin, User],
})
export class UpdateUser {
  ...
}
```

Authorization in Booster is done through roles. Every Command (and in the future, every ReadModel)
has an `authorize` policy that tells Booster who can execute or access it. The policy is specified in the
`@Command` decorator and consists of one of the following two values:

- `'all'`: Meaning that the command is public: any user, both authenticated and anonymous, can execute it.
- An array of authorized roles `[Role1, Role2, ...]`: This means that only those authenticated users that
  have any of the roles listed there are authorized to execute the command

> This is an example of a definition of two roles:

```typescript
@Role({
  allowSelfSignUp: false,
})
export class Admin {}

@Role({
  allowSelfSignUp: true,
})
export class User {}
```

By default, a Booster application has no roles defined, so the only allowed value you can use in the `authorize` policy is `'all'` (good for public APIs).
If you want to add user authorization, you first need to create the roles that are suitable for your application.
Roles are classes annotated with the `@Role` decorator, where you can specify some attributes.

Here, we have defined the `Admin` and `User` roles. The former contains the following attribute `allowSelfSignUp: false`,
which means that when users sign-up, they can't specify the role `Admin` as one of its roles.
The latter has this attribute set to `true`, which means that any user can self-assign the role `User` when signing up.

If your Booster application has roles defined, an authentication API will be provisioned. It will allow your users to gain
access to your resources.

This API consists of three endpoints ([see the API documentation](#booster-cloud-framework-rest-api)):

- `/auth/sign-up`: Users can use this endpoint to register in your application and get some roles assigned to them.
  Only roles with the attribute `allowSelfSignUp: true` can be specified upon sign-up. After calling this endpoint, the
  registration is not yet finished. Users need to confirm their emails by clicking in the link that will be sent to their
  inbox.

<img class="image" src="./sign-up-verificaiton-email.png" alt="Confirmation email" />
<img class="image" src="./sign-up-confirmed.png" alt="Email confirmed" />

- `/auth/sign-in`: This endpoint creates a session for an already registered user, returning an access token that
  can be used to access role-protected resources (like Commands)
- `/auth/sign-out`: Users can call this endpoint to finish the session.

Once a user has an access token, it can be included in any request made to your Booster application as a
Bearer Authorization header (`Authorization: Bearer`). It will be used to get the user information and
authorize it to access protected resources.

---

# Deploying

One of the goals of Booster is to become provider agnostic so you can deploy your application to any serverless provider like AWS, Google Cloud, Azure, etc...

So far, in the current version, only AWS is supported, but given the high level of abstraction, it will eventually support
all cloud providers. (**Contributions are welcome!** 😜)

## Configure your provider credentials

> Creating a plain text file manually named `~/.aws/credentials` with the following content will be enough:

```text
[default]
aws_access_key_id = <YOUR KEY ID>
aws_secret_access_key = <YOUR ACCESS KEY>
region = eu-west-1
```

In the case of AWS, it is required that your `~/.aws/credentials` are properly setup, and a `region` attribute is specified. If you have the [AWS CLI installed](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html), you can create the config file by running the command `aws configure`, but that is completely optional, **AWS CLI is not required to run booster**.

<aside class="notice">
It's recomended to use IAM user keys and avoiding your root access keys. If you need help obtaining a `KEY ID` and `ACCESS KEY`, <a href=https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_CreateAccessKey">check out the oficial AWS guides</a>
</aside>

## Deploy your project

To deploy your Booster project, run the following command:

`boost deploy`

It will take a while, but you should have your project deployed to your cloud provider.

If you make changes to your code, you can run `boost deploy` again to update your project in the cloud.

## Deleting your cloud stack

If you want to delete the Booster application that has been deployed to the cloud, you can run:

`boost nuke`

<aside class="warning">
<b>Note</b>: This will delete everything in your stack, including databases. This action is <b>not</b> reversible!
</aside>

---

# Booster Cloud Framework REST API

The API for a Booster application is very simple and is fully defined by auth endpoints and the [commands](#commands-and-command-handlers-the-write-pipeline)
and [read models](#read-models-the-read-Pipeline) names and structures.

After a successful deployment you'll see an "Outputs:" section in your terminal with several values that you need to use
when doing requests to the API. Those values are:

- `baseURL`: This is the base URL for all your endpoints
- `clientID`: Needed for authentication/authorization endpoints. This is only shown if there are roles defined in your app.

Note that the `Content-Type` for all requests is `application/json`.

## Authentication and Authorization API

The following endpoints are provisioned if your application have at least one role defined. For more information about how
to use roles to restrict the access to your application, see the section [Authentication and Authorization](#authentication-and-authorization).

### Sign-up

Register a user in your application. After a successful invocation, an email will be sent to the user's inbox
with a confirmation link. **Users's won't be able to sign-in before they click in that link**.

###### Endpoint

```http request
POST https://<baseURL>/auth/sign-up
```

###### Request body

> Sign-up response body

```json
{
  "clientId": "string",
  "username": "string",
  "password": "string",
  "userAttributes": {
    "roles": ["string"]
  }
}
```

| Parameter        | Description                                                                                |
| ---------------- | ------------------------------------------------------------------------------------------ |
| _clientId_       | The application client Id that you got as an output when the application was deployed.     |
| _username_       | The username of the user you want to register. It **must be an email**.                    |
| _password_       | The password the user will use to later login into your application and get access tokens. |
| _userAttributes_ | Here you can specify the attributes of your user. These are:                               |

- _roles_ An array of roles this user will have. You can only specify here roles with the property `allowSelfSignUp = true`

###### Response

An empty body

###### Errors

> Sign-up error response body example: Not specifiying an email as username.

```json
{
  "__type": "InvalidParameterException",
  "message": "Username should be an email."
}
```

You will get a HTTP status code different from 2XX and a body with a message telling you the reason of the error.

### Sign-in

Allows your users to get tokens to be able to make request to restricted endpoints.
Remember that before a user can be signed in into your application, **its email must be confirmed**

###### Endpoint

```http request
POST https://<baseURL>/auth/sign-in
```

###### Request body

> Sign-in request body

```json
{
  "clientId": "string",
  "username": "string",
  "password": "string"
}
```

| Parameter  | Description                                                                            |
| ---------- | -------------------------------------------------------------------------------------- |
| _clientId_ | The application client Id that you got as an output when the application was deployed. |
| _username_ | The username of the user you want to sign in. It must be previously signed up          |
| _password_ | The password used to sign up the user.                                                 |

###### Response

> Sign-in response body

```json
{
  "accessToken": "string",
  "expiresIn": "string",
  "refreshToken": "string",
  "tokenType": "string"
}
```

| Parameter      | Description                                                                                                                         |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| _accessToken_  | The token you can use to access restricted resources. It must be sent in the `Authorization` header (prefixed with the `tokenType`) |
| _expiresIn_    | The period of time, in seconds, after which the token will expire                                                                   |
| _refreshToken_ | The token you can use to get a new access token after it has expired.                                                               |
| _tokenType_    | The type of token used. It is always `Bearer`                                                                                       |

###### Errors

> Sign-in error response body example: Login of an user that has not been confirmed

```json
{
  "__type": "UserNotConfirmedException",
  "message": "User is not confirmed."
}
```

You will get a HTTP status code different from 2XX and a body with a message telling you the reason of the error.

### Sign-out

Finalizes the user session by cancelling their tokens.

###### Endpoint

```http request
POST https://<baseURL>/auth/sign-out
```

###### Request body

> Sign-out request body

```json
{
  "accessToken": "string"
}
```

| Parameter     | Description                                   |
| ------------- | --------------------------------------------- |
| _accessToken_ | The access token you get in the sign-in call. |

###### Response

An empty body

###### Errors

> Sign-out error response body example: Invalid access token specified

```json
{
  "__type": "NotAuthorizedException",
  "message": "Invalid Access Token"
}
```

You will get a HTTP status code different from 2XX and a body with a message telling you the reason of the error.

## Write API (commands submission)

- [ ] TODO: Improve this documentation

`POST https://<baseURL>/commands`

#### Request body:

```json
{
  "typeName": "ChangeCartItem",
  "version": 1,
  "value": {
    "cartId": "demo",
    "sku": "ABC-10",
    "quantity": 1
  }
}
```

## Read API (retrieve a read model)

- [ ] Improve this documentation

### Get a list

`GET https://<baseURL>/readmodels/<read model class name>`

Example:

`GET https://<baseURL>/readmodels/CartReadModel`

### Get a specific read model

`GET https://<baseURL>/readmodels/<read model class name>/<read model ID>`

Example:

`GET https://<baseURL>/readmodels/CartReadModel/42`

---

# Frequently asked questions

**1.- When deploying my application in AWS for the first time, I got an error saying _"StagingBucket <your app name>-toolkit-bucket already exists"_**

When you deploy a Booster application to AWS, an S3 bucket needs to be created to upload the application code. Booster names that bucket
using your application name as a prefix.
In AWS, bucket names must be unique _globally_, so if there is another bucket in the world with exactly the same name as
the one generated for your application, you will get this error.

The solution is to change your application name in the configuration file so that the bucket name is unique.
