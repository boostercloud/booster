import TabItem from '@theme/TabItem'
import Tabs from '@theme/Tabs'

# File Uploads Rocket

This package is a configurable rocket to add a storage API to your Booster applications.

:::info
[GitHub Repo](https://github.com/boostercloud/rocket-file-uploads)
:::

## Supported Providers
- Azure Provider
- AWS Provider
- Local Provider

## Overview
This rocket provides some methods to access files stores in your cloud provider:

- **presignedPut**: Returns a presigned put url and the necessary form params. With this url files can be uploaded directly to your provider.
- **presignedGet**:  Returns a presigned get url to download a file. With this url files can be downloaded directly from your provider.
- **list**: Returns a list of files stored in the provider.
- **deleteFile**: Removes a file from a directory (only supported in AWS at the moment).

These methods may be used from a Command in your project secured via JWT Token.
This rocket also provides a Booster Event each time a file is uploaded.


## Usage

<Tabs groupId="providers-usage">
<TabItem value="azure-provider" label="Azure Provider" default>

Install needed dependency packages:
```bash
npm install --save @boostercloud/rocket-file-uploads-core @boostercloud/rocket-file-uploads-types
npm install --save @boostercloud/rocket-file-uploads-azure
```

Also, you will need a devDependency in your project:
```bash
npm install --save-dev @boostercloud/rocket-file-uploads-azure-infrastructure
```

In your Booster config file, configure your BoosterRocketFiles:

```typescript title="src/config/config.ts"
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { BoosterRocketFiles } from '@boostercloud/rocket-file-uploads-core'
import { RocketFilesUserConfiguration } from '@boostercloud/rocket-file-uploads-types'

const rocketFilesConfigurationDefault: RocketFilesUserConfiguration = {
  storageName: 'STORAGE_NAME',
  containerName: 'CONTAINER_NAME',
  directories: ['DIRECTORY_1', 'DIRECTORY_2'],
}

const rocketFilesConfigurationCms: RocketFilesUserConfiguration = {
  storageName: 'cmsst',
  containerName: 'rocketfiles',
  directories: ['cms1', 'cms2'],
}

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'TEST_APP_NAME'
  config.providerPackage = '@boostercloud/framework-provider-azure'
  config.rockets = [
    new BoosterRocketFiles(config, [rocketFilesConfigurationDefault, rocketFilesConfigurationCms]).rocketForAzure(),
  ]
})

```
:::info
Available parameters are:
- **storageName**: Name of the storage repository.
- **containerName**: Directories container.
- **directories**: A list of folders where the files will be stored.

---
The structure created will be:
```text
├── storageName
│   ├── containerName
│   │   ├── directory
```

**NOTE:** Azure Provider will use `storageName` as the Storage Account Name.
:::


## Rocket Methods Usage

<details>
    <summary>Presigned Put</summary>

Create a command in your application and call the `presignedPut` method on the `FileHandler` class with the directory and filename you want to upload on the storage.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-put.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'

@Command({
  authorize: 'all',
})
export class FileUploadPut {
  public constructor(readonly directory: string, readonly fileName: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadPut, register: Register): Promise<string> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.presignedPut(command.directory, command.fileName)
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadPut(input: {
    storageName: "clientst",
    directory: "client1",
    fileName: "myfile.txt"
    }
  )
}
```

Azure Response:
```json
{
  "data": {
    "FileUploadPut": "https://clientst.blob.core.windows.net/rocketfiles/client1/myfile.txt?<SAS>"
  }
}
```
</details>
<details>
    <summary>Presigned Get</summary>

Create a command in your application and call the `presignedGet` method on the `FileHandler` class with the directory and filename you want to get on the storage.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-get.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'

@Command({
  authorize: 'all',
})
export class FileUploadGet {
  public constructor(readonly directory: string, readonly fileName: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadGet, register: Register): Promise<string> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.presignedGet(command.directory, command.fileName)
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadGet(input: {
    storageName: "clientst",
    directory: "client1",
    fileName: "myfile.txt"
  }
  )
}
```

Azure Response:
```json
{
  "data": {
    "FileUploadGet": "https://clientst.blob.core.windows.net/rocketfiles/folder01%2Fmyfile.txt?<SAS>"
  }
}
```
</details>
<details>
    <summary>List</summary>

Create a command in your application and call the `list` method on the `FileHandler` class with the directory you want to get the info and return the formatted results.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-list.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'
import { ListItem } from '@boostercloud/rocket-file-uploads-types'

@Command({
  authorize: 'all',
})
export class FileUploadList {
  public constructor(readonly directory: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadList, register: Register): Promise<Array<ListItem>> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.list(command.directory)
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadList(input: {
    storageName: "clientst",
    directory: "client1"
    }
  )
}
```

Response:
```json
{
  "data": {
    "FileUploadList": [
      {
        "name": "client1/myfile.txt",
        "properties": {
          "createdOn": "2022-10-26T05:40:47.000Z",
          "lastModified": "2022-10-26T05:40:47.000Z",
          "contentLength": 6,
          "contentType": "text/plain"
        }
      }
    ]
  }
}
```

</details>

<details>
    <summary>Delete File</summary>
    Currently, the option to delete a file is only available on AWS. If this is a feature you were looking for, please let us know on Discord. Alternatively, you can implement this feature and submit a pull request on GitHub for this Rocket!
</details>

## Azure Roles
:::info
Starting at version **0.31.0** this Rocket use Managed Identities instead of Connection Strings. Please, check that you have the required permissions to assign roles https://learn.microsoft.com/en-us/azure/role-based-access-control/role-assignments-portal-managed-identity#prerequisites
:::

For uploading files to Azure you need the Storage Blob Data Contributor role. This can be assigned to a user using the portal or with the next scripts:

First, check if you have the correct permissions:
```bash
ACCOUNT_NAME="<STORAGE ACCOUNT NAME>"
CONTAINER_NAME="<CONTAINER NAME>"

# use this to test if you have the correct permissions
az storage blob exists --account-name $ACCOUNT_NAME `
                        --container-name $CONTAINER_NAME `
                        --name blob1.txt --auth-mode login
```

If you don't have it, then run this script as admin:

```bash
ACCOUNT_NAME="<STORAGE ACCOUNT NAME>"
CONTAINER_NAME="<CONTAINER NAME>"

OBJECT_ID=$(az ad user list --query "[?mailNickname=='<YOUR MAIL NICK NAME>'].objectId" -o tsv)
STORAGE_ID=$(az storage account show -n $ACCOUNT_NAME --query id -o tsv)

az role assignment create \
    --role "Storage Blob Data Contributor" \
    --assignee $OBJECT_ID \
    --scope "$STORAGE_ID/blobServices/default/containers/$CONTAINER_NAME"
```

</TabItem>
<TabItem value="aws-provider" label="AWS Provider" default>

Install needed dependency packages:
```bash
npm install --save @boostercloud/rocket-file-uploads-core @boostercloud/rocket-file-uploads-types
npm install --save @boostercloud/rocket-file-uploads-aws
```
Also, you will need a devDependency in your project:
```bash
npm install --save-dev @boostercloud/rocket-file-uploads-aws-infrastructure
```

In your Booster config file, configure your BoosterRocketFiles:

```typescript title="src/config/config.ts"
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { BoosterRocketFiles } from '@boostercloud/rocket-file-uploads-core'
import { RocketFilesUserConfiguration } from '@boostercloud/rocket-file-uploads-types'

const rocketFilesConfigurationDefault: RocketFilesUserConfiguration = {
  storageName: 'STORAGE_NAME',
  containerName: '', // Not used in AWS, you can just pass an empty string
  directories: ['DIRECTORY_1', 'DIRECTORY_2'],
}

const rocketFilesConfigurationCms: RocketFilesUserConfiguration = {
  storageName: 'cmsst',
  containerName: '', // Not used in AWS, you can just pass an empty string
  directories: ['cms1', 'cms2'],
}

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'TEST_APP_NAME'
  config.providerPackage = '@boostercloud/framework-provider-aws'
  config.rockets = [
    new BoosterRocketFiles(config, [rocketFilesConfigurationDefault, rocketFilesConfigurationCms]).rocketForAWS(),
  ]
})
```

:::info
Available parameters are:
- **storageName**: Name of the storage repository.
- **directories**: A list of folders where the files will be stored.

---
The structure created will be:
```text
├── storageName
│   ├── directory
```
:::

## Rocket Methods Usage

<details>
    <summary>Presigned Put</summary>

Create a command in your application and call the `presignedPut` method on the `FileHandler` class with the directory and filename you want to upload on the storage.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-put.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'

@Command({
  authorize: 'all',
})
export class FileUploadPut {
  public constructor(readonly directory: string, readonly fileName: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadPut, register: Register): Promise<PresignedPostResponse> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.presignedPut(command.directory, command.fileName) as Promise<PresignedPostResponse>
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadPut(input: { 
    directory: "files", 
    fileName: "lol.jpg"
  }) {
    url
    fields
  }
}
```

