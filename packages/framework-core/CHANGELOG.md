# Change Log - @boostercloud/framework-core

This log was last generated on Tue, 11 Feb 2025 19:54:24 GMT and should not be manually modified.

## 3.0.0
Tue, 11 Feb 2025 19:54:24 GMT

### Breaking changes

- Node 20 upgrade 

## 2.19.0
Tue, 07 Jan 2025 15:05:43 GMT

### Minor changes

- Enhance storing events in batches in Azure provider

## 2.18.8
Tue, 17 Dec 2024 14:48:18 GMT

### Patches

- Update processProperties function to fix readModel getters where return type is an array of objects
- Added graphql union types to query return type generation

## 2.18.7
Mon, 09 Dec 2024 14:07:07 GMT

### Patches

- Replace child-process-promise with execa

## 2.18.6
Mon, 25 Nov 2024 15:49:50 GMT

### Patches

- Fixes optimistic concurrency issue found when projecting read models

## 2.18.5
Tue, 29 Oct 2024 18:40:31 GMT

### Patches

- Add mandatory subscriptionId property

## 2.18.4
Tue, 08 Oct 2024 15:06:52 GMT

### Patches

- Remove unnecessary @cdktf/provider-azurerm dependency from Azure provider package

## 2.18.3
Mon, 30 Sep 2024 19:09:52 GMT

### Patches

- Fix handling of deeply-nested arrays an sub-array properties in Read Model queries in local provider

## 2.18.2
Wed, 25 Sep 2024 18:23:11 GMT

### Patches

- Correct handling of deeply-nested arrays and sub-array properties in Read Model queries

## 2.18.1
Wed, 04 Sep 2024 18:51:46 GMT

### Patches

- Fix types for Projects decorator

## 2.18.0
Thu, 29 Aug 2024 21:39:42 GMT

### Minor changes

- Add remove events

## 2.17.0
Wed, 21 Aug 2024 00:15:44 GMT

### Minor changes

- Health sensors for Rockets

## 2.16.0
Tue, 06 Aug 2024 20:59:53 GMT

### Minor changes

- Improve GlobalErrorHandler

## 2.15.0
Wed, 31 Jul 2024 14:54:27 GMT

### Minor changes

- Handle non-found events with global error handler

## 2.14.0
Tue, 30 Jul 2024 18:43:55 GMT

### Minor changes

- Read Models projections by ReadModel query

## 2.13.1
Fri, 26 Jul 2024 09:36:18 GMT

### Patches

- Bump ws version (fixes CVE-2024-37890)

## 2.13.0
Fri, 19 Jul 2024 16:36:39 GMT

### Minor changes

- Improve support for calculated fields and their dependencies on read models

## 2.12.1
Thu, 13 Jun 2024 16:17:26 GMT

### Patches

- Add support for GraphQL fragments

## 2.12.0
Tue, 11 Jun 2024 16:43:14 GMT

### Minor changes

- Add global event handler

## 2.11.0
Thu, 30 May 2024 15:58:21 GMT

### Minor changes

- Allow define projection fields in read model queries

## 2.10.1
Tue, 16 Apr 2024 12:37:20 GMT

### Patches

- Pin effect library version and related dependencies

## 2.10.0
Fri, 12 Apr 2024 15:06:00 GMT

### Minor changes

- Store Azure events in batch

## 2.9.2
Thu, 04 Apr 2024 11:10:00 GMT

### Patches

- Fix peer dependency issue with @effect/printer

## 2.9.1
Tue, 26 Mar 2024 12:46:41 GMT

### Patches

- Bump express dependency version

## 2.9.0
Fri, 22 Mar 2024 17:48:10 GMT

### Minor changes

- replace nedb with seald

## 2.8.0
Wed, 20 Mar 2024 16:53:38 GMT

### Minor changes

- Track processed events in Azure provider to avoid duplication

## 2.7.1
Thu, 14 Mar 2024 15:18:33 GMT

### Patches

- Fix peer dependencies issues

## 2.7.0
Tue, 05 Mar 2024 13:04:14 GMT

### Minor changes

- Add injectable feature

## 2.6.0
Thu, 15 Feb 2024 12:26:19 GMT

### Minor changes

- Add Azure sku gateway and rus environment variables

### Updates

- changed CosmosDB throughput to use process env
- fixed documentation env example for cosmosdb throughput

## 2.5.1
Fri, 02 Feb 2024 13:14:34 GMT

### Patches

- Refactored the core exports and removed the `BoosterApp` to reduce code redund

## 2.5.0
Thu, 01 Feb 2024 12:48:38 GMT

### Minor changes

- refactor subnet creation for Azure Provider

## 2.4.0
Tue, 30 Jan 2024 10:48:42 GMT

### Minor changes

- Bump TypeScript to 5.1.6

## 2.3.0
Thu, 25 Jan 2024 18:03:39 GMT

### Minor changes

- Update Azure infrastructure to use Azure Gateway. Rockets now support multiple functions with specific host.json files

## 2.2.0
Fri, 17 Nov 2023 14:25:53 GMT

### Minor changes

- Add Azure Event Hub

## 2.1.0
Thu, 09 Nov 2023 12:10:04 GMT

### Minor changes

- Add health sensor

## 2.0.0
Wed, 01 Nov 2023 13:01:59 GMT

### Breaking changes

- Bump version to 2.0.0

### Minor changes

- Upgraded for Node18 support
- Replaced the deprecated dependency `ttypescript` with `ts-patch`

## 1.21.0
Mon, 30 Oct 2023 16:27:59 GMT

### Minor changes

- Add elapsed time to data migration entities

