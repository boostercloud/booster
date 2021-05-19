# ![Booster logo](docs/img/booster-logo.png)

[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.0-4baaaa.svg)](CODE_OF_CONDUCT.md)
[![Build Status](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Factions-badge.atrox.dev%2Fboostercloud%2Fbooster%2Fbadge&style=flat)](https://actions-badge.atrox.dev/boostercloud/booster/goto)
[![oclif](https://img.shields.io/badge/cli-oclif-brightgreen.svg)](https://oclif.io)
[![License](https://img.shields.io/npm/l/@boostercloud/cli)](https://github.com/boostercloud/booster/blob/main/package.json)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-yellow.svg)](https://conventionalcommits.org)
![Integration tests](https://github.com/boostercloud/booster/workflows/Integration%20tests/badge.svg)
[![Discord](https://img.shields.io/discord/763753198388510780.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bDY8MKx)
[![Docs](https://img.shields.io/badge/Docs-Booster-blue)](https://docs.booster.cloud)
---

# What is Booster Framework?

The Booster Framework is a true serverless framework that provides a highly opinionated implementation of the CQRS and Event Sourcing patterns in Typescript. Using these patterns, you naturally write highly semantic, simple, and strongly typed code that puts into practice the ideas of Domain-Driven Design.

Booster extracts metadata from your code structure at deployment time, combining it with years worth of knowledge and experience from our team to build an optimal cloud environment to run your application at scale.
‍
Combining these features, Booster provides an unprecedented developer experience. On the one hand, it helps you write simpler code, defining your application in terms of commands, events, entities, and read models. On the other hand, you don't have to worry about the tremendous amount of low-level configuration details of conventional tools. You write highly semantic code, and if it compiles, it will run on the cloud.

Booster is designed with extensibility in mind. If there's still something you can't do with the default functionality, there are lower-level APIs that you can use to extend any layer of the framework with Rockets (The way we call Booster's plugins). With rockets, it's easy to add support for new cloud building blocks or pack functionality in highly reusable npm packages.

This is only the beginning. We want to keep expanding the framework to become a tool usable from any programming language, add support for more runtime targets and implement other common high-level patterns like MVC. If you want to help us make this a reality, don't hesitate to ping us on [Discord](https://discord.gg/bDY8MKx)!

## Current state

We've built the foundations, and there's a fully working TypeScript implementation that models CQRS and Event-Sourcing on top of AWS Serverless. The AWS implementation is end-to-end tested, including unit tests, integration tests, and automated load tests before every new version release. It implements advanced techniques like optimistic concurrency or sharding databases out of the box and by default, so you don't have to worry about your data consistency. It feels like magic!

We also have experimental support for Azure and Kubernetes runtimes, which are already usable, but still, need some work to reach the same level of robustness and scalability of the AWS implementation.

We know that this project is extremely ambitious. Still, we're convinced that this project could become the next level of abstraction in software history. That's why we made it open-source. Because we believe that the only way to make something like this succeed is by the collaboration and support of diverse groups of developers with varying goals. Now and then, there has to be a jump in software evolution, and we won't make it if we're not bold!

## The "Booster Way"

Booster Framework follows the next principles:

* *Play nicely*: Booster is not here to replace your toolkit but to expand it. Booster's goal is to come along well together with your existing auth, queues, databases, and services, providing a modern and swift tool to build new functionality that really squeezes the cloud.
* *DDD:* Software should be designed around business-level concepts to ease the team's communication. All code in Booster is defined in terms of Commands, Events, Handlers, and Entities, limiting the need for artificial developers-only constructs.
* *CQRS and Event-Sourcing:* Booster is designed around the concepts of CQRS and Event-Sourcing. This design has many advantages regarding scalability and data management. It even allows you to travel back in time!
* *The cloud is the machine:* We believe that the developer's tools should create infrastructure transparently in the same way that a compiler hides the details of the target processor. We often think about Booster as the "TypeScript-to-Cloud compiler."
* *True Serverless*: Serverless is about to stop caring about your servers, but many implementations still require long YAML files to describe your infrastructure, and you really need to know what you're doing. True Serverless means that you don't even care about cloud configuration. Booster will figure it out for you based on the code you write.
* *Convention over Configuration:* We prefer to provide standardized highly-opinionated modules than highly-configurable ones. This helps us to keep your code small and follow the best security and structure practices when deploying your applications to the cloud. High consistency in your project and code structure also helps to abstract out most of the boilerplate.
* *Don't Repeat Yourself (Extreme edition):* /The only code that matters is the one that makes your application different/. We push TypeScript structure and type system to the limit to avoid writing repetitive code, like object-to-JSON serializations, API or database schemas, or redundant architecture layers. 
* *Self-documenting APIs* We adopted GraphQL because it's a self-documenting standard. You can grab a standard GraphQL client like [ApolloClient](https://github.com/apollographql/apollo-client) and start using a Booster backend in a minute.
* *Developer's productivity:* Software development is fun, and a modern tool should make it even more fun, reducing the effort needed for mundane tasks. Booster provides code generators to help you quickstart new projects and objects, and the framework types and APIs are hand-crafted to help your IDE help you.


## Contributing

We've built the foundations and demonstrated that this new model of programming works. Now is the time to grow, and early contributors will become the masters of this new universe that we're opening. 

Now it's time for you to join a diverse and welcoming community of crazy developers trying to redefine the future. There's no small contribution, and all ideas are welcome, and we can't guarantee any success, but we believe the journey will be worth it no matter what. Bold adventurers will be recognized as such.

You can join the conversation and start contributing in any of the following ways:
* [Say hello in Discord](https://discord.gg/bDY8MKx)
* [Create a new issue in Github](https://github.com/boostercloud/booster/issues/new/choose)
* [Try the framework and let us know how you liked it!](https://docs.booster.cloud/#/chapters/02_getting-started)

Please refer to [`CONTRIBUTING.md`](./CONTRIBUTING.md) for more details. Pull requests are welcome. For major changes, please
open an issue first to discuss what you would like to change.

## License

The Booster Cloud Framework is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for more details.

## Resources

* [Documentation](https://docs.booster.cloud)
* [Step-by-step guides and examples](docs/examples)
* [Join the conversation in Discord](https://discord.gg/k7b4B8CDtT)
* [Twitter](https://twitter.com/boostthecloud)
* [Demos and more in Youtube](https://www.youtube.com/channel/UCpUTONI8OG19pr9A4cn35DA)
* [Rocket to the Cloud Podcast](https://www.youtube.com/channel/UCxUYk1SVyNRCGNV-9SYjEFQ)
* [Booster in Dev.to](https://dev.to/boostercloud)

## Disclaimer

Booster is still under heavy development, and non-backward-compatible changes might be introduced until we reach v1.0.0.

Refer to [Release Notes](https://github.com/boostercloud/booster/releases) for more specific information about changes of each iteration.
