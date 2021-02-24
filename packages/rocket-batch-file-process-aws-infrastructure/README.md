# Batch File Booster Rocket for AWS

This package is a configurable Booster rocket for parallel batch file processing.
First, it creates a source Object Storage (S3) and a Staging Object Storage(S3).

Every time a new File is uploaded to the Source S3 Bucket, a new Lambda function will be triggered that will:

- Split the source file in smaller chunks. The `chunkSize` is defined by the user as input parameter of the rocket (See example below).
- Persist the new formed chunk file in the Staging Bucket.
- Persist a new event (only containing `fileUri` and `fileSize`) in the Events Store for each chunk created.

Then, for every chunk file dropped in the Staging rocket a new Lambda function will be triggered that will:

- Read each chunk file line by line and persist a new event in the Events Store for each row.

This event is registered in the Booster application's store as a regular event. Then, you can create event handlers to perform any kind of processing.

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
        chunkSize: '100',
      },
      rowEvent: {
        entityId: 'id',
        eventTypeName: 'AddressAdded',
        entityTypeName: 'AddressEntity',
      },
    },
  }])
})
```
Based on this example let's also define the `AddressAdded` event and the `AddressEntity` entity in your Booster project

```typescript
import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class AddressAdded {
  public constructor(
    readonly id: UUID,
    readonly firstName: string,
    readonly lastName: string,
    readonly address: string,
    readonly city: string,
    readonly state: string,
    readonly postalCode: string
  ) {}

  public entityID(): UUID {
    return this.id
  }
}
```

```typescript
import { Entity, Reduces } from '@boostercloud/framework-core'
import { AddressAdded } from '../events/address-added'
import { UUID } from '@boostercloud/framework-types'

@Entity
export class AddressEntity {
  public constructor(
    public id: UUID,
    readonly firstName: string,
    readonly lastName: string,
    readonly address: string,
    readonly city: string,
    readonly state: string,
    readonly postalCode: string
  ) {}

  @Reduces(AddressAdded)
  public static reduceAddressAdded(event: AddressAdded, currentAddressEntity?: AddressEntity): AddressEntity {
    return new AddressEntity(
      event.id,
      event.firstName,
      event.lastName,
      event.address,
      event.city,
      event.state,
      event.postalCode
    )
  }
}
```

The attributes of the `AddressAdded` and `AddressEntity` classes are defined by the structure of the input file.
This is an example of CSV file that will work with what we defined above:
 
```csv
id,firstName,lastName,address,city,state,postalCode
1a,John,Doe,120 jefferson st.,Riverside, NJ, 08075
2b,Jack,McGinnis,220 hobo Av.,Phila, PA,09119
3c,"John ""Da Man""",Repici,120 Jefferson St.,Riverside, NJ,08075
4d,Stephen,Tyler,"7452 Terrace ""At the Plaza"" road",SomeTown,SD, 91234
5e,,Blankman,,SomeTown, SD, 00298
6f,"Joan ""the bone"" Anne",Jet,"9th at Terrace plc",Desert City,CO,00123
7g,Juan,Perez,120 jota st.,NY,NY,00678
```

The first row of the CSV file defines the headers of the schema and is a 1 to 1 match with the `AddressAdded` and `AddressEntity` attributes definition.

If we were to use `jsonl` files instead of `csv`, this would be an example 

```jsonl
{"metadata":".."}
{"id": "1a", "firstName": "John", "lastName": "Doe", "address": "120 jefferson st.", "city": "Riverside", "state": "NJ", "postalCode": "08075"}
{"id": "2b", "firstName": "Jack", "lastName": "McGinnis", "address": "220 hobo Av.", "city": "Phila", "state": "PA", "postalCode": "09119"}
```

The first line of the jsonl file is also used to specify some metadata. 

As a result the event handler will look as following

```typescript
import { EventHandler } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { AddressAdded } from '../events/address-added'

@EventHandler(AddressAdded)
export class AddressEventHandler {
  public static async handle(event: AddressAdded, register: Register): Promise<void> {
    ...
  }
}

```