## 1.20.0
Tue, 10 Oct 2023 12:54:54 GMT

### Minor changes

- Fix event api for events and notifications not found

## 1.19.1
Tue, 03 Oct 2023 21:18:09 GMT

### Patches

- improve error log

## 1.19.0
Wed, 13 Sep 2023 14:53:03 GMT

### Minor changes

- Update booster dependencies

### Patches

- Fix local provider index

### Updates

- Fixed broken links to the documentation

## 1.18.1
Tue, 18 Jul 2023 10:04:58 GMT

### Patches

- fix getter on migrations

## 1.18.0
Fri, 30 Jun 2023 13:15:00 GMT

### Minor changes

- Fix GraphQL dependency

## 1.17.0
Thu, 29 Jun 2023 10:29:53 GMT

### Minor changes

- Add Local Provider concurrency

## 1.16.1
Mon, 26 Jun 2023 14:20:43 GMT

### Patches

- ensure aws storeSnapshot is idempotent

## 1.16.0
Thu, 22 Jun 2023 15:57:36 GMT

### Minor changes

- Add queryInfo and hooks to query decorator

## 1.15.0
Tue, 20 Jun 2023 10:05:41 GMT

### Minor changes

- Add NonExposed decorator to hide GraphQL fields

## 1.14.1
Fri, 16 Jun 2023 08:48:46 GMT

### Patches

- Add last update to read models

## 1.14.0
Wed, 07 Jun 2023 15:41:28 GMT

### Minor changes

- add tracer

## 1.13.0
Wed, 07 Jun 2023 14:31:25 GMT

### Minor changes

- Add touch entities

## 1.12.0
Wed, 31 May 2023 14:25:34 GMT

### Minor changes

- Disable subscriptions

## 1.11.1
Wed, 24 May 2023 14:42:54 GMT

### Patches

- Return ReadModel instance when querying by id
- Updated GraphQL to latest version

## 1.11.0
Fri, 05 May 2023 12:14:49 GMT

### Minor changes

- Add regex and iRegex filters

## 1.10.2
Thu, 04 May 2023 12:25:58 GMT

### Patches

- Create a host.json file and add it to the functionApp.zip file for Azure Provider only if there is not an existing one

## 1.10.1
Thu, 27 Apr 2023 18:25:07 GMT

### Patches

- Removed Kubernetes

## 1.10.0
Wed, 26 Apr 2023 15:55:21 GMT

### Minor changes

- Add Azure subscriptions

## 1.9.0
Thu, 20 Apr 2023 22:02:42 GMT

### Minor changes

- Local subscriptions

## 1.8.0
Thu, 20 Apr 2023 17:20:40 GMT

### Minor changes

- add query annotation

## 1.7.1
Wed, 19 Apr 2023 21:05:26 GMT

### Patches

- Add rush update to wf publish npm
- Support UUID contains and beginsWith filters

### Updates

- Added tests for partition key generation functions and removed the unused `partitionKeyForIndexByEntity` function from the Azure provider

## 1.7.0
Mon, 27 Feb 2023 23:02:14 GMT

### Minor changes

- Implented a lazy entity snapshot system that caches the entities when they're read instead of every time an event is registered in the event store. It also solves a bug that was missing events during reducers if a reducer trew an error.

## 1.6.2
Thu, 23 Feb 2023 18:55:04 GMT

### Patches

- Documented the register object and made the `flusher` property private, as it should not be accessed by end users.

## 1.6.1
Thu, 23 Feb 2023 13:15:35 GMT

### Patches

- Removed the experimental tag from Azure provider mentions in the documentation and CLI

## 1.6.0
Wed, 22 Feb 2023 12:12:00 GMT

### Minor changes

- Add Notification events

## 1.5.1
Sat, 18 Feb 2023 21:05:28 GMT

### Patches

- migrate readmodels on api search

## 1.5.0
Mon, 13 Feb 2023 13:56:07 GMT

### Minor changes

- rocket dispatcher request rocketFunctionId to the provider

## 1.4.1
Tue, 24 Jan 2023 12:31:04 GMT

### Patches

- Implemented a fallback to check the existence of a 'compile' command in the project package.json file for backwards compatibility

## 1.4.0
Mon, 16 Jan 2023 18:48:00 GMT

### Minor changes

- Add flush method parameter to Register

## 1.3.5
Thu, 22 Dec 2022 16:52:13 GMT

### Patches

- Make CLI error messages more readable

## 1.3.4
Mon, 19 Dec 2022 19:41:13 GMT

### Patches

- Changed the `ReadModelFilterHooks` and `ReadModelMetadata` types to be generic, allowing stricter typing.

## 1.3.3
Wed, 14 Dec 2022 15:59:09 GMT

### Patches

- Add optional parameters for host and port on local provider rockets

## 1.3.2
Tue, 29 Nov 2022 19:05:38 GMT

### Patches

- Add optional headers to Azure and Local API responses

## 1.3.1
Tue, 29 Nov 2022 09:58:54 GMT

### Patches

- Exported BoosterDataMigrationEntity

## 1.3.0
Fri, 18 Nov 2022 17:45:52 GMT

### Minor changes

- Support GraphQL Promises for getters

## 1.2.0
Fri, 18 Nov 2022 16:37:13 GMT

### Minor changes

- Migrate on ReadModel Find and Search

## 1.1.0
Thu, 17 Nov 2022 15:58:44 GMT

### Minor changes

- Fix BEM events processing

## 1.0.2
Fri, 11 Nov 2022 00:15:47 GMT

### Updates

- Added missing dependencies to @boostercloud/framework-provider-aws-infrastructure

