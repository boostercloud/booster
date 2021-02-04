# File Uploader Booster Rocket for AWS

This package is a configurable Booster rocket to add an Object Storage(S3 bucket) to your Booster Application. 
Every time a new object is uploaded, a new event will be persisted in the Events Store will data like the fileUri and file size.

## Usage

Install this package as a dependency in your Booster project.

```sh
npm install @boostercloud/rocket-static-sites-aws-infrastructure
```

In your Booster config file, pass a `RocketDescriptor` array to the AWS' `Provider` initializer configuring the file uploader rocket:

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as AWS from '@boostercloud/framework-provider-aws'

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'test-app'
  config.provider = Provider([{
    packageName: '@boostercloud/rocket-fileuploader-aws-infrastructure', 
    parameters: {
      bucketName: 'test-bucket-name', // Required
      eventTypeName: 'FileAdded',
      entityTypeName: 'File',
    }
  }])
})
```

`FileAdded` event type and `File` entity type can have different names but they should comply to the following structure:

```typescript
import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class FileAdded {
  public constructor(readonly fileURI: string, readonly filesize: number) {}

  public entityID(): UUID {
    return this.fileURI
  }
}

```

```typescript
import { Entity, Reduces } from '@boostercloud/framework-core'
import { FileAdded } from '../events/file-added'
import { UUID } from '@boostercloud/framework-types'

@Entity
export class File {
  public constructor(public id: UUID, readonly filesize: number) {}

  @Reduces(FileAdded)
  public static reduceBigFileAdded(event: FileAdded, currentFile?: File): File {
    return new File(event.fileURI, event.filesize)
  }
}


```
