# Batch File Booster Rocket for AWS

This package is a configurable Booster rocket for parallel batch file processing.
First, it creates a source Object Storage (S3) and a Staging Object Storage(S3).
Every time a new File is uploaded to the Source S3 Bucket, a new Lambda function will be triggered.

This Lambda function will

- Split the source file in smaller chunks. The chunk size is defined by the user as input parameter of the rocket.
- Persist the new formed chunk in the Staging Bucket.
- Persist a new event (containing fileUri and fileSize) for each chunk in the Event Store.

**Disclaimer:** As of now the rocket can only process CSV files.

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
      bucketName: 'test-bucket-name', 
      chunkSize: '2',
    }
  }])
})
```
