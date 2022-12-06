# Going deeper with Booster

## Contributing

If you want to start making contributions to Booster, we strongly recommend that you read our [contributing guide](contributing).

## Framework Core

The `framework-core` package includes the most important components of the framework abstraction. It can be seen as skeleton or the main architecture of the framework.

The package defines the specification of how should a Booster application work without taking into account the specific providers that could be used. Every Booster provider package is based on the components that the framework core needs in order to work on the platform.

## Framework Types

The `framework-types` packages includes the types that define the domain of the Booster framework. It defines domain concepts like an `Event`, a `Command` or a `Role`.

## Framework integration tests

Booster framework integration tests package is used to test the Booster project itself, but it is also an example of how a Booster application could be tested. We encourage developers to have a look at our [Booster project repository](https://github.com/boostercloud/booster/tree/main/packages/framework-integration-tests).

Some integration tests highly depend on the provider chosen for the project, and the infrastructure is normally deployed in the cloud right before the tests run. Once tests are completed, the application is teared down.

There are several types of integration tests in this package:

- Tests to ensure that different packages integrate as expected with each other.
- Tests to ensure that a Booster application behaves as expected when it is hit by a client (a GraphQL client).
- Tests to ensure that the application behaves in the same way no matter what provider is selected.

If you are curious about the framework providers, you will be able to read more about them in the following section.

## Providers

The providers are different implementations of the Booster runtime to allow Booster applications run on different cloud providers or services. They all implement the same interface, and the main idea behind the providers is that no matter what the developer chooses as backend, they won't need to know anything about the underlying infrastructure.

Currently, the Booster framework provides a fully working provider package:

-  **framework-provider-aws-\***

Other providers packages are currently under experimental support. Some of the features might be missing:

- **framework-provider-kubernetes-\***
- **framework-provider-azure-\***

## Configuration and environments

Booster uses sensible defaults, convention over configuration, and code inference to reduce dramatically the amount of configuration needed. However, there are some aspects that can't be inferred (like the application name) or the provider library used for each [environment](#environments).

### Booster configuration

You configure your application by calling the `Booster.configure()` method. There are no restrictions about where you should do this call, but the convention is to do it in your configuration files located in the `src/config` folder. This folder will get automatically generated for you after running the `boost new:project <project-name>` CLI command.

This is an example of a possible configuration:

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'

Booster.configure('pre-production', (config: BoosterConfig): void => {
  config.appName = 'my-app-name'
  config.providerPackage = '@boostercloud/framework-provider-aws'
})
```

The following is the list of the fields you can configure:

- **appName:** This is the name that identifies your application. It will be used for many things, such us prefixing the resources created by the provider. There are certain restrictions regarding the characters you can use: all of them must be lower-cased and can't contain spaces. Two apps with different names are completely independent.

- **providerPackage:** This field contains the name of the provider package that Booster will use when deploying or running your application.

-**enableGraphQLIntrospection** This field allows to enable/disable get information about the GraphQL schema of your application from client side. By default is enabled but it is recommended to disable for security reasons in production applications.

- **assets**: This is an array of _relative_ paths from the root of the project pointing to files and folders with static assets. They will be included among the deployed files to the cloud provider.
  For example, imagine you are using the `dotenv` module so that all the environment variables you have in your `.env` files are loaded into memory in runtime. In order for this to work, you need to include your `.env` files as assets of your project, so that they are included when deploying. Assuming you only have a `.env` file in the root of your project, you should add the following to your configuration:
  ```typescript
  config.assets = ['.env']
  ```

### Providers configuration

#### AWS Provider

To configure AWS as a provider you need to meet certain prerequisites:

- Set up an AWS account following the getting started section [instructions](#aws-provider-prerequisites)
- Check `@boostercloud/framework-provider-aws` is listed in your app `package.json` dependencies.
- Check `@boostercloud/framework-provider-aws-infrastructure` is listed in your app `package.json` devDependencies.
- Check both dependencies are installed, otherwise use `npm install` in the root of your project.

Now go to your `config.ts` file, import the aws provider library and set up your app environment.

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Provider as AWSProvider } from

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'my-app-name'
  config.providePackage = '@boostercloud/framework-provider-aws'
})
```

Open your terminal and run the deployment command

```sh
boost deploy -e production
```

Now just let the magic happen, Booster will create everything for you and give you back your app ready to use URL. 🚀

#### Azure Provider

To configure Azure as a provider you need to meet certain prerequisites:

- Set up an Azure subscription and install [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli?view=azure-cli-latest).
- Install [jq](https://stedolan.github.io/jq/download/) in your system.
- Check `@boostercloud/framework-provider-azure` is listed in your app `package.json` dependencies.
- Check `@boostercloud/framework-provider-azure-infrastructure` is listed in your app `package.json` devDependencies.
- Check both dependencies are installed, otherwise use `npm install` in the root of your project.

At this moment you have to log in you Azure account using the Azure CLI with the following command.

```bash
az login
```

Then create a service pricipal running the following command.

```bash
az ad sp create-for-rbac --name <service-principal-name>
```

:::note
Remember to change `<service-principal-name>` for a custom one.
:::

After the service principal is created, create a bash script with the following content. It will set up the necessary environment variables required by the provider in order to work:

```bash
#!/usr/bin/env bash

SP_DISPLAY_NAME="<service-principal-name>" #replace <service-principal-name> with the name of your own SP
REGION="East US" #replace with a region of your choice, see full list here: https://azure.microsoft.com/en-us/global-infrastructure/locations/

export AZURE_APP_ID=$(az ad sp list --display-name ${SP_DISPLAY_NAME} | jq -r '.[].appId')
export AZURE_TENANT_ID=$(az ad sp list --display-name ${SP_DISPLAY_NAME} | jq -r '.[].appOwnerTenantId')
export AZURE_SECRET=$(az ad sp credential reset --name ${AZURE_APP_ID} | jq -r '.password')
export AZURE_SUBSCRIPTION_ID=$(az account show | jq -r '.id')
export REGION
```

:::note
Remember to have [jq](https://stedolan.github.io/jq/download/) installed in your system.
:::

Now go to your `config.ts` file, import the aws provider library and set up your app environment.

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'my-app-name'
  config.providerPackage = '@boostercloud/framework-provider-azure'
})
```

Open your terminal and run the bash file to export you env variables and the deploy command

```bash
source <path-to-your-bash-file> && boost deploy -e production
```

Now just let the magic happen, Booster will create everything for you and give you back your app ready to use URL. 🚀

#### Azure synth command

Azure provider implement the experimental **Booster** `synth` command. This command will generate [Terrafom](https://www.terraform.io/) templates from your code. It will also generate needed files to deploy your Booster application using [cdktf](https://learn.hashicorp.com/tutorials/terraform/cdktf).

Running `synth` command, for example `boost synth -e production` will generate following files:

* A file `cdktf.json`: A basic json file to deploy your application using `cdktf`
* A folder `cdktf.out`: with the Terraform templates.

Booster deploy command for Azure will deploy your application using the generated templates.  You don't need to run the `synth` command for deploy your application, the `deploy` command will generate the templates before deploy for you.

Once you have the new files and folders generates you could use `cdktf` to deploy your application if you want to.

#### Azure and CI/CD environments

It is possible to deploy your Azure infrastructure using the [Terrafom](https://www.terraform.io/) templates generated by the `synth` command using Terraform executable.

To deploy a **Booster** application using the [Terrafom](https://www.terraform.io/) templates generated by the **Booster** `synth` command:

1. Navigate to
```shell
> cd cdktf.out/stacks/<application name><environment name>
```
2. Run (only the first time)
```shell
> terraform init
```
3. Run
```shell
> terraform plan --out plan
```
4. Run
```shell
> terrafom apply "plan"
```

You could follow similar steps to integrate the Azure **Booster** deploys in your CI/CD environment.

1. Navigate to
```shell
> cd cdktf.out/stacks/<application name><environment name>
```
2. Copy `functionApp.zip` to the destination folder
```shell
> cp functionApp.zip <destination>
```

After copying the files you should have the following structure:

```text
<application>
├── cdktf.out
│   └── stacks
│       └── <application name><environment name>
│           └── cdk.tf.json
```

Now deploy the template:

1. Run (only the first time)
```shell
> terraform init
```
2. Run
```shell
> terraform plan --out plan
```
3. Run
```shell
> terrafom apply "plan"
```

Finally, you need to upload the source code. The main options are ([more info](https://docs.microsoft.com/en-us/azure/azure-functions/deployment-zip-push)):

1. Using az-cli. Run
```shell
> az functionapp deployment source config-zip -g <resource_group> -n \
   <app_name> --src ./functionApp.json
```
2. Using REST APIs. Send a POST request to `https://<app_name>.scm.azurewebsites.net/api/zipdeploy`. Example:
```shell
>  curl -X POST -u <deployment_user> --data-binary @"<zip_file_path>" https://<app_name>.scm.azurewebsites.net/api/zipdeploy
```

:::note
Remember to follow the **Azure Provider** steps in this page to set up your credentials correctly
:::

#### Kubernetes provider

To configure Kubernetes as a provider you need to meet certain prerequisites:

- Config the Kubernetes cluster beforehand in a cloud provider or on-premises.
- Install **kubectl** and connect it to your Kubernetes cluster.
- Install [Helm](https://helm.sh) version 3 or greater.
- Check `@boostercloud/framework-provider-kubernetes` is listed in your app `package.json` dependencies.
- Check `@boostercloud/framework-provider-kubernetes-infrastructure` is listed in your app `package.json` devDependencies.
- Check both dependencies are installed, otherwise use `npm install` in the root of your project.

Now go to your `config.ts` file, import the kubernetes provider library and set up your app environment.

### Working with minikube

[Minikube](https://minikube.sigs.k8s.io/docs/) allows you to use a Kubeneter cluster on your local environment.

Once you have it installed, you just have to make sure that you configure the namespace for the current context. By default, the namespace has the following format: `booster-<app_name>-<environment_name>`. For example, if our app name were “myApp” and the environment “kubernetes-dev”, the namespace config command would be like:

`kubectl config set-context --current --namespace=booster-myApp-kubernetes-dev`

Ready! We can now boost deploy -e kubernetes-dev to deploy our application locally.

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterK8sConfiguration } from '@boostercloud/framework-provider-kubernetes-infrastructure'

Booster.configure('production', (config: BoosterK8sConfiguration): void => {
  config.appName = 'my-app-name'
  config.providerPackage = '@boostercloud/framework-provider-kubernetes'
})
```

### Environments

You can create multiple environments calling the `Booster.configure` function several times using different environment names as the first argument. You can create one file for each environment, but it is not required. In this example we set all environments in a single file:

```typescript
// Here we use a single file called src/config.ts, but you can use separate files for each environment too.
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
// A provider that deploys your app to AWS:

Booster.configure('stage', (config: BoosterConfig): void => {
  config.appName = 'fruit-store-stage'
  config.providerPackage = '@boostercloud/framework-provider-aws'
})

Booster.configure('prod', (config: BoosterConfig): void => {
  config.appName = 'fruit-store-prod'
  config.providerPackage = '@boostercloud/framework-provider-aws'
})
```

It is also possible to place an environment configuration in a separated file. Let's say that a developer called "John" created its own configuration file `src/config/john.ts`. The content would be the following:

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as AWS from

Booster.configure('john', (config: BoosterConfig): void => {
  config.appName = 'john-fruit-store'
  config.providerPackage = '@boostercloud/framework-provider-aws'
})
```

The environment name will be required by any command from the Booster CLI that depends on the provider. For instance, when you deploy your application, you'll need to specify on which environment you want to deploy it:

```sh
boost deploy -e prod
```

This way, you can have different configurations depending on your needs.

Booster environments are extremely flexible. As shown in the first example, your 'fruit-store' app can have three team-wide environments: 'dev', 'stage', and 'prod', each of them with different app names or providers, that are deployed by your CI/CD processes. Developers, like "John" in the second example, can create their own private environments in separate config files to test their changes in realistic environments before committing them. Likewise, CI/CD processes could generate separate production-like environments to test different branches to perform QA in separate environments without interferences from other features under test.

The only thing you need to do to deploy a whole new completely-independent copy of your application is to use a different name. Also, Booster uses the credentials available in the machine (`~/.aws/credentials` in AWS) that performs the deployment process, so developers can even work on separate accounts than production or staging environments.

## Extending Booster with Rockets!

You can extend Booster by creating rockets. A rocket is just a node package that implements the public Booster rocket interfaces. You can use them for many things:

1. Extend your infrastructure (Currently supported in AWS, and under experimental support in Azure and Local): You can write a rocket that adds provider resources to your application stack.
2. Runtime extensions (Not yet implemented): Add new annotations and interfaces, which combined with infrastructure extensions, could implement new abstractions on top of highly requested use cases.
3. Deploy and init hooks (Not yet implemented): Run custom scripts before or after deployment, or before a Booster application is loaded.

This extension mechanism is very new, but we're planning to port most of the functionality as rockets. This has two benefits:

- Composability: You can use the default rockets or configure your application to suit your needs without adding anything extra.
- Easier to manage feature sets in different providers: It would be really hard for the core team and contributors to implement and test every new feature in every supported provider, so by providing functionality like rockets, you'll have access to the most advanced features for your provider faster, and the rockets library can be built on-demand for each provider.

### Create your own Rocket

:::note
Currently Rockets work in AWS.
In Azure and Local, Rockets are under experimental support. We are working on porting them to other providers.
:::

A rocket is nothing more than an npm package that extends your current Booster architecture. The structure is simple, and it mainly has 2 methods: `mountStack` and `unmountStack`. We'll explain what they are shortly.

Rockets are usually composed of many packages, so we recommend using [Lerna](https://lerna.js.org) for development and publishing.

*Infrastructure Rocket* interfaces are provider-dependant, so *Infrastructure Rockets* must import the corresponding booster infrastructure package for their chosen provider. For AWS, that's `@boostercloud/framework-provider-aws-infrastructure`. Notice that, as the only thing we use of that package is the `InfrastructureRocket` interface, you can import it as a dev dependency to avoid including that big package in your deployed lambdas.

So let's start by creating a new package and adding this dependency:`

```sh
mkdir rocket-your-rocket-name-aws-infrastructure
cd rocket-your-rocket-name-aws-infrastructure
npm init
...
npm install --save @boostercloud/framework-provider-aws-infrastructure
```

The basic structure of an *Infrastructure Rocket* project is quite simple as you can see here:

```text
rocket-your-rocket-name-aws-infrastructure
├── package.json
├── src
    ├── index.ts
    └── your-main-class.ts

```

`<your-main-class>.ts`  can be named as you want and this is where we define the mountStack and unmount methods.

```typescript
export class YourMainClass {
  public static mountStack(params: YourRocketParams, stack: Stack, config: BoosterConfig): void {
    /* CDK code to expand your Booster infrastructure */
  }
  public static unmountStack?(params: YourRocketParams, utils: RocketUtils): void {
    /* Optional code that runs before removing the stack */
  }
}
```

Let's look in more detail these two special functions:

- **mountStack**: Whenever we are deploying our Booster application (`boost deploy`) this method will also be run.  It receives three params:
  - `params`: The parameters required by your *Infrastructure Rocket* initializator, you will receive them from your Booster app's `config.ts` file.
  - `stack`: An initialized AWS CDK stack that you can use to add new resources. Check out [the Stack API in the official CDK documentation](https://docs.aws.amazon.com/cdk/latest/guide/stacks.html#stack_api). This is the same stack instance that Booster uses to deploy its resources, so your resources will automatically be deployed along with the Booster's ones on the same stack.
  - `config`: It includes properties of the Booster project (e.g. project name) that come in handy for your rocket.

- **unmountStack**: It will run when you run the `boost nuke` command. When you nuke your Booster application, all the resources added by your rocket are automatically destroyed along with the application stack, but there are some situations where you might or need to specify any additional step in the deletion process. The `unmountStack` function will run the code you intend for this purpose. For instance, in AWS, before destroying your stack (where you have some S3 buckets) you need to first empty them in order to delete them. You can accomplish this action in the `unmountStack` method.

We also have an index.ts file to export these two functions:
```typescript
export interface InfrastructureRocket {
  mountStack: (stack: Stack, config: BoosterConfig) => void
  unmountStack?: (utils: RocketUtils) => void
}
```

You'll have to implement a default exported function that accepts a parameters object and returns an initialized `InfrastructureRocket` object:

```typescript
const YourRocketInitializator = (params: YourRocketParams): InfrastructureRocket => ({
  mountStack: SomePrivateObject.mountStack.bind(null, params),
  unmountStack: SomePrivateObject.unmountStack.bind(null, params),
})

export default YourRocketInitializator
```

Notice that *Infrastructure Rockets* should not be included in the Booster application code to avoid including the CDK and other unused dependencies in the lambdas, as there are some strict restrictions on code size on most platforms. That's why *Infrastructure Rockets* are dynamically loaded by Booster passing the package names as strings in the application config file:

_src/config/production.ts:_

```typescript
Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.providerPackage = '@boostercloud/framework-provider-aws'
  config.rockets = [
    {
      packageName: 'rocket-your-rocket-name-aws-infrastructure', // The name of your infrastructure rocket package
      parameters: {
        // An arbitrary object with the parameters required by your infrastructure rocket initializator
        hello: 'world',
      },
    },
  ]
})
```

### Naming recommendations

There are no restrictions on how you name your rocket packages, but we propose the following naming convention to make it easier to find your extensions in the vast npm library and find related packages (code and infrastructure extensions cannot be distributed in the same package).

- `rocket-{rocket-name}-{provider}`: A rocket that adds runtime functionality or init scripts. This code will be deployed along with your application code to the lambdas.
- `rocket-{rocket-name}-{provider}-infrastructure`: A rocket that provides infrastructure extensions or implements deploy hooks. This code will only be used on developer's or CI/CD systems machines and won't be deployed to lambda with the rest of the application code.

Notice that some functionalities, for instance an S3 uploader, might require both runtime and infrastructure extensions. In these cases, the convention is to use the same name `rocket-name` and add the suffix `-infrastructure` to the infrastructure rocket. It's recommended, but not required, to manage these dependent packages in a monorepo and ensure that the versions match on each release.

If you want to support the same functionality in several providers, it could be handy to also have a package named `rocket-{rocket-name}-{provider}-core` where you can have cross-provider code that you can use from all the provider-specific implementations. For instance, a file uploader rocket that supports both AWS and Azure could have an structure like this:

- `rocket-file-uploader-core`: Defines abstract decorators and interfaces to handle uploaded files.
- `rocket-file-uploader-aws`: Implements the API calls to S3 to get the uploaded files.
- `rocket-file-uploader-aws-infrastructure`: Adds a dedicated S3 bucket.
- `rocket-file-uploader-azure`: Implements the API calls to Azure Storage to get the uploaded files.
- `rocket-file-uploader-azure-infrastructure`: Configures file storage.

### How to publish a Rocket

1. Upload your rocket to a git repository.
2. Run :
    ```shell
        > lerna bootstrap
    ```
    This will bootstrap the packages, install all their dependencies and link any cross-dependencies.
3. Ensure you have the option `publishConfig.access` set to `public` in the `package.json` file of every package.
4. Make sure you are logged into NPM by running:
   ```shell
        > npm login
    ```
5. Run :
    ```shell
        > lerna publish
    ```
    Creates a new release of the packages that have been updated. It will update all the packages in git and npm, and it will prompt for a new version of the rocket package.
6. If the previous step finished successfully, the package will be available in NPM.

### Booster Rockets list

Here you can check out the official Booster Rockets developed at this time:

- [Authentication Booster Rocket for AWS](https://github.com/boostercloud/rocket-auth-aws-infrastructure)
- [Backup Booster Rocket for AWS](https://github.com/boostercloud/rocket-backup-aws-infrastructure)
- [Static Sites Booster Rocket for AWS](https://github.com/boostercloud/rocket-static-sites-aws-infrastructure)
- [Webhook Booster Rocket for Azure and Local](https://github.com/boostercloud/rocket-webhook)

## Customizing CLI resource templates

You can change what the newly created Booster resources will contain by customizing the resource template files.

To do this, you first need to publish the resource templates by running the `boost stub:publish` command. This will create a folder `stubs` in the root directory of the project, and it will contain all the resources that you can customize:

```
stubs/
├─ command.stub
├─ entity.stub
├─ event.stub
├─ event-handler.stub
├─ read-model.stub
├─ scheduled-command.stub
└─ type.stub
```

After that, Booster CLI will start using your local templates instead of the default ones.
Let's try this by adding a simple comment to the `type.stub` file.

```
// Look I am a comment that will now appear in every new type file 🐙
export class {{{ name }}} {
  public constructor(
    {{#fields}}
    public {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}
}
```

Now if you run `boost new:type CartItem --fields sku:string` command, you will get `common/cart-item.ts` file with following content:
```typescript
// Look I am a comment that will now appear in every new type file 🐙
export class CartItem {
  public constructor(
      public sku: string,
  ) {}
}
```

You did it, we just updated our resource template file! Now when you run `boost new:type', it will contain the comment you added earlier 🚀
Of course, this is a simple example, and you may want to add new methods, import something, you name it!

Here are some answers to questions you may have:

#### QA
<details>
    <summary>Can I have only one stub for a certain resource?</summary>

    Yes! The resource generator will check if you have a custom template or it will use the default template
</details>

<details>
    <summary>How can I keep up with new template updates?</summary>

    1. Run `boost stub:publish --force` command
    2. Review changes
    3. Done!
</details>

<details>
    <summary>Can I adjust the command template and leave the other resources as they are?</summary>

    Yes. You can only have the `command.stub` file in the `/stubs` folder and customize it.
    The generator will use the default templates for the other resources.
</details>

<details>
    <summary>How can I use the default templates again!?</summary>

    Simply delete the `/stubs` folder or a specific resource file.
</details>

<details>
    <summary>What are these strange name, #fields, etc. things????</summary>

    These are the variables and sections used by the mustache.js templating engine.
    They allow us to dynamically generate new resources.
</details>

<details>
    <summary>How do I change what `new:project` command generates?</summary>

    At the moment there is no way to do this.
    But in the future we will move the new project template from the CLI package( https://github.com/boostercloud/booster/issues/1078 ), and then you will be able to create and use your own templates for new projects.
</details>

<details>
    <summary>I have another question!</summary>

    You can ask questions on our Discord channel or create discussion on Github.
</details>

## Migration from Previous Versions

* To migrate to new versions of Booster, check that you have the latest development dependencies required:

```json
"devDependencies": {
    "rimraf": "^3.0.1",
    "@typescript-eslint/eslint-plugin": "4.22.1",
    "@typescript-eslint/parser": "4.22.1",
    "eslint": "7.26.0",
    "eslint-config-prettier": "8.3.0",
    "eslint-plugin-prettier": "3.4.0",
    "mocha": "8.4.0",
    "@types/mocha": "8.2.2",
    "nyc": "15.1.0",
    "prettier":  "2.3.0",
    "typescript": "4.5.4",
    "ts-node": "9.1.1",
    "@types/node": "15.0.2",
    "ttypescript": "1.5.13",
    "@boostercloud/metadata-booster": "0.30.2"
  },
```