AWS Response:
```json
{
  "data": {
    "FileUploadPut": {
      "url": "https://s3.eu-west-1.amazonaws.com/myappstorage",
      "fields": {
        "Key": "files/lol.jpg",
        "bucket": "myappstorage",
        "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
        "X-Amz-Credential": "blablabla.../eu-west-1/s3/aws4_request",
        "X-Amz-Date": "20230207T142138Z",
        "X-Amz-Security-Token": "IQoJb3JpZ2... blablabla",
        "Policy": "eyJleHBpcmF0a... blablabla",
        "X-Amz-Signature": "60511... blablabla"
      }
    }
  }
}
```
</details>
<details>
    <summary>Presigned Get</summary>

Create a command in your application and call the `presignedGet` method on the `FileHandler` class with the directory and filename you want to get on the storage.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-get.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'

@Command({
  authorize: 'all',
})
export class FileUploadGet {
  public constructor(readonly directory: string, readonly fileName: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadGet, register: Register): Promise<string> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.presignedGet(command.directory, command.fileName)
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadGet(input: {
    storageName: "clientst",
    directory: "client1",
    fileName: "myfile.txt"
  }
  )
}
```

AWS Response:
```json
{
  "data": {
    "FileUploadGet": "https://myappstorage.s3.eu-west-1.amazonaws.com/client1/myfile.txt?<presigned_params>"
  }
}
```
</details>

<details>
    <summary>List</summary>

Create a command in your application and call the `list` method on the `FileHandler` class with the directory you want to get the info and return the formatted results.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-list.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'
import { ListItem } from '@boostercloud/rocket-file-uploads-types'

@Command({
  authorize: 'all',
})
export class FileUploadList {
  public constructor(readonly directory: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadList, register: Register): Promise<Array<ListItem>> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.list(command.directory)
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadList(input: {
    storageName: "clientst",
    directory: "client1"
    }
  )
}
```

