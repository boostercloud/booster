# booster-framework-provider-azure-infrastructure (`com.boostercloud.framework.provider.azure.infrastructure`)

Java port of `packages/framework-provider-azure-infrastructure`: the **deploy-time** half of the Azure provider. It
synthesizes the Terraform configuration of an application, builds the Azure Functions packages (Java worker), deploys
them, points the Web PubSub hub at the function app, and deletes everything on `nuke`. No code path of a running
function app uses it (it may still travel in `lib/`, since packages contain the application's class path).

```java
config.providerPackage = "com.boostercloud.framework.provider.azure";
// Provider.provider(rockets).infrastructure() loads
// com.boostercloud.framework.provider.azure.infrastructure.Infrastructure.infrastructure(rockets)
```

`Booster.run(Main.class, args)` (core) runs `synth`, `deploy` or `nuke` through it; `start` is not supported (Azure
applications cannot run locally), exactly as in TypeScript.

Nothing in the test suite deploys or contacts Azure: Terraform, `az`, Entra ID, Resource Manager, Kudu and the function
app are reached through an injectable command runner and HTTP transport (`InfrastructureContext`), and the tests assert
the exact commands and requests.

## Layout

| Java | TypeScript | Notes |
| --- | --- | --- |
| `Infrastructure` | `src/index.ts` (`Infrastructure`) | loads every rocket eagerly (`RocketLoader`), returns `AzureInfrastructure` |
| `AzureInfrastructure` | `infrastructure/index.ts` (`synth`, `deploy`, `nuke`, `updateWebPubSubHub`, `triggerColdStart`) | `ProviderInfrastructure`; `start` throws, `supports(START)` is false |
| `ApplicationBuilder`, `ApplicationBuild` | `application-builder.ts` | |
| `AzureStack` | `azure-stack.ts` | `addAppSettingsToFunctionApp` (rocket settings) |
| `Constants` | `constants.ts` | + Java-only `BOOSTER_AZURE_JAVA_VERSION`, `BOOSTER_TERRAFORM_BINARY` |
| `InfrastructureContext` | (`process.cwd()`, the SDK clients, `runCommand`, timers) | the injectable outside world |
| `terraform.*` | `cdktn` (`App`, `TerraformStack`, `TerraformResource`, `TerraformOutput`, `Fn`, `addOverride`) | a Terraform JSON emitter, see below |
| `synth.ApplicationSynth`, `synth.Terraform*`, `synth.gateway.*` | `synth/*.ts`, `synth/gateway/*.ts` | one builder per resource, same construct ids, names and attributes |
| `types.*` | `types/*.ts` | `ApplicationSynthStack` (the mutable stack bag), `FunctionDefinition`, `Binding`, ... |
| `functions.*` | `functions/*.ts` | the legacy v3 definitions, pinned by the TypeScript tests |
| `helper.FunctionsCodeGenerator` | `helper/functions-code-generator.ts` | `function.json` definitions for the Java worker (see below) |
| `helper.FunctionZip`, `helper.FunctionAppLayout`, `helper.DeploySources`, `helper.BaseWebPubSubBinding` | `helper/function-zip.ts` | packages and Kudu upload |
| `helper.Utils`, `helper.Params`, `helper.AzCliHelper`, `helper.JsonText` | `helper/utils.ts`, `helper/params.ts`, `helper/az-cli-helper.ts` | naming rules, settings, `az` wrappers, `JSON.stringify` |
| `azure.*` | `@azure/identity`, `@azure/arm-appservice`, `@azure/arm-webpubsub`, `@azure/arm-resources`, `needle`, `fetch` | `ClientSecretCredential`, `ArmClient` (+ long-running operations), the three management clients, `HttpTransport` |
| `process.*` | `runCommand` | `Command`, `CommandRunner`, `ProcessCommandRunner` |
| `rockets.*` | `rockets/*.ts` | `InfrastructureRocket` SPI, `RocketUtils`, `RocketBuilder`, `RocketZipResource` |
| `templates.Templates` | `templates/index.ts`, `templates/policy.ts` | `templates.policy` |
| `testhelper.*` | `test-helper/*.ts` | `AzureTestHelper`, `AzureCounters`, `AzureQueries`, `ApplicationOutputs` |

## Flows

**synth** = `ApplicationBuilder.buildApplication()`, sequentially:

1. with subscriptions, `<project>/baseWebPubSubBinding.zip` (the Web PubSub bootstrap package);
2. `AzureStack` (every base resource, `ApplicationSynth`);
3. every rocket's `mountStack`, in order (failures collected, then thrown as one `PromisesError`);
4. the main function app settings: `merge(<defaults>, { ROCKET_FUNCTION_APP_NAMES, ROCKET_PACKAGE_MAPPING })`;
5. `<project>/cdktf.out/manifest.json` and `<project>/cdktf.out/stacks/<appName><environmentName>/cdk.tf.json`;
6. `<project>/functionApp.zip` and, with event streaming, `<project>/consumerFunctionApp.zip`;
7. the rocket packages `<project>/rocket_<functionApp>.zip` (rockets concurrently).

**deploy** = synth, then in the stack directory (environment `TF_IN_AUTOMATION=true` added to the inherited one):

```
terraform init -input=false
terraform plan -input=false -out=plan
terraform apply -input=false -auto-approve plan
```

(TypeScript runs `npx cdktn-cli deploy --auto-approve --ignore-missing-stack-dependencies`, which runs Terraform on the
same synthesized JSON.) A failure throws `IllegalStateException("Deployment of application <app> failed. Check
Terraform logs. \n<stderr>")` and stops. Then, as TypeScript:

* `POST .../sites/<rg>fpr/config/publishingcredentials/list` (`api-version=2023-12-01`) and
  `POST https://<credentials.name>.scm.azurewebsites.net/api/zipDeploy?isAsync=true` with basic authentication and the
  package as body (status not checked, deployment not awaited); the same for `<rg>fcs` (event streaming) and, all
  concurrently, for the rocket packages;
* with subscriptions: a cold start (`POST https://<rg>fpr.azurewebsites.net/api/graphql`, `{"query":"{ __typename }"}`,
  10 s timeout, errors ignored), 30 s, then up to 30 attempts 10 s apart of
  `POST .../sites/<rg>fpr/host/default/listkeys` until `systemKeys.webpubsub_extension` exists (cold start again
  every 5 attempts); finally `PUT .../Microsoft.SignalRService/webPubSub/<rg>wps/hubs/booster`
  (`api-version=2023-02-01`) with `{"properties":{"eventHandlers":[{"urlTemplate":
  "https://<rg>fpr.azurewebsites.net/runtime/webhooks/webpubsub?code=<key>","userEventPattern":"*","systemEvents":
  ["connect","disconnected"]}]}}`, waiting for the operation. Without the key the TypeScript error is thrown.

**nuke** = every rocket's `unmountStack()`, then `DELETE /subscriptions/<id>/resourcegroups/<rg>`
(`api-version=2021-04-01`) waiting for the long-running operation. Terraform is not involved and its local state is
left as is (TypeScript does the same).

Credentials: `AZURE_APP_ID`, `AZURE_SECRET`, `AZURE_TENANT_ID` (client credentials token for
`https://management.azure.com/.default`) and `AZURE_SUBSCRIPTION_ID`. As in TypeScript, the azurerm Terraform provider
only receives `subscription_id`; Terraform authenticates by its own means (`ARM_*` variables, `az login`...).

## Terraform synthesis without cdktn

`terraform.*` reproduces what the TypeScript code gets from CDK for Terraform:

* the construct tree with the `constructs` duplicate-id error (`There is already a Construct with name 'pip' in
  AzureStack [my-storeproduction]`, e.g. for a read model named `Pip`);
* logical ids with the `cdktf.json` flags of TypeScript (`excludeStackIdFromLogicalIds`,
  `allowSepCharsInLogicalIds`): direct children keep their construct id (`mystoreproductionrg`, `pip`, `ag`,
  `cartreadmodel`); the outputs, which TypeScript creates inside the provider construct, are
  `azureFeature<id>_<8 hex of md5("azureFeature/<id>")>`;
* references (`${azurerm_resource_group.mystoreproductionrg.location}`), `depends_on`, `provider = "azurerm"` (only
  where TypeScript sets it), `lifecycle.ignore_changes`, `addOverride` (with numeric path segments indexing lists, so
  `url_path_map.0.path_rule` works for rockets) and `Fn.merge` rendered as an HCL expression;
* attributes of object type (azurerm attributes that are `list(object)`, not nested blocks, such as the gateway
  NSG's `security_rule`) carry every attribute of the object, the unset ones as `null`, as the cdktn provider bindings
  render them: Terraform's JSON syntax requires them all (`terraform validate` rejects the stack otherwise);
* the document layout: `"//"` metadata, `data`, `output`, `provider.azurerm` (`features {}`, `subscription_id`,
  `resource_provider_registrations = "legacy"`), `resource`, `terraform.backend.local.path =
  <project>/terraform.<stack>.tfstate` and `terraform.required_providers.azurerm = { source = "azurerm", version =
  "~> 5.0" }`. Keys are sorted, so the output is deterministic.

Every resource has the TypeScript type, construct id, name and attribute values; the golden files
(`src/test/resources/golden/*.cdk.tf.json`) pin four configurations: no subscriptions, subscriptions with read models
and a scheduled command, event streaming, and rockets with WAF and the basic plan. With
`BOOSTER_TERRAFORM_VALIDATE=true` the opt-in `TerraformValidateTest` runs `terraform init -backend=false` and
`terraform validate` on synthesized stacks (it needs the azurerm provider, downloaded or cached).

## Function apps for the Java worker

TypeScript generates a `functions.js` that registers every function with the Node v4 programming model. A Java function
app declares each function with a `function.json`, so `FunctionsCodeGenerator` produces those definitions instead, with
the same function names and trigger semantics, bound to the entry points of
`com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions` (module `framework-provider-azure`):

| Function | Bindings | Entry point |
| --- | --- | --- |
| `graphql` | `httpTrigger` `request` POST anonymous, `http` `$return` | `graphql` |
| `sensor-health` | `httpTrigger` `request` GET anonymous route `sensor/health/{*componentPath}`, `$return` | `sensorHealth` |
| `eventHandler` (streaming off) / `eventProducer` (on) | `cosmosDBTrigger` `documents` on `<app>-app-events-store`, connection `COSMOSDB_CONNECTION_STRING`, lease container `leases` (created), `dataType: string` | `eventHandler` / `eventProducer` |
| `connect`, `disconnect`, `messages` (subscriptions) | `webPubSubTrigger` `request`, hub `booster`, `system/connect`, `system/disconnect`, `user/message`, connection `WebPubSubConnectionString` | `connect`, `disconnect`, `messages` |
| `<ReadModel>-subscriptions-notifier` (subscriptions) | `cosmosDBTrigger` `documents` on `<app>-app-<ReadModel>`, lease container `leases` | `subscriptionsNotifier` |
| `scheduleFunction-<Command>` | `timerTrigger` `timer`, `schedule` = `0 <minute> <hour> <day> <month> <weekDay>` (absent = `*`, `year` ignored, empty schedules skipped) | `scheduledCommand` |
| `eventConsumer` (consumer app) | `eventHubTrigger` `messages`, connection `EVENTHUB_CONNECTION_STRING`, hub = stream topic (`events-stream` when empty), cardinality `many`, `dataType: string` | `eventConsumer` |

Package layout (`FunctionAppLayout`):

```
functionApp.zip
├── <appName>.jar                 the class path directories (target/classes): the scriptFile of every function
├── lib/*.jar                     the class path jars (target/lib): Booster, the Azure provider, ...
├── <assets>                      config.assets, with their project-relative paths
├── <function>/function.json      one per function (JSON.stringify(config, null, 2))
└── host.json                     {"version": "2.0", "extensionBundle": {"id": "Microsoft.Azure.Functions.ExtensionBundle", "version": "[4.*, 5.0.0)"}}
```

The sources are the class path of the running application (`DeploySources.fromRuntime`: the `boost` CLI launches it
with `target/classes` and `target/lib/*`), which is the Java counterpart of the `.deploy` sandbox. Entries have a fixed
timestamp, so packages are reproducible. The consumer package has the same layout with `eventConsumer` only; rocket v4
packages have the same layout with the rocket's functions and the rocket's `host.json` (an empty custom file means no
`host.json`, as in TypeScript); legacy packages (`mountCode`/`mountFunctions`) keep every source file, root `host.json`
included, and add the default `host.json` only when the sources have none.

The bootstrap package `baseWebPubSubBinding.zip` (Terraform's `zip_deploy_file` of the main app) contains the files of
`<project>/.deploy-base` when it exists and, with subscriptions, a `baseWebPubSubBinding` function (Web PubSub
`user/message`, connection `WebPubSubConnectionString`) whose class is generated with the class-file API for Java 8
(`public String run(String)` returning `{"data":"ok"}`), so the runtime loads the Web PubSub extension and creates its
system key.

Function apps are `azurerm_windows_function_app` with `functions_extension_version = "~4"` and
`site_config.application_stack.java_version = $BOOSTER_AZURE_JAVA_VERSION` (default `21`). Every Booster function app
gets the Java-only app setting `BOOSTER_APP_CLASS` (the application class: `BOOSTER_APP_CLASS` of the deploying
process, else its main class), which `BoosterAzureFunctions` passes to `Booster.start`.

## App settings

Main (`func`) and consumer (`fhub`) apps, in this order (`config.env` can override only the first two):
`WEBSITE_RUN_FROM_PACKAGE=1`, `WEBSITE_CONTENTSHARE`, every `config.env` entry, `WebPubSubConnectionString`,
`BOOSTER_ENV`, `BOOSTER_REST_API_URL=http://<rg>apis.<region>.cloudapp.azure.com/<env>`,
`EVENTHUB_CONNECTION_STRING`, `EVENTHUB_NAME`, `EVENTHUB_MAX_RETRIES` (default `5`), `EVENTHUB_MODE` (default
`exponential`), `COSMOSDB_CONNECTION_STRING`, `WEBSITE_CONTENTAZUREFILECONNECTIONSTRING`, `BOOSTER_APP_NAME`,
`BOOSTER_APP_CLASS`. The main app also gets `ROCKET_FUNCTION_APP_NAMES` (every rocket's function app, `,`-joined) and
`ROCKET_PACKAGE_MAPPING` (`<package>:<functionApp>` joined with `;`, the package being `rocketProviderPackage` without
`@scope/` and its last `-segment`, mapped to the function app of the **first** rocket defining `getFunctionAppName`,
a TypeScript quirk kept on purpose).

Deploying machine settings: `REGION` (required), `AZURE_*`, `AZURE_MAX_CONTAINER_THROUGHPUT` /
`AZURE_MAX_DATABASE_THROUGHPUT` (`parseInt`, default 1000; non-numeric = `null`), `BOOSTER_AZURE_SERVICE_PLAN_BASIC`
(`true` = B1 with 2 workers, else Y1), `BOOSTER_USE_WAF` (`true` = `WAF_v2` with a detection WAF), `BOOSTER_ENV`
(gateway paths and outputs, default `azure`), `BOOSTER_APP_NAME`; Java only: `BOOSTER_APP_CLASS`,
`BOOSTER_AZURE_JAVA_VERSION`, `BOOSTER_TERRAFORM_BINARY` (default `terraform`, e.g. `tofu`). All are read through
`EnvironmentVariables`.

## Rockets

An Azure rocket package `<pkg>` contains `<pkg>.Rocket`, a `RocketBuilder` (framework-common-helpers) whose
`build(parameters)` returns an `InfrastructureRocket`:

```java
public interface InfrastructureRocket {
  ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils);  // required
  default void unmountStack() {}
  default String getFunctionAppName(ApplicationSynthStack stack) { ... }
  @Deprecated default List<FunctionDefinition> mountFunctions(BoosterConfig, ApplicationSynthStack, RocketUtils) { ... }
  default List<FunctionAppFunctionsDefinition> mountCode(BoosterConfig, ApplicationSynthStack, RocketUtils) { ... }
  default List<FunctionAppV4Definition> mountFunctionsV4(BoosterConfig, ApplicationSynthStack, RocketUtils) { ... }
  default boolean defines(Hook hook) { ... }   // overridden = "defined" (TypeScript: the member is not undefined)
}
```

`mountStack` adds `TerraformResource`s to `stack.terraformStack` and/or patches the bag (`stack.appGateway.addOverride(
"backend_address_pool", ...)`, `stack.functionApp.addOverride("identity", ...)`); references come from
`resource.ref("name")`. `TerraformFunctionApp.siteConfig()` and `stack.boosterAppClass` give rocket function apps the
Java stack and the application class. Packages: `mountFunctionsV4` else `mountCode` else `mountFunctions`, with the
TypeScript names (`rocket_<name with \W+ → _>.zip`), logs and errors (`mountCode method not found on rocket [object
Object]`, `FunctionAppName not found on rocket [object Object]`). Rocket functions are `function.json` definitions on
`BoosterAzureFunctions.rocketHttp` / `rocketBlob` (`Binding.httpTrigger`, `Binding.blobTrigger`, ...).

## Test helper

`AzureTestHelper.build(appName, environmentName)` checks the resource group (`az group show`) and the Azure settings,
reads the Cosmos DB connection string (`az cosmosdb list-connection-strings`) and exposes `outputs()` (`graphqlURL`
with `BOOSTER_ENV` defaulting to `DEFAULT`, `websocketURL` on hub `booster`, `healthURL` with a trailing slash),
`counters()` and `queries()` (the method shapes of application-tester's `ProviderTestHelper`, `Counters`, `Queries`),
backed by the Cosmos DB REST client of `framework-provider-azure`. The counters run `SELECT VALUE COUNT(1) FROM c`
(`WHERE c.kind="event"` for events).

## Differences from TypeScript

* **No cdktn / cdktn-cli / Node.** The Terraform JSON is emitted by `terraform.*`, and `deploy` runs `terraform init`,
  `plan -out=plan` and `apply plan` itself. No `cdktf.json` is written (it only configured `cdktn-cli`). The JSON keys
  are sorted; the exact key order of cdktn and the content of its `"//"` metadata (`outputs`, `version`) could not be
  checked against cdktn in this repository. `required_providers.azurerm.version` is the constraint `~> 5.0`
  (`@cdktn/provider-azurerm` 19 is generated for azurerm 5.x; the exact pinned version was not available).
* **HCL expression rendering**: inside `merge(...)` string literals escape `\`, `"`, newlines, tabs and `%{`
  (cdktn only escapes `"`); `${...}` in `config.env` values stays an interpolation, as in cdktn.
* **Java worker instead of Node.** `function.json` definitions replace `functions.js`; there is no generated
  `package.json`; `application_stack.java_version` replaces `node_version: '~22'`; Cosmos DB and Event Hub triggers
  add `dataType: string` and the Java binding names (`request`, `documents`, `messages`, `timer`); the Web PubSub
  triggers have no `$return` binding (as the Node v4 registration). The `BOOSTER_APP_CLASS` app setting is new.
* **One configuration**: TypeScript re-reads `<cwd>/dist/index.js` for the resources; Java uses the configuration it is
  given (`readProjectConfig` is not ported).
* **Project root**: `config.userProjectRootPath()` (set by `Booster.start`) or the working directory, instead of
  `process.cwd()`; packages are built in a temporary file (not a fixed `os.tmpdir()` name) and copied there.
* **`config.assets`**: copied into the packages; a root `host.json` asset is ignored by the main/v4 packages (the code
  ignores it; the documentation claims it is used) and kept by legacy packages.
* **Settings are read when used** (once per operation) through `EnvironmentVariables`, not at module load.
* **Rockets' `mountStack` run sequentially** in order (TypeScript starts them in order and awaits them together; the
  hooks mutate the shared stack), failures aggregated as `PromisesError` (message: `Name: message` parts joined with
  `. `). `unmountStack` is synchronous.
* **Errors**: plain TypeScript `Error`s and string rejections are `IllegalStateException`s with the same messages
  (`IllegalArgumentException` for `toTerraformName` and duplicate construct ids); Resource Manager failures are
  `ArmError` (`code`, `statusCode`, the ARM message), token failures `AuthenticationError`.
* **SDK clients** are small REST clients with the SDK's long-running operation protocol (`Azure-AsyncOperation`,
  `Location`, `provisioningState`, `Retry-After`, 2 s default); API versions: `Microsoft.Web` `2023-12-01`,
  `Microsoft.SignalRService` `2023-02-01`, `Microsoft.Resources` `2021-04-01`.
* **Test helper** counters return `long` (`-1` where TypeScript returns `undefined`) and query
  `SELECT VALUE COUNT(1) FROM c` instead of `SELECT COUNT(1) as total FROM c`: across partitions a non-`VALUE`
  aggregate needs the JS SDK's client-side `GROUP BY` pipeline, which the REST client does not implement (same counts).
  The `az` rejections that are strings in TypeScript are exceptions (`RunCommandError` carries `az`'s standard error,
  e.g. `ResourceGroupNotFound`).
* **Not ported (dead code in TypeScript)**: `TerraformWebPubSubExtensionKey`, `TerraformSleep`,
  `TerraformFunctionAppData` (replaced by the post-deploy key polling) and the `time`/`null` providers they needed.

### TypeScript quirks kept on purpose

`websocketURL` output uses the event hub name instead of `booster`; outputs scoped to the provider construct; every
package mapped to the first rocket's function app; the disconnect trigger's `eventName: 'disconnect'` vs the hub's
`disconnected` system event; one shared lease container `leases`; the dispatched events container created but not in
`stack.containers` without subscriptions; rocket app names sanitized with `\W+ → _` and used to upload; Kudu status not
checked and the host taken from the credentials' `name`; `AzureTestHelper.graphqlURL` defaulting `BOOSTER_ENV` to
`DEFAULT`; `ensureAzureConfiguration` printing the secret; NSG rules allowing all TCP and the gateway on plain HTTP.

## Verification

`mvn -B -pl framework-provider-azure-infrastructure -am verify` runs everything without deploying: golden
`cdk.tf.json` files, resource builder guards and knobs, the function definitions and package entries (application jar
contents, `function.json` and `host.json` texts, reproducible bytes, a loadable bootstrap class), the deploy/nuke/synth
flows with recorded commands and HTTP calls (Terraform sequence and environment, Kudu uploads, key polling and its
timing, hub update body, resource group deletion), the Resource Manager client and token protocol, the production HTTP
transport against an in-JVM server, the rocket SPI (priority, names, errors, app settings), the `az` helper and the test
helper.

Not verifiable without deploying: that Terraform and the azurerm 5.x provider accept the JSON (run the opt-in
`TerraformValidateTest`), the Java worker's handling of the generated `function.json` files (binding data types, the
Web PubSub return value without `$return`), that Azure Functions runs a Java 27 application (the runtime currently
offers up to Java 21; see `BOOSTER_AZURE_JAVA_VERSION`), and the live API versions and long-running operation headers.

The parity ledger of both Azure modules (item -> Java test, audit fixes, intentional differences, unverifiable
items, and a hand walk of the TypeScript synthesis compared with the golden files) is
[`../framework-provider-azure/PARITY.md`](../framework-provider-azure/PARITY.md).
