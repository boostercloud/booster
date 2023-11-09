# Change Log - @boostercloud/framework-core

This log was last generated on Thu, 09 Nov 2023 12:10:04 GMT and should not be manually modified.

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