Response:
```json
{
  "data": {
    "FileUploadList": [
      {
        "name": "client1/myfile.txt",
        "properties": {
          "createdOn": "2022-10-26T05:40:47.000Z",
          "lastModified": "2022-10-26T05:40:47.000Z",
          "contentLength": 6,
          "contentType": "text/plain"
        }
      }
    ]
  }
}
```

</details>

<details>
    <summary>Delete File</summary>

Create a command in your application and call the `deleteFile` method on the `FileHandler` class with the directory and file name you want to delete.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/delete-file.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'
import { ListItem } from '@boostercloud/rocket-file-uploads-types'

@Command({
  authorize: 'all',
})
export class DeleteFile {
  public constructor(readonly directory: string, readonly fileName: string, readonly storageName?: string) {}

  public static async handle(command: DeleteFile, register: Register): Promise<boolean> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.deleteFile(command.directory, command.fileName)
  }
}
```

GraphQL Mutation:
```json
mutation {
  DeleteFile(input: {
    storageName: "clientst",
    directory: "client1",
    fileName: "myfile.txt"
    }
  )
}
```

Response:
```json
{
  "data": {
    "DeleteFile": true
  }
}
```

</details>

</TabItem>
<TabItem value="local-provider" label="Local Provider" default>

Install needed dependency packages:
```bash
npm install --save @boostercloud/rocket-file-uploads-core @boostercloud/rocket-file-uploads-types
npm install --save @boostercloud/rocket-file-uploads-local
```
Also, you will need a devDependency in your project:
```
npm install --save-dev @boostercloud/rocket-file-uploads-local-infrastructure
```

In your Booster config file, configure your BoosterRocketFiles:

```typescript title="src/config/config.ts"
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { BoosterRocketFiles } from '@boostercloud/rocket-file-uploads-core'
import { RocketFilesUserConfiguration } from '@boostercloud/rocket-file-uploads-types'

