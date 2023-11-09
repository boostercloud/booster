---
description: Learn how to get Booster health information
---

## Health

The Health functionality allows users to easily monitor the health status of their applications. With this functionality, users can make GET requests to a specific endpoint and retrieve detailed information about the health and status of their application components.

## Supported Providers
- Azure Provider
- Local Provider

### Enabling Health Functionality

To enable the Health functionality in your Booster application, follow these steps:

1. Install or update to the latest version of the Booster framework, ensuring compatibility with the Health functionality. 
2. Enable the Booster Health endpoints in your application's configuration file. Example configuration in config.ts:

```typescript
Booster.configure('local', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.providerPackage = '@boostercloud/framework-provider-local'
  Object.values(config.sensorConfiguration.health.booster).forEach((indicator) => {
    indicator.enabled = true
  })
})
```

Or enable only the components you want:
```typescript
Booster.configure('local', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.providerPackage = '@boostercloud/framework-provider-local'
  const sensors = config.sensorConfiguration.health.booster
  sensors[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].enabled = true
})
```


3. Optionally, implement health checks for your application components. Each component should provide a health method that performs the appropriate checks and returns a response indicating the health status. Example:

```typescript
import {
  BoosterConfig,
  HealthIndicatorResult,
  HealthIndicatorMetadata,
  HealthStatus,
} from '@boostercloud/framework-types'
import { HealthSensor } from '@boostercloud/framework-core'

@HealthSensor({
  id: 'application',
  name: 'my-application',
  enabled: true,
  details: true,
  showChildren: true,
})
export class ApplicationHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorResult> {
    return {
      status: HealthStatus.UP,
    } as HealthIndicatorResult
  }
}
```
4. A health check typically involves verifying the connectivity and status of the component, running any necessary tests, and returning an appropriate status code.
5. Start or restart your Booster application. The Health functionality will be available at the https://your-application-url/sensor/health/ endpoint URL.


### Health Endpoint

The Health functionality provides a dedicated endpoint where users can make GET requests to retrieve the health status of their application. The endpoint URL is: https://your-application-url/sensor/health/

This endpoint will return all the enabled Booster and application components health status. To get specific component health status, add the component status to the url. For example, to get the events status use: https://your-application-url/sensor/health/booster/database/events   

#### Available endpoints

Booster provides the following endpoints to retrieve the enabled components:

* https://your-application-url/sensor/health/: All the components status
* https://your-application-url/sensor/health/booster: Booster status 
* https://your-application-url/sensor/health/booster/database: Database status
* https://your-application-url/sensor/health/booster/database/events: Events status
* https://your-application-url/sensor/health/booster/database/readmodels: ReadModels status
* https://your-application-url/sensor/health/booster/function: Functions status
* https://your-application-url/sensor/health/your-component-id: User defined status
* https://your-application-url/sensor/health/your-component-id/your-component-child-id: User child component status 

Depending on the `showChildren` configuration, children components will be included or not.

### Health Status Response

Each component response will contain the following information:

* status: The component or subsystem status
* name: component description
* id: string. unique component identifier. You can request a component status using the id in the url
* details: optional object. If `details` is true, specific details about this component.
* components: optional object. If `showChildren` is true, children components health status.  

Example: 

```json
[
  {
    "status": "UP",
    "details": {
      "urls": [
        "dbs/my-store-app"
      ]
    },
    "name": "Booster Database",
    "id": "booster/database",
    "components": [
      {
        "status": "UP",
        "details": {
          "url": "dbs/my-store-app/colls/my-store-app-events-store",
          "count": 6
        },
        "name": "Booster Database Events",
        "id": "booster/database/events"
      },
      {
        "status": "UP",
        "details": [
          {
            "url": "dbs/my-store-app/colls/my-store-app-ProductReadModel",
            "count": 1
          }
        ],
        "name": "Booster Database ReadModels",
        "id": "booster/database/readmodels"
      }
    ]
  }
]
```

### Get specific component health information

Use the `id` field to get specific component health information. Booster provides the following ids:

* booster
* booster/function
* booster/database
* booster/database/events
* booster/database/readmodels

You can provide new components:
```typescript
@HealthSensor({
  id: 'application',
})
```

```typescript
@HealthSensor({
  id: 'application/child',
})
```

Add your own components to Booster:

```typescript
@HealthSensor({
  id: `${BOOSTER_HEALTH_INDICATORS_IDS.DATABASE}/extra`,
})
```


Or override Booster existing components with your own implementation:

```typescript
@HealthSensor({
  id: BOOSTER_HEALTH_INDICATORS_IDS.DATABASE,
})
```


### Health configuration

Health components are fully configurable, allowing you to display the information you want at any moment.

Configuration options:
* enabled: If false, this indicator and the components of this indicator will be skipped
* details: If false, the indicator will not include the details
* showChildren: If false, this indicator will not include children components in the tree.
  * Children components will be shown through children urls
