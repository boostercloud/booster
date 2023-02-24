# Change Log - @boostercloud/framework-core

This log was last generated on Thu, 23 Feb 2023 18:55:04 GMT and should not be manually modified.

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