const rocketFilesConfigurationDefault: RocketFilesUserConfiguration = {
  storageName: 'STORAGE_NAME',
  containerName: 'CONTAINER_NAME',
  directories: ['DIRECTORY_1', 'DIRECTORY_2'],
}

const rocketFilesConfigurationCms: RocketFilesUserConfiguration = {
  storageName: 'cmsst',
  containerName: 'rocketfiles',
  directories: ['cms1', 'cms2'],
}

Booster.configure('local', (config: BoosterConfig): void => {
  config.appName = 'TEST_APP_NAME'
  config.providerPackage = '@boostercloud/framework-provider-local'
  config.rockets = [
    new BoosterRocketFiles(config, [rocketFilesConfigurationDefault, rocketFilesConfigurationCms]).rocketForLocal(),
  ]
})
```

:::info
Available parameters are:
- **storageName**: Name of the storage repository.
- **containerName**: Directories container.
- **directories**: A list of folders where the files will be stored.

---
The structure created will be:
```text
├── storageName
│   ├── containerName
│   │   ├── directory
```

**NOTE:** Local Provider will use `storageName` as the root folder name.
:::

## Rocket Methods Usage

<details>
    <summary>Presigned Put</summary>
Create a command in your application and call the `presignedPut` method on the `FileHandler` class with the directory and filename you want to upload on the storage.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-put.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'

@Command({
  authorize: 'all',
})
export class FileUploadPut {
  public constructor(readonly directory: string, readonly fileName: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadPut, register: Register): Promise<string> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.presignedPut(command.directory, command.fileName)
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadPut(input: {
    storageName: "clientst",
    directory: "client1",
    fileName: "myfile.txt"
    }
  )
}
```

Response:
```json
{
  "data": {
    "FileUploadPut": "http://localhost:3000/clientst/rocketfiles/client1/myfile.txt"
  }
}
```
</details>
<details>
    <summary>Presigned Get</summary>

Create a command in your application and call the `presignedGet` method on the `FileHandler` class with the directory and filename you want to get on the storage.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-get.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'

@Command({
  authorize: 'all',
})
export class FileUploadGet {
  public constructor(readonly directory: string, readonly fileName: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadGet, register: Register): Promise<string> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.presignedGet(command.directory, command.fileName)
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadGet(input: {
    storageName: "clientst",
    directory: "client1",
    fileName: "myfile.txt"
  }
  )
}
```

Response:
```json
{
  "data": {
    "FileUploadGet": "http://localhost:3000/clientst/rocketfiles/client1/myfile.txt"
  }
}
```
</details>
<details>
    <summary>List</summary>

Create a command in your application and call the `list` method on the `FileHandler` class with the directory you want to get the info and return the formatted results.

The storageName parameter is optional. It will use the first storage if undefined.

```typescript title="src/commands/file-upload-list.ts"
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { FileHandler } from '@boostercloud/rocket-file-uploads-core'
import { ListItem } from '@boostercloud/rocket-file-uploads-types'

