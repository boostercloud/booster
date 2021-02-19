# Batch File Booster Rocket for AWS

This package is a configurable Booster rocket for parallel batch file processing.
First, it creates a source Object Storage (S3) and a Staging Object Storage(S3).
Every time a new File is uploaded to the Source S3 Bucket, a new Lambda function will be triggered.

This Lambda function will

- Split the source file in smaller chunks. The chunk size is defined by the user as input parameter of the rocket.
- Persist the new formed chunk file in the Staging Bucket.
- Persist a new event (containing fileUri and fileSize) for each chunk in the Event Store.

Then, for every chunk file dropped in the Staging rocket a new Lambda function will be triggered that will:

- Read each chunk file line by line and persist a new event in the Events Store for each row

This event is defined in the Booster application and can be consumed from the Event Handler for any kind of processing.
You drop your file, and you implement your logic based on an event representing a line of the file.

*Disclaimer: Currently the rocket supports CSV and jsonl files.* 
## Usage

Install this package as a dependency in your Booster project.

```sh
npm install --save @boostercloud/rocket-batch-file-process-aws-infrastructure
```

In your Booster config file, pass a `RocketDescriptor` array to the AWS' `Provider` initializer configuring the batch file rocket:

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as AWS from '@boostercloud/framework-provider-aws'

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'test-app'
  config.provider = Provider([{
    packageName: '@boostercloud/rocket-batch-file-process-aws-infrastructure', 
    parameters: {
      config: {
        bucketName: 'test-bucket',
        chunkSize: '2',
      },
      rowEvent: {
        entityId: 'id',
        eventTypeName: 'RowAdded',
        entityTypeName: 'RowEntity',
      },
    },
  }])
})
```
