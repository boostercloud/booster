# Booster Documentation

## Introduction

> _Progress isn't made by early risers. It's made by lazy men trying to find easier ways to do something._ — [Robert A. Heinlein](https://en.wikipedia.org/wiki/Robert_A._Heinlein)

### What is Booster?

Booster is a new kind of framework to build scalable and reliable event-driven systems faster, maximizing developer speed by reducing friction at many levels.

We have redesigned the whole developer experience from scratch, taking advantage of the advanced TypeScript type system and Serverless technologies to go from project generation to a production-ready real-time GraphQL API that can ingest thousands of concurrent users in a matter of minutes.

Booster's ultimate goal is fulfilling the developer's dream of writing code at the application layer, and don't care about how anything else is done except it just works!

### Booster Principles

Booster takes a holistic and highly-opinionated approach at many levels:

* **Focus on business value**: The only code that makes sense is the code that makes your application different from any other.
* **Convention over configuration**: All the supporting code and configuration that is similar in all applications should be out of programmers' sight.
* **Serverless-less**: Why going Serverless to avoid managing infrastructure when you can implicitly infer your Serverless architecture from your code and not even deal with that?
* **Scale smoothly**: A modern project shouldn't need to change their software architecture or rewrite their code in a different language just because they succeed and get a lot of users.
* **Event-source and CQRS**: Our world is event-driven, businesses are event-driven, and modern software must map reality better being event-driven. We also have enough MVC frameworks already!
* **Principle of Abstraction**: Building an application is hard enough to have to deal with recurrent low-level details like SQL, API design, or authentication mechanisms, so we tend to build more semantic abstractions on top of them.
* **Real-time first**: Client applications must be able to react to events happening in the backend and notice data changes.

### Why using Booster

Booster will fit like a glove in applications that are naturally event-driven like:

* Commerce applications (retail, e-commerce, omnichannel applications, warehouse management, etc.)
* Business management applications
* Communication systems

But it's a general-purpose framework that has several advantages over other solutions:

* **Faster time-to-market**: Booster can deploy your application to a production-ready environment from minute one, without complicated configurations or needing to invest any effort to design it. In addition to that, it features a set of code generators to help developers build the project scaffolding faster and focus on actual business code in a matter of seconds instead of dealing with complicated framework folklore.
* **Write less code**: Booster conventions and abstractions require less code to implement the same features. This not just speeds up development but combined with the clear architecture guidelines, it also makes Booster projects easier to understand, iterate, and maintain.
* **All the advantages of Microservices, none of its cons**: Microservices are a great way to deal with code complexity, at least on paper. Services are isolated and can scale independently, and different teams can work independently, but that usually comes with a con: interfaces between services introduce huge challenges like delays, hard to solve cyclic dependencies, or deployment errors. In Booster, every handler function works as an independent microservice, it scales separately in its own lambda function, and there are no direct dependencies between them, all communication happens asynchronously via events, and all the infrastructure is compiled, type-checked and deployed atomically to avoid issues.
* **All advantages of Serverless, without earning a degree in cloud technologies** Serverless technologies are amazing and have made a project like Booster possible, but they're relatively new technologies, and while day after day new tools appear to make them easier, the learning curve is still quite steep. With Booster you'll take advantage of Serverless main selling points of high scalability and reduced hosting costs, without having to learn every detail from minute one.
* **Event-sourcing by default**: Similarly to Git repositories, Booster keeps all data changes as events indefinitely. This means that any previous state of the system can be recreated and replayed at any moment. This enables a whole world of possibilities for troubleshooting and auditing your system, or syncing development or staging environments with the production data to perform tests and simulations.
* **Booster makes it easy to build enterprise-grade applications** Implementing an event-sourcing system from scratch is a challenging exercise that usually requires highly specialized experts. There are some technical challenges like eventual consistency, message ordering, and snapshot building. Booster takes care of all of that and more for you, lowering the curve for people that are starting and making expert lives easier.

## Getting started

### Installing Booster

You can develop with Booster using any of the following operating systems:

- Linux
- MacOS
- Windows (Native and WSL)

Booster hasn't been tested under other platforms like BSD, by using them you may face unknown issues so proceed at your own risk.

#### Prerequisites

##### Install Node.js

The minimal required Node.js version is `v12`. Download the installer [from it's website](https://nodejs.org/en/), or install it using the system's package manager.

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

As soon as you have a Node.js version higher than `v12`, and an `npm` version higher than `6`, you are good to go. Just note that `npm` comes with node, you don't have to install it apart.

Alternatively, we recommend you to use a version manager for dealing with different Node.js versions

- [`nvm`](https://github.com/nvm-sh/nvm) - Works with MacOS, Linux and WSL
- [`nvm-windows`](https://github.com/coreybutler/nvm-windows) - Works with native Windows

##### Set up an AWS account

This step is optional, Booster is a cloud-native framework, meaning that your application
will be deployed to the cloud using different cloud providers. By default, Booster uses the
[AWS Provider](framework-providers-aws) so an AWS account is needed. You can always omit
this step if you only want to get a grip of Booster or test it locally without making a
deployment.

Note:

> Booster is, and will always be, free but the resources used by the cloud providers are
> not. All the resources used by the AWS Provider are part of the
> [AWS free tier](https://aws.amazon.com/free). Even if you are not eligible for it,
> you can still test your app and it shouldn't cost more than a few cents. Still,
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
