# Booster Documentation

## Introduction

### What is Booster?

### Booster Principles

### Why using Booster

## Getting started

### Installing Booster

You can develop with Booster using any of the following operating systems:

- Linux
- MacOS
- Windows (Native and WSL)

Booster hasn't been tested under other platforms like BSD, by using them you may face unknown issues so proceed at your own risk.

#### Prerequisites

##### Install Node.js

The minimal required Node.js version is `v12`. Download the installer [from it's website](https://nodejs.org/en/), or install it using the system's package manager.

###### Ubuntu

```shell
curl -sL https://deb.nodesource.com/setup_14.x | sudo -E bash -
sudo apt install nodejs
```

###### macOS

```shell
brew install node
```

###### Windows

```shell
choco install nodejs
```

Verify that it was installed properly by checking so from your terminal:

```shell
$ node -v
v13.12.0

$ npm -v
6.14.4
```

As soon as you have a Node.js version higher than `v12`, and an `npm` version higher than `6`, you are good to go. Just note that `npm` comes with node, you don't have to install it apart.

Alternatively, we recommend you to use a version manager for dealing with different Node.js versions

- [`nvm`](https://github.com/nvm-sh/nvm) - Works with MacOS, Linux and WSL
- [`nvm-windows`](https://github.com/coreybutler/nvm-windows) - Works with native Windows

##### Set up an AWS account

This step is optional, Booster is a cloud-native framework, meaning that your application
will be deployed to the cloud using different cloud providers. By default, Booster uses the
[AWS Provider](framework-providers-aws) so an AWS account is needed. You can always omit
this step if you only want to get a grip of Booster or test it locally without making a
deployment.

Note:

> Booster is, and will always be, free but the resources used by the cloud providers are
> not. All the resources used by the AWS Provider are part of the
> [AWS free tier](https://aws.amazon.com/free). Even if you are not eligible for it,
> you can still test your app and it shouldn't cost more than a few cents. Still,
> **we recommend you to un-deploy your application after finishing the tests if you don't
> plan to use it anymore**.

Now it is a good time to create that AWS account, you can do so from
[the AWS console registration](https://portal.aws.amazon.com/billing/signup).

Once you've registered yourself, you have to generate an access key for Booster. To do so,
login into the [AWS Console](https://console.aws.amazon.com), and click on your account
name on the top-right corner.

![aws account menu location](./img/aws-account-menu.png)

A menu will open, click on **My security credentials** and it will take you to the
Identity and Access Management panel. Once there, create an access key:

![create access key button location](./img/aws-create-access-key.png)

A pop-up will appear, **don't close it!**. Create a folder called `.aws` under your home
folder, and a file called `credentials` with this template:

```ini
# ~/.aws/credentials
[default]
aws_access_key_id = <YOUR ACCESS KEY ID>
aws_secret_access_key = <YOUR SECRET ACCESS KEY>
```
#### Installing the Booster CLI

Booster comes with a command-line tool that helps you generating boilerplate code,
testing and deploying the application, and deleting all the resources in the cloud. All
the stable versions are published to [`npm`](https://www.npmjs.com/package/@boostercloud/cli),
these versions are the recommended ones, as they are well documented, and the changes are
stated in the release notes.

To install the Booster CLI run

```shell
npm install --global @boostercloud/cli
```

Verify the Booster CLI installation with the `boost version` command. You should get back
something like

```shell
$ boost version
@boostercloud/cli/0.3.3 darwin-x64 node-v13.12.0
```