@Command({
  authorize: 'all',
})
export class FileUploadList {
  public constructor(readonly directory: string, readonly storageName?: string) {}

  public static async handle(command: FileUploadList, register: Register): Promise<Array<ListItem>> {
    const boosterConfig = Booster.config
    const fileHandler = new FileHandler(boosterConfig, command.storageName)
    return await fileHandler.list(command.directory)
  }
}
```

GraphQL Mutation:
```json
mutation {
  FileUploadList(input: {
    storageName: "clientst",
    directory: "client1"
    }
  )
}
```

Response:
```json
{
  "data": {
    "FileUploadList": [
      {
        "name": "client1/myfile.txt",
        "properties": {
          "lastModified": "2022-10-26T10:35:18.905Z"
        }
      }
    ]
  }
}
```

</details>

<details>
    <summary>Delete File</summary>
    Currently, the option to delete a file is only available on AWS. If this is a feature you were looking for, please let us know on Discord. Alternatively, you can implement this feature and submit a pull request on GitHub for this Rocket!
</details>

## Security
Local Provider doesn't check paths. You should check that the directory and files passed as paratemers are valid.

</TabItem>
</Tabs>

---

## Events

For each uploaded file a new event will be automatically generated and properly reduced on the entity `UploadedFileEntity`.

<Tabs groupId="providers-usage">
<TabItem value="azure-and-aws-provider" label="Azure & AWS Provider" default>

The event will look like this:

```typescript
{
  "version": 1,
  "kind": "snapshot",
  "superKind": "domain",
  "requestID": "xxx",
  "entityID": "xxxx",
  "entityTypeName": "UploadedFileEntity",
  "typeName": "UploadedFileEntity",
  "value": {
    "id": "xxx",
    "metadata": {
      // A bunch of fields (depending on Azure or AWS)
    }
  },
  "createdAt": "2022-10-26T10:23:36.562Z",
  "snapshottedEventCreatedAt": "2022-10-26T10:23:32.34Z",
  "entityTypeName_entityID_kind": "UploadedFileEntity-xxx-b842-x-8975-xx-snapshot",
  "id": "x-x-x-x-x",
  "_rid": "x==",
  "_self": "dbs/x==/colls/x=/docs/x==/",
  "_etag": "\"x-x-0500-0000-x\"",
  "_attachments": "attachments/",
  "_ts": 123456
}
```

</TabItem>

<TabItem value="local-provider" label="Local Provider" default>

The event will look like this:

```typescript
{
  "version": 1,
  "kind": "snapshot",
  "superKind": "domain",
  "requestID": "x",
  "entityID": "x",
  "entityTypeName": "UploadedFileEntity",
  "typeName": "UploadedFileEntity",
  "value": {
    "id": "x",
    "metadata": {
      "uri": "http://localhost:3000/clientst/rocketfiles/client1/myfile.txt",
      "name": "client1/myfile.txt"
    }
  },
  "createdAt": "2022-10-26T10:35:18.967Z",
  "snapshottedEventCreatedAt": "2022-10-26T10:35:18.958Z",
  "_id": "lMolccTNJVojXiLz"
}
```

</TabItem>
</Tabs>

## TODOs
- Add file deletion to Azure and Local (only supported in AWS at the moment).
- Optional storage deletion when unmounting the stack.
- Optional events, in case you don't want to store that information in the events-store.
- When deleting a file, save a deletion event in the events-store. Only uploads are stored at the moment.