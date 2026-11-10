# booster-rocket-file-uploads (`com.boostercloud.rocket.fileuploads`)

Java port of the file uploads rocket ([`rocket-file-uploads`](https://github.com/boostercloud/rocket-file-uploads),
release 3.2.0): a storage API for Booster applications. Commands get **presigned URLs** to upload and download files
directly from the provider's storage, **list** and (Java addition) **delete** files, and every upload is turned into a
Booster event, `UploadedFileEvent`, reduced into the entity `UploadedFileEntity` that applications project into their
own read models.

Supported providers, as in TypeScript: **Local** (files on disk, URLs served by the local server, a file watcher) and
**Azure** (Blob Storage, user delegation SAS URLs, blob triggers). There is no AWS implementation in the TypeScript
code (only in its documentation), so none here.

## Layout

The six npm packages are one Maven module (`com.boostercloud:booster-rocket-file-uploads`), one Java package each:

| TypeScript package | Java package | What |
| --- | --- | --- |
| `@boostercloud/rocket-file-uploads-types` | `…fileuploads.types` | `RocketFilesUserConfiguration`, `RocketFilesConfiguration`, `RocketProviderPackageType`, `RocketFilesParams` (`FUNCTION_ID`, `getUserConfiguration`), the provider contracts (`RocketFilesProviderLibrary`, `RocketFileUploadedLibrary`, `RocketFilesProvider`), `UploadedFileEvent`, `UploadedFileEntity`, `RocketFilesBlobUploaded`, `ListItem`, `ListItemProperties`, `Constants` (`azureStorageName`, `LOCAL_PORT_ENV`, `localPort`), `Utils` (`isValidDirectory`); Java-only: `RocketFilesConcepts`, `Minimatch` + `BraceExpansion` (ports of minimatch 3.1.5 / brace-expansion 1.1.16), `NodePath` (Node's `path.parse/join`) |
| `@boostercloud/rocket-file-uploads-core` | `…fileuploads.core` | `BoosterRocketFiles`, `FileHandler`, `FileUploaded` (`file-uploaded.ts`); Java-only `RocketFilesProviders` (the `require(rocketProviderPackage)`) |
| `@boostercloud/rocket-file-uploads-local` | `…fileuploads.local` | `RocketFilesLibrary` (the module object), `handlers.PresignedGet/PresignedPut/ListHandler`, `FileUploaded`; Java additions `handlers.DeleteFile`, `LocalStorage` |
| `@boostercloud/rocket-file-uploads-local-infrastructure` | `…fileuploads.local.infrastructure` | `Rocket` (the default export), `LocalRocketFiles`, `Infra`, `FsWatch`, `controllers.FileController` (`http.ts` is the framework's `Http`, the same code) |
| `@boostercloud/rocket-file-uploads-azure` | `…fileuploads.azure` | `RocketFilesLibrary`, `handlers.BlobService/PresignedGet/PresignedPut/ListHandler`, `FileUploaded`; Java additions `handlers.DeleteFile`, `storage.*` (the Blob REST client, SAS signing, credential chain) |
| `@boostercloud/rocket-file-uploads-azure-infrastructure` | `…fileuploads.azure.infrastructure` | `Rocket`, `AzureRocketFiles`, `Helper`, `functions.Functions`, `functions.RocketFilesFileUploadedFunction`, `synth.Synth`, `synth.TerraformStorageAccount/StorageContainer/FunctionApp/RoleAssignment`; Java-only `synth.ConstructIds` (unique construct ids) |

`List` is taken in Java, so `handlers/list.ts` is `ListHandler`.

## How it fits together

```
config block                                    request path (commands)
new BoosterRocketFiles(config, storages)        new FileHandler(Booster.config(), storageName)
  ├ registers UploadedFileEvent/Entity            ├ finds the descriptor (…local.infrastructure / …azure.infrastructure)
  ├ .rocketForLocal() / .rocketForAzure()         ├ loads its RocketFilesLibrary (rocketProviderPackage)
  │   storage names lower-cased                   ├ selects the storage (getUserConfiguration)
  │   config.registerRocketFunction("rocket-files")  └ presignedPut / presignedGet / list / deleteFile(directory[, file])
  └ RocketDescriptor(<pkg>.infrastructure,           after isValidDirectory(directory, storage.directories)
                     RocketFilesConfiguration)
                                                upload path
local: FsWatch on <project>/<storage>/<container> ─┐
azure: blob trigger fileupload_<storage>  ─────────┴► boosterRocketDispatcher(request) ► "rocket-files":
                                                       for every storage: metadata = library.getMetadataFromRequest(request)
                                                       if library.validateMetadata(storage, metadata):
                                                         provider.events.store(UploadedFileEvent { metadata })
                                                       ► reducer UploadedFileEntity(id = md5(JSON(metadata)), metadata)
                                                       ► the application's projections
```

The providers load the infrastructure rockets as `<descriptor packageName>.Rocket` (`RocketLoader`):
`com.boostercloud.rocket.fileuploads.local.infrastructure.Rocket` (a `LocalInfrastructureRocket`) and
`com.boostercloud.rocket.fileuploads.azure.infrastructure.Rocket` (an Azure `InfrastructureRocket`). The descriptor
parameters are the `RocketFilesConfiguration` record; its JSON form is TypeScript's:

```json
{ "userConfiguration": [ { "storageName": "clientst", "containerName": "rocketfiles", "directories": ["client1", "client2"] },
                         { "storageName": "cmsst", "containerName": "rocketfiles", "directories": ["cms1", "cms2"] } ],
  "rocketProviderPackage": "@boostercloud/rocket-file-uploads-local" }
```

`rocketProviderPackage` keeps the npm names, so the framework derives the same rocket key as TypeScript
(`ROCKET_PACKAGE_MAPPING=rocket-file-uploads:<function app>` on Azure); each value also names the Java runtime library
(`com.boostercloud.rocket.fileuploads.{local,azure}.RocketFilesLibrary`).

## Usage

Maven dependency of the application (one artifact for every provider; the Azure function packages contain the
application's class path, so the runtime library travels with it):

```xml
<dependency>
  <groupId>com.boostercloud</groupId>
  <artifactId>booster-rocket-file-uploads</artifactId>
  <version>4.1.0</version>
</dependency>
```

Configuration (a `BoosterConfigurator` of the application's package):

```java
public class Config implements BoosterConfigurator {
  static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1", "client2"));
  static final RocketFilesUserConfiguration CMS =
      new RocketFilesUserConfiguration("cmsst", "rocketfiles", List.of("cms1", "cms2"));

  @Override
  public void configure() {
    Booster.configure("local", config -> {
      config.appName = "test";
      config.providerPackage = "com.boostercloud.framework.provider.local";
      config.rockets = List.of(new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForLocal());
    });
    Booster.configure("production", config -> {
      config.appName = "test";
      config.providerPackage = "com.boostercloud.framework.provider.azure";
      config.rockets = List.of(new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForAzure());
    });
  }
}
```

* `storageName`: the Azure **storage account** name (lower-cased by the rocket; Azure needs 3-24 characters `[a-z0-9]`)
  or the local root folder. With several storages, commands must name one.
* `containerName`: the blob container (Azure) or the folder under the storage (local).
* `directories`: the directories files may be in, as minimatch globs (`client1`, `*`, `active-pool/**`). They are
  checked against the `directory` argument of every `FileHandler` call and against the folder of every upload
  notification. On the local server each one is also an Express route path (see below).

Commands (as in the TypeScript README):

```java
@Command(authorize = All.class)
public record FileUploadPut(String directory, String fileName, Optional<String> storageName) {
  public static String handle(FileUploadPut command, Register register) {
    return new FileHandler(Booster.config(), command.storageName().orElse(null))
        .presignedPut(command.directory(), command.fileName());
  }
}

@Command(authorize = All.class)
public record FileUploadGet(String directory, String fileName, Optional<String> storageName) {
  public static String handle(FileUploadGet command, Register register) {
    return new FileHandler(Booster.config(), command.storageName().orElse(null))
        .presignedGet(command.directory(), command.fileName());
  }
}

@Command(authorize = All.class)
public record FileUploadList(String directory, Optional<String> storageName) {
  public static List<ListItem> handle(FileUploadList command, Register register) {
    return new FileHandler(Booster.config(), command.storageName().orElse(null)).list(command.directory());
  }
}

@Command(authorize = All.class)
public record DeleteFile(String directory, String fileName, Optional<String> storageName) {
  public static boolean handle(DeleteFile command, Register register) {
    return new FileHandler(Booster.config(), command.storageName().orElse(null))
        .deleteFile(command.directory(), command.fileName());
  }
}
```

A read model of the uploads:

```java
@ReadModel(authorize = All.class)
public record UploadedFileEntityReadModel(UUID id, Object metadata) {
  @Projects(entity = UploadedFileEntity.class, joinKey = "id")
  public static ProjectionResult<UploadedFileEntityReadModel> projectUploadedFileEntity(
      UploadedFileEntity entity, UploadedFileEntityReadModel current) {
    return ProjectionResult.of(new UploadedFileEntityReadModel(entity.id(), entity.metadata()));
  }
}
```

GraphQL and HTTP:

```graphql
mutation { FileUploadPut(input: { storageName: "clientst", directory: "client1", fileName: "myfile.txt" }) }
# local: "http://localhost:3000/clientst/rocketfiles/client1/myfile.txt"
# azure: "https://clientst.blob.core.windows.net/rocketfiles/client1/myfile.txt?sv=2018-11-09&spr=https%2Chttp&...&sp=w&sig=..."
query { ListUploadedFileEntityReadModels(filter: {}) { items { id metadata } } }
```

```shell
curl -X PUT -H "Content-Type: application/octet-stream" -H "x-ms-blob-type: BlockBlob" --data-binary @myfile.txt "<FileUploadPut>"
curl "<FileUploadGet>"
```

Azure answers `201` to the upload, the local server `200` with
`{"status":"success","result":{"message":"File uploaded","data":{"name":"myfile.txt","mimeType":"unknown","size":0}}}`.
`FileUploadList` returns `[{ "name": "client1/myfile.txt", "properties": { "lastModified": "..." } }]` locally and
`[{ "name", "properties": { "createdOn", "lastModified", "contentLength", "contentType" }, "metadata": {...} }]` on
Azure. The rocket's types are external to the GraphQL schema (`JSON`), as TypeScript types imported from a package.

Errors (messages as in TypeScript): `Rocket not found. Please make sure you have setup the rocket packageName correctly`,
`Missing userConfiguration`, `Missing storageAccountName`, `Storage account name <name> not found`,
`Invalid directory <directory>`; a second `rocketForLocal()`/`rocketForAzure()` on one configuration fails with
`Error registering rocket function with id rocket-files: There is already a rocket function registered under the same ID, ""`.

## Events

For every upload notification and every storage whose `validateMetadata` accepts it, the rocket stores

```json
{ "kind": "event", "superKind": "domain", "version": 1, "typeName": "UploadedFileEvent", "entityTypeName": "UploadedFileEntity",
  "entityID": "<random uuid>", "requestID": "<the same uuid>", "createdAt": "<set by the provider>",
  "value": { "metadata": { "BOOSTER_ROCKET_FUNCTION_ID": "rocket-files",
                           "uri": "http://localhost:3000/clientst/rocketfiles/client1/myfile.txt",
                           "name": "client1/myfile.txt" } } }
```

(Azure metadata: the trigger metadata, `{ "blobTrigger": "rocketfiles/client1/myfile.txt", "uri": "https://..." }`.)
The reducer builds `UploadedFileEntity { id: md5(JSON.stringify(metadata)), metadata }`, so a read model projected by
`id` has one row per distinct metadata (per file path locally). Store failures are logged
(`[ROCKET#files] An error occurred while performing a PutItem operation: `) and swallowed; metadata errors fail the
rocket function.

## Local provider

* Files: `<project>/<storageName>/<containerName>/<directory>/<fileName>`, where `<project>` is
  `config.userProjectRootPath()` (the working directory before `Booster.start`). They stay across restarts.
* Mounting (`Infra.mountStack`): sets `ROCKET_FILES_LOCAL_PORT` to the server port (an in-process
  `EnvironmentVariables` override, read by `localPort()`), starts one watcher per storage and mounts one
  `FileController` per directory pattern at `/<storageName>/<containerName>`.
* Presigned URLs: `http://localhost:<ROCKET_FILES_LOCAL_PORT or 3000>/<storage>/<container>/<directory>/<fileName>`,
  the same for GET and PUT, nothing signed or encoded.
* `PUT /<storage>/<container>/<directory>/:fileName` writes the raw body (directories created, existing file
  truncated); `GET` downloads it as an attachment (`Content-Disposition`, type by extension). A directory pattern is an
  Express route path: plain names are literal, `*` matches anything (also several segments), matching is
  case-insensitive. The folder written comes from the raw URL and the file name from the decoded parameter. Errors:
  status of the error class + `{"title", "reason"}` (missing file: `500`, `ENOENT: no such file or directory, stat
  '<path>'`; dot files `404 Not Found`; unrouted paths Express's `404`).
* The watcher (`FsWatch`) notifies every creation, change and deletion under the container whose path has no
  dot-segment and whose parent directory matches a pattern: `boosterRocketDispatcher({ BOOSTER_ROCKET_FUNCTION_ID:
  "rocket-files", uri: "http://localhost:<port>/<storage>/<container>/<name>", name: "<dir>/<file>" })`. A file
  copied into the folder by hand is notified like an upload.
* `list(directory)`: the entries of the folder (not recursive; files, sub-folders and dot files), byte order of the
  names, `name = path.join(directory, entry)`, `properties.lastModified` = inode change time. A missing folder fails
  with `ENOENT: no such file or directory, scandir '<path>'`.
* `deleteFile(directory, fileName)`: removes the file (`false` when it did not exist); the watcher notifies it.

The local server has no authentication: the presigned URLs work for anyone who can reach the port.

## Azure provider

Runtime (`…azure`, used by the main function app when commands call `FileHandler`):

* `presignedPut` / `presignedGet`: `https://<account>.blob.core.windows.net/<container>/<directory>/<fileName>?<SAS>`,
  a **user delegation SAS** (version `2018-11-09`, `spr=https,http`, `sp=w` / `sp=r`) signed with a key from
  `POST /?restype=service&comp=userdelegationkey`. Valid for **86.4 seconds** from now (TypeScript adds its "86400
  seconds" to a millisecond timestamp; kept).
* `list(directory)`: `List Blobs` with `prefix=<directory>` and `include=metadata`, every page (a prefix: `client1`
  also lists `client10/...`).
* `deleteFile`: `Delete Blob` (with its snapshots); `false` for `BlobNotFound`.
* Calls use the Blob REST API (`x-ms-version: 2025-11-05`) over `java.net.http`, with an Azure AD token from
  `storage.DefaultAzureCredential`: environment service principal (`AZURE_TENANT_ID`, `AZURE_CLIENT_ID`,
  `AZURE_CLIENT_SECRET`), managed identity (App Service `IDENTITY_ENDPOINT`/`IDENTITY_HEADER`, else IMDS;
  `AZURE_CLIENT_ID` picks a user-assigned identity), Azure CLI. The caller needs `Storage Blob Data Contributor` on the
  account (the infrastructure grants it to the application's function app; developers can grant it to themselves:
  `az role assignment create --role "Storage Blob Data Contributor" --assignee <object id> --scope <account id>`).
  `storage.BlobStorageContext.use(...)` replaces the transport, credential, endpoint (Azurite, tests) or clock.
* Upload notifications: the blob trigger function `fileupload_<storage>` calls
  `BoosterAzureFunctions.rocketBlob`, which dispatches `{ blob, context }` with
  `context.triggerMetadata = { blobTrigger: "<container>/<blob>", uri }`; `validateMetadata` accepts the blob when
  `blobTrigger` starts with `<containerName>/` and its folder matches a pattern (others are logged as `Ignoring blob
  trigger ...`).

Infrastructure (`…azure.infrastructure`, at `synth`/`deploy`):

| Resource | Construct id | Attributes |
| --- | --- | --- |
| `azurerm_storage_account` per storage | `toTerraformName(appPrefix, <storage>rfst)` | `name = <storage>`, application resource group, `Standard`/`LRS` |
| `azurerm_storage_container` per storage | `toTerraformName(<storage>, rfsb<container>)` | `private` |
| `azurerm_windows_function_app` | `toTerraformName(appPrefix, rffunc)` | `<rg>filesfunc` on the application's plan and host storage, Functions `~4`, Java worker, system identity, settings `WEBSITE_RUN_FROM_PACKAGE`, `WEBSITE_CONTENTSHARE`, `config.env`, `BOOSTER_ENV`, `COSMOSDB_CONNECTION_STRING`, `BOOSTER_ROCKET_FUNCTION_ID=rocket-files`, `BOOSTER_APP_CLASS`, `<storage>__serviceUri=https://<storage>.blob.core.windows.net` |
| `azurerm_role_assignment` ×4 per storage | `toTerraformName(<storage><app suffix>, rfrlb / rfrlr)` | `Storage Blob Data Contributor` and `Reader and Data Access` on the account, for the rocket's function app and for the application's one |

The application's function app also gets a system-assigned identity. `mountFunctionsV4` builds
`rocket_<rg>filesfunc.zip` with one `function.json` per storage:

```json
{ "bindings": [ { "type": "blobTrigger", "direction": "in", "name": "blob", "path": "rocketfiles/{name}",
                  "connection": "clientst", "dataType": "binary" } ],
  "scriptFile": "../<app>.jar",
  "entryPoint": "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.rocketBlob" }
```

The connection is identity-based (`<storage>__serviceUri`). Names must match `^[a-z0-9_-]+$` (`Invalid containerName:
"<v>". Must match /^[a-z0-9_-]+$/.`), and storage/container names longer than 20 characters fail the construct ids
(`Error. Suffix could not be greater than 24`), as in TypeScript.

## Differences from TypeScript

* **Packaging and loading.** One Maven module; descriptors name the Java infrastructure packages
  (`com.boostercloud.rocket.fileuploads.{local,azure}.infrastructure`) instead of the npm packages, and
  `FileHandler` looks for those. The runtime library named by `rocketProviderPackage` is a class loaded once
  (`RocketFilesProviders`) instead of `require(...)`.
* **Concept registration.** `new BoosterRocketFiles(config, ...)` registers `UploadedFileEvent`, `UploadedFileEntity`
  and its reducer in that configuration (TypeScript: decorators run when the types package is imported; Java scans only
  the application's package) and declares the rocket's package external for the GraphQL schema.
* **No mutation of the caller's configuration.** Records are immutable: the descriptor holds lower-cased copies.
* **`deleteFile` exists** for both providers (the TypeScript documentation lists it as AWS-only/TODO). Locally the
  deletion is notified by the watcher like any change (an `UploadedFileEvent` is stored, as TypeScript does for any
  deletion in the folder); on Azure blob triggers do not fire on deletions.
* **`presignedPut` is generic** (`String url = handler.presignedPut(...)`) for TypeScript's `Promise<string> |
  Promise<any>`.
* **Project directory**: `config.userProjectRootPath()` (or the working directory) instead of `process.cwd()`.
* **Local server**: a target outside the container directory (a decoded `..%2F` in the file name) is refused with
  `400 Invalid Parameter Error` (TypeScript writes or reads any file of the machine). The upload is answered after the
  file is written (TypeScript answers on the request's `end`, possibly before the flush, and can answer twice on a write
  error). Error titles of I/O failures come from Java classes (`Unchecked IO Exception` instead of `Error`, same
  status and Node-like reason). The dot-file `404` and the directory fall-through of `res.download` are emulated.
* **Watcher**: the JDK `WatchService` with every folder registered (Node's recursive `fs.watch`); new folders are
  registered when they appear and the entries already in them are notified; when the JDK drops the events of a busy
  directory (more than 512 pending, `OVERFLOW`) its entries are notified again, so a burst of uploads is not lost. On
  macOS the JDK polls every 2 seconds, so notifications come later than with Node; Node's event multiplicity is
  platform-specific anyway. A failing dispatch is logged (Node: an unhandled rejection, which ends the process). The
  watchers stop when the server stops (`unmountStack`, which TypeScript does not have locally).
* **Envelope**: no `createdAt` before storing (the provider sets it in both implementations); a `null` metadata is
  stored as `{}`.
* **Azure SDK replaced** by REST calls: `@azure/storage-blob` and `@azure/identity` are not available in Java without
  the Azure SDK, which the architecture excludes. The credential chain is a subset (no workload identity, certificates,
  PowerShell or Azure Developer CLI) and is created once per process with a token cache (TypeScript creates a
  `DefaultAzureCredential` per call). REST calls use `x-ms-version: 2025-11-05`; the SAS keeps version `2018-11-09`.
  Failures are `storage.StorageError` (status, error code, service message).
* **Azure functions for the Java worker**: `function.json` definitions on `BoosterAzureFunctions.rocketBlob` replace
  the generated `functions.js`; the function app runs the Java stack (`java_version`) and gets `BOOSTER_APP_CLASS`. The
  Java entry point passes only `blobTrigger` and `uri` as trigger metadata (the Node host adds `properties`,
  `metadata`, `name`, `sys`...), so the stored metadata, and therefore the entity ids, differ from a Node deployment.
* **Terraform**: container and role assignment construct ids are deterministic, computed from the names (TypeScript
  computes them from unresolved token strings, so they are opaque); when two storages' names end alike, which would
  give the same id, a counter is added to the name;
  the identity connection keys are the literal `<account>__serviceUri` (TypeScript: a token resolving to it). The
  `FunctionApp identity not found` check is applied to the function app's `identity` attribute/override (TypeScript's
  check on a token list never fires).
* **minimatch / brace-expansion**: on Windows `\` is treated as `/` in paths and patterns (minimatch's own rule);
  brace sequences with numbers beyond `long` are not expanded as numbers.
* The `Ignoring blob trigger` lines go to a replaceable logger (`azure.FileUploaded.useLogger`, the console by default).
* Inherited from the framework's local server (see its README): in the directory patterns used as route paths only
  `*` (and `:param`) are special, so glob characters such as `?`, `+`, `()` and `[]` are literal (Express 4 treats
  them as regular expression syntax); `res.download` answers no `Range` requests and no `304`; Java errors are titled
  by class (`Unchecked IO Exception` where Node writes `Error`).

### TypeScript quirks kept on purpose

The 86.4-second SAS (and user delegation key) lifetime; SAS for `https,http`; the local `validateMetadata` accepting
every notification for every storage (one event per storage); the md5 entity id of the metadata while the event is
stored under a random entity id; several storages requiring a `storageName`; Azure listing by prefix; unencoded paths in
presigned URLs; directory patterns used as Express routes locally; the server's JSON/urlencoded parsers consuming a
`PUT` sent as `application/json` (the file is empty); `lastModified` = `ctime` locally; deletions notified as uploads
locally; the storage account not checked by the Azure `validateMetadata`; the local presigned URLs on `localhost` and
the server port.

## Not implemented

* AWS (not in the TypeScript code; AWS is not ported by this repository) and its presigned POST forms.
* The TypeScript documentation's TODOs: optional storage deletion when unmounting, optional events, deletion events.
* `DefaultAzureCredential` members beyond environment, managed identity and Azure CLI.

## Tests

`mvn -B -pl rockets/rocket-file-uploads -am verify` from `java/`. [PARITY.md](PARITY.md) maps every TypeScript test,
every item of the rocket's specification and every TypeScript source to the Java test that pins it.

* `types.UtilsTest` ports `utils.test.ts` and `azure.FileUploadedTest` ports `file-uploaded.test.ts`; the other
  packages have no TypeScript tests.
* `MinimatchDifferentialTest` / `BraceExpansionDifferentialTest` compare the ports with the JavaScript packages on
  ~9 000 / ~1 500 cases generated with Node (`src/test/resources/differential/generate.js`; rerun it with minimatch
  3.1.5 and brace-expansion 1.1.16 to regenerate). `NodePathTest` and `UploadedFileEventTest` pin Node-computed values.
* Core: descriptors, the rocket function with both libraries, store failures, `FileHandler` lookups and checks.
* Local: the library against a temporary project; `LocalFilesServerTest` runs the real local server with the rocket
  (uploads, downloads, routes, errors, quirks, notifications, unmount); `FsWatchTest` the watcher.
* Azure: `BlobServiceContractTest` against a fake Blob service (requests, SAS URLs with the signature recomputed from
  the specification, paged listing, deletion, errors), `BlobSasTest` (an OpenSSL known answer),
  `DefaultAzureCredentialTest`, `BlobTriggerEntryPointTest` (the request `BoosterAzureFunctions.rocketBlob` dispatches
  is the one the library validates); `AzureRocketSynthTest` synthesizes a whole application with the rocket through the Azure
  infrastructure and compares `golden/file-uploads.cdk.tf.json` and `golden/file-uploads-rocket-package.txt`
  (regenerate with `-Dbooster.golden.update=true`).
* `e2e.LocalEndToEndTest`: core, the local provider and the local server in-process on free ports with the
  `e2e.app` application: presigned PUT, upload, the read model produced by the notification, presigned GET, list,
  delete, restart on the same directory, files dropped by hand and the error messages.
* `e2e.LocalServerProcessEndToEndTest`: the same application run as `boost start` runs it (a separate JVM,
  `java -cp target/classes:<dependencies> Main start --port N` from a project directory, free ports): concurrent
  presigned URL mutations, uploads, overwrites, downloads and listings, the events, snapshots and read models, SIGTERM,
  and a second process over the same project directory.
* TypeScript-side expectations (`src/test/resources/typescript`, written by `generate.mjs`, which runs the rocket's
  own `functions.js` generator with Node and computes user delegation SAS vectors from the public specification with
  Node's `crypto`): `AzureRocketSynthTest` diffs the `function.json` files with `functions.js`, the name validation
  messages, and the Terraform with a hand walk of the TypeScript synth code
  (`file-uploads-resources.expected.json`), attribute by attribute; `BlobSasSpecificationVectorsTest` compares the
  SAS string-to-sign, signatures, query strings and URLs. Regenerate with
  `node generate.mjs <rocket-file-uploads repository> .` (Node 23.6 or later).

Not verifiable without deploying: that Azure accepts the generated SAS and Terraform, and how the Java worker delivers
the blob trigger binding data (`BlobTrigger`, `Uri`) to `rocketBlob`.
