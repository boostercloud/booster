# ![Booster Framework](https://user-images.githubusercontent.com/175096/217907175-b81b3937-d773-45fd-85ca-716f9813432d.png)

[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.0-4baaaa.svg)](CODE_OF_CONDUCT.md)
[![Build Status](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Factions-badge.atrox.dev%2Fboostercloud%2Fbooster%2Fbadge%3Fref%3Dmain&style=flat)](https://actions-badge.atrox.dev/boostercloud/booster/goto?ref=main)
[![oclif](https://img.shields.io/badge/cli-oclif-brightgreen.svg)](https://oclif.io)
[![License](https://img.shields.io/npm/l/@boostercloud/cli)](https://github.com/boostercloud/booster/blob/main/package.json)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-yellow.svg)](https://conventionalcommits.org)
![Integration tests](https://github.com/boostercloud/booster/actions/workflows/integration-tests.yml/badge.svg)
[![Discord](https://img.shields.io/discord/763753198388510780.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bDY8MKx)
[![Docs](https://img.shields.io/badge/Docs-Booster-blue)](https://docs.boosterframework.com)
---

# What is Booster Framework?

[Booster Framework](https://boosterframework.com) is a software development framework designed to create event-driven backend microservices that focus on extreme development productivity. It provides a highly opinionated implementation of the CQRS and Event Sourcing patterns in Typescript, using [DDD (Domain-Driven Design)](https://en.wikipedia.org/wiki/Domain-driven_design) semantics that makes business logic fit naturally within the code. Thanks to Booster, business, product, and technical teams can collaborate, sharing a much closer language.

Booster uses advanced static analysis techniques and takes advantage of the Typescript type system to understand the structure and semantics of your code and minimize the amount of glue code. It’s capable not just of building an entirely functioning GraphQL API for you, but also to build an optimal, production-ready and scalable cloud infrastructure for your application in Azure or AWS.

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
* [Try the framework and let us know how you liked it!](https://docs.boosterframework.com/category/getting-started)

Please refer to [`CONTRIBUTING.md`](./CONTRIBUTING.md) for more details. Pull requests are welcome. For major changes, please
open an issue first to discuss what you would like to change.

# License

The Booster Framework is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for more details.

# Resources

* [Website](https://boosterframework.com)
* [Documentation](https://docs.boosterframework.com)
* [Step-by-step guides and examples](docs/examples)
* [Join the conversation in Discord](https://discord.gg/k7b4B8CDtT)
* [Twitter](https://twitter.com/boostthecloud)
* [Demos and more on Youtube](https://www.youtube.com/channel/UCpUTONI8OG19pr9A4cn35DA)
* [Rocket to the Cloud Podcast](https://www.youtube.com/channel/UCxUYk1SVyNRCGNV-9SYjEFQ)
* [Booster in Dev.to](https://dev.to/boostercloud)





