# ![Booster logo](docs/img/booster-logo.png)

[![Build Status](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Factions-badge.atrox.dev%2Fboostercloud%2Fbooster%2Fbadge&style=flat)](https://actions-badge.atrox.dev/boostercloud/booster/goto)
[![oclif](https://img.shields.io/badge/cli-oclif-brightgreen.svg)](https://oclif.io)
[![License](https://img.shields.io/npm/l/cli.svg)](https://github.com/boostercloud/booster/blob/master/package.json)

---

<!-- TOC -->

- [Disclaimer](#disclaimer)
- [Overview](#overview)
- [Getting Started](#getting-started)
- [Examples](#examples)
- [Architecture and core concepts](#architecture-and-core-concepts)
  - [Commands](#commands)
  - [Events](#events)
  - [Entities](#entities)
- [Features](#features)
  - [Authentication](#authentication)
- [Deploying](#deploying)
  - [Configure your provider credentials](#configure-your-provider-credentials)
  - [Deploy your project](#deploy-your-project)
  - [Deleting your cloud stack](#deleting-your-cloud-stack)
- [Contributing](#contributing)

<!-- /TOC -->

## Disclaimer

Current published versions of Booster are in a very alpha state. This means that a great chunk of the most
basic functionality that you’d expect in a production-ready framework like data migration management,
upgrades, data search and others are completely missing or in a very buggy state. It’s also very likely that
non-backward-compatible changes will be introduced on every release until the project reaches a stable version.
Hard work is being put towards this first production-ready release, if you want to contribute,
refer to the [CONTRIBUTING guide](./CONTRIBUTING.md).

## Overview

Booster is a high-level framework for _TypeScript_ to build _Serverless_ applications with built-in business-logic-level abstractions.

Serverless architectures have great advantages (especially regarding scalability) useful for any production-grade application, but it also
has some important pain-points (mainly related to complexity).

If a genie gave us three Serverless wishes, we would probably ask for:

- Smoother learning curves beyond the getting-started guide.
- Easy-to-use conventions and standards for common recurrent problems.
- Higher-level abstractions that make it easier to represent business rules in the cloud.

Booster provides a set of highly opinionated conventions and sensible defaults to build and provision the
cloud architecture needed to run your projects without asking you to write any configuration at all.
In fact, the configuration is inferred _automatically_ from the code you write.

## Getting Started

If you are not familiar with how a Booster application is structured and what are its main components, we encourage
you to first read the section ["Architecture and core concepts"](#architecture-and-core-concepts).
If you are ready to start _boosting_ your application development flow, follow these steps:

1. Install the Booster CLI tool if you haven’t yet:

   ```shell script
   npm install -g @boostercloud/cli
   ```

2. Create a new Booster project

   ```shell script
   boost new:project <name of your project>
   ```

3. If you now get into the project (`cd <name of your project>`) you'll see the folder structure where
   you will place your commands, events, etc. Although Booster does not force you to follow a specific folder structure,
   we encourage you to use the auto-generated one (at least until you feel comfortable with any Booster app).
4. Now you can start creating your components or use the `boost` generators to get the basic scaffolding of your project
   in the blink of an eye.

## Examples

For a step-by-step guide of how to create an application that uses the main concepts of Booster, please go to ["Creating a cart service from scratch"](docs/create-cart-service-demo.md).
If you want to see the code of a more elaborated example application, check [`packages/booster-example`](packages/framework-example).

## Architecture and core concepts

Booster’s architecture is heavily inspired by the [CQRS](https://www.martinfowler.com/bliki/CQRS.html) and [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) patterns. These patterns
have proven to work well for highly-distributed high available systems, being a tool to make resilient
software that is fast and scales very well, especially in distributed scenarios.

Essentially, these patterns combined, make your app:

- Separate the write operations from the read operations (called _commands_ and _queries_, respectively) in order to balance the load on the endpoints.
- Instead of storing and mutating the data in the database, store an infinite list of events (think of your bank account, where all the movements are stored as individual events).
- The source of truth is not a single database that holds the current state, but a virtually infinite list of events. The current state is cached in a shape that's optimized for reading in what we call **read models**.

If you don’t know these patterns well yet, don’t fret, as Booster integrates them in a way that
simplifies their usage and understanding.

The Booster high-level architecture diagram looks like this:

![Booster architecture](docs/img/booster-arch.png)

### Commands

Commands define the **write** API for your application (yellow color on the diagram). They are objects that are sent to the `/commands` endpoint.

Instead of a controller, like in a traditional architecture like [MVC](https://www.martinfowler.com/eaaCatalog/modelViewController.html), you define a _Handler_, which will be in charge of processing the command, calling any third-party services, performing side-effects, and finally, registering [events](#events).

> **Note:** Event registration is not mandatory, although we **strongly** recommend that each command registers an event for any possible final state, like success or failure, making your application much easier to debug.

A command is a class, decorated with the `@Command` decorator, that defines a data structure
and a handler method. The method will process the commands and optionally generate and persist
one or more events to the event store.

To create a command, you can do so manually, or by running the generator provided by the `boost` CLI tool. Let's create a command that will confirm a payment

```shell script
boost new:command ConfirmPayment --fields cartID:UUID confirmationToken:string
```

You can specify as many fields as you want, and Booster will generate a class for you in the `src/commands` folder that more or less will look like this:

```typescript
@Command({
  authorize: 'all',
})
export class ConfirmPayment {
  public constructor(readonly cartID: UUID, readonly confirmationToken: string) {}

  public handle(register: Register): void {
    // implementation for the handler
  }
}
```

The `handle` method is the Handler we were talking about some paragraphs ago. Here you will write your integration with 3rd party services, data validation, and, basically, all your side effects for your application.

Again, we strongly advise that after a command has been executed, even if it didn’t succeed, you `register` an event
specifying what happened. This also goes in the `handle` method, and that’s what the `register` parameter is used for:

```typescript
public handle(register: Register): void {
  // i.e. code that performs a payment
  register.events(new CartPaid(this.cartId, this.confirmationToken))
}
```

Note how no magic happened here yet. The only thing that is needed for Booster to know that this class is a
command, is the `@Command` decorator. Apart from that, the generator only writes code, nothing else!
You could achieve the same result by writing it yourself 😉

### Events

An event is a data structure that represents a **fact** and is the source of truth for your application. Instead of mutating your database, you store an event representing that mutation. Think of your bank account, instead of mutating the value that you have in it, it stores events representing those operations. Two examples of events in your bank account would be:

- `WithdrawMoney`
- `DepositMoney`

_(Event handlers are not implemented yet)._ Events can have handlers attached that allow the system to react to them. Imagine that a specific event represents that your account has reached zero. You can make the handler fire a [command](#commands) to notify the user by email.

To create an event, you can do the same thing that you did with a command, either manually,
or with the `boost` command line tool:

```shell script
boost new:event <name of the event> --fields fieldName:fieldType
```

Booster will generate a class for you in the `src/events` folder:

```typescript
@Event
export class CartPaid {
  public constructor(readonly cartID: UUID, readonly confirmationToken: string) {}

  public entityID(): UUID {
    return this.cartId
  }
}
```

Note the `entityID` method! You have to implement it by returning the ID of the entity that this event is associated to.

For example, this `CartPaid` event should have a `cartID` field, which then you will return in the `entityID` method.
Note that this method **has to be pure**, this means that it should not do any side effects, only return the entity ID, and nothing else.

### Entities

Entities have semantics in the domain model, something that makes sense on its own in your application. They only exist conceptually, because they’re just data generated from a list of events. Entities are automatically snapshotted by the system in the Read Model database so clients can subscribe or query their data at any moment.

Examples of entities would be:

- Cart
- Account
- User

Booster provides a handy `fetchEntitySnapshot` methods to check the value of an entity from any handler method in order to make domain-driven decisions.

To create an entity... You guessed it! We use the `boost` tool:

```shell script
boost new:entity <name of the entity> --fields fieldName:fieldType --reactsTo EventOne EventTwo EventThree
```

Booster will generate a class for you in the `src/entities` folder:

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

As you can see, entities are also regular TypeScript classes, like the rest of the Booster concepts.

Take a look, entities have a special **reducer function** decorated with `@Reduces`,
that will be triggered each time that a specific kind of event is generated.

All projection functions receive:

- The event
- A possible previous state (note the `?` meaning that there could be no previous state. i.e. when the app is just starting)

And it **always** must return a new entity. This function **must be pure**, which means that it cannot perform any side effects. Only create a new object based on some conditions, and return it.

## Features

In addition to all above, Booster includes other features that are important in any application.

### Authentication

Authorization in Booster is done through roles. Every Command (and in the future, every ReadModel)
has an `authorize` policy that tells Booster who can execute it. The policy is specified in the
`@Command` decorator and consists of one of the following two values:

- `'all'`: Meaning that the command is public: any user, both authenticated and anonymous, can execute it.
- An array of authorized roles `[Role1, Role2, ...]`: This means that only those authenticated users that
  have any of the roles listed there are authorized to execute the command

For example, the following command can be executed by anyone:

```typescript
@Command({
  authorize: 'all',
})
export class CreateComment {
  ...
}
```

While this one can be executed by authenticated users that have the role `Admin` or `User`:

```typescript
@Command({
  authorize: [Admin, User],
})
export class UpdateUser {
  ...
}
```

By default, a Booster application has no roles defined, so the only allowed value you can use in the `authorize` policy is `'all'` (good for public APIs).
If you want to add user authorization, you first need to create the roles that are suitable for your application.
Roles are classes annotated with the `@Role` decorator, where you can specify some attributes.

This is an example of a definition of two roles:

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

Here, we have defined the `Admin` and `User` roles. The former contains the following attribute `allowSelfSignUp: false`,
which means that when users sign-up, they can't specify the role `Admin` as one of its roles.
The latter has this attribute set to `true`, which means that any user can self-assign the role `User` when signing up.

If your Booster application has roles defined, an authentication API will be provisioned. It will allow your users to gain
access to your resources.

This API consists of three endpoints:

- `/auth/sign-up`: Users can use this endpoint to register in your application and get some roles assigned to them.
  Only roles with the attribute `allowSelfSignUp: true` can be specified upon sign-up.
- `/auth/sign-in`: This endpoint creates a session for an already registered user, returning an access token that
  can be used to access role-protected resources (like Commands)
- `/auth/sign-out`: Users can call this endpoint to finish the session.

Once a user has an access token, it can be included in any request made to your Booster application as a
Bearer Authorization header (`Authorization: Bearer`). It will be used to get the user information and
authorize it to access protected resources.

## Deploying

One of the goals of Booster is to become provider agnostic so you can deploy your application to any serverless provider like AWS, Google Cloud, Azure, etc...

So far, in the current version, only AWS is supported, but given the high level of abstraction, it will eventually support
all cloud providers. (**Contributions are welcome!** 😜)

### Configure your provider credentials

Booster uses your cloud provider's SDK. Make sure it is properly configured.

In the case of AWS, it is required that your `~/.aws/credentials` are properly setup, and a `region` attribute is specified. To do that you could [install the AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html) and run `aws configure`, but creating a `~/.aws/credentials` file with your AWS credentials should be enough:

```shell script
[default]
aws_access_key_id = <YOUR KEY ID>
aws_secret_access_key = <YOUR ACCESS KEY>
region = eu-west-1
```

It's recomended to use IAM user keys and avoiding your root access keys. If you need help obtaining a `KEY ID` and `ACCESS KEY`, [check out the oficial AWS guides](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_CreateAccessKey).

### Deploy your project

To deploy your Booster project, run the following command:

```shell script
boost deploy
```

It will take a while, but you should have your project deployed to your cloud provider.

If you make changes to your code, you can run `boost deploy` again to update your project in the cloud.

### Deleting your cloud stack

If you want to delete the Booster application that has been deployed to the cloud, you can run:

```shell script
boost nuke
```

> **Note**: This will delete everything in your stack, including databases. This action is **not** reversible!

## Contributing

Please refer to [`CONTRIBUTING.md`](CONTRIBUTING.md). Pull requests are welcome. For major changes, please
open an issue first to discuss what you would like to change.