* authorize: Authorize configuration. [See security documentation](https://docs.boosterframework.com/security/security) 

#### Booster components default configuration

Booster sets the following default configuration for its own components:

* enabled: false
* details: true
* showChildren: true

Change this configuration using the `config.sensorConfiguration` object. This object provides:

* config.sensorConfiguration.health.globalAuthorizer: Allow to define authorization configuration
* config.sensorConfiguration.health.booster: Allow to override default Booster components configuration
  * config.sensorConfiguration.health.booster[BOOSTER_COMPONENT_ID].enabled
  * config.sensorConfiguration.health.booster[BOOSTER_COMPONENT_ID].details
  * config.sensorConfiguration.health.booster[BOOSTER_COMPONENT_ID].showChildren


#### User components configuration

Use `@HealthSensor` parameters to configure user components. Example:

```typescript
@HealthSensor({
  id: 'user',
  name: 'my-application',
  enabled: true,
  details: true,
  showChildren: true,
})
```

### Create your own health endpoint

Create your own health endpoint with a class annotated with `@HealthSensor` decorator. This class 
should define a `health` method that returns a **HealthIndicatorResult**. Example:

```typescript
import {
  BoosterConfig,
  HealthIndicatorResult,
  HealthIndicatorMetadata,
  HealthStatus,
} from '@boostercloud/framework-types'
import { HealthSensor } from '@boostercloud/framework-core'

@HealthSensor({
  id: 'application',
  name: 'my-application',
  enabled: true,
  details: true,
  showChildren: true,
})
export class ApplicationHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorResult> {
    return {
      status: HealthStatus.UP,
    } as HealthIndicatorResult
  }
}
```

### Booster health endpoints

#### booster
* status: UP if and only if graphql function is UP and events are UP
* details: 
  * boosterVersion: Booster version number

#### booster/function
* status: UP if and only if graphql function is UP
* details:
  * graphQL_url: GraphQL function url
  * cpus: Information about each logical CPU core.
    * cpu:
      * model: Cpu model. Example: AMD EPYC 7763 64-Core Processor
      * speed: cpu speed in MHz
      * times: The number of milliseconds the CPU/core spent in (see iostat)
        * user:  CPU utilization that occurred while executing at the user level (application)
        * nice: CPU utilization that occurred while executing at the user level with nice priority.
        * sys: CPU utilization that occurred while executing at the system level (kernel).
        * idle: CPU or CPUs were idle and the system did not have an outstanding disk I/O request.
        * irq: CPU load system
    * timesPercentages: For each times value, the percentage over the total times
  * memory:
    * totalBytes: the total amount of system memory in bytes as an integer.
    * freeBytes: the amount of free system memory in bytes as an integer.

#### booster/database

* status: UP if and only if events are UP and Read Models are UP
* details:
  * urls: Database urls


#### booster/database/events

* status: UP if and only if events are UP
* details:
  * **AZURE PROVIDER**:
    * url: Events url
    * count: number of rows
  * **LOCAL PROVIDER**:
    * file: event database file
    * count: number of rows


#### booster/database/readmodels

* status: UP if and only if Read Models are UP
* details:
  * **AZURE PROVIDER**:
    * For each Read Model:
      * url: Event url
      * count: number of rows
  * **LOCAL PROVIDER**:
    * file: Read Models database file
    * count: number of total rows

> **Note**: details will be included only if `details` is enabled


### Health status

Available status are

* UP: The component or subsystem is working as expected
* DOWN: The component is not working
* OUT_OF_SERVICE: The component is out of service temporarily
* UNKNOWN: The component state is unknown

If a component throw an exception the status will be DOWN 


### Securing health endpoints

To configure the health endpoints authorization use `config.sensorConfiguration.health.globalAuthorizer`.

Example:

```typescript
config.sensorConfiguration.health.globalAuthorizer = {
      authorize: 'all',
}
```

If the authorization process fails, the health endpoint will return a 401 error code

### Example

If all components are enable and showChildren is set to true:

* A Request to https://your-application-url/sensor/health/ will return:

```text
├── booster
│  ├── database
│    ├── events
│    └── readmodels
└  └── function
```

If the database component is disabled, the same url will return:

```text
├── booster
└  └── function
```

If the request url is https://your-application-url/sensor/health/database, the component will not be returned

```text
[Empty]
```

And the children components will be disabled too using direct url https://your-application-url/sensor/health/database/events

```text
[Empty]
```

If database is enabled and showChildren is set to false and using https://your-application-url/sensor/health/

```text
├── booster
│  ├── database
│  └── function
```

using https://your-application-url/sensor/health/database, children will not be visible

```text
└── database
```

but you can access to them using the component url https://your-application-url/sensor/health/database/events

```text
└── events
```
