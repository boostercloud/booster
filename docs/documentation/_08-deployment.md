# Deploying

One of the goals of Booster is to become provider agnostic so you can deploy your application to any serverless provider like AWS, Google Cloud, Azure, etc...

So far, in the current version, only AWS is supported, but given the high level of abstraction, it will eventually support
all cloud providers. (**Contributions are welcome!** 😜)

## Configure your provider credentials


In the case of AWS, it is required that your `~/.aws/credentials` are properly setup, and a `region` attribute is specified. If you have the [AWS CLI installed](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html), you can create the config file by running the command `aws configure`, but that is completely optional, **AWS CLI is not required to run booster**. Creating a plain text file manually named `~/.aws/credentials` with the following content will be enough:

```shell script
[default]
aws_access_key_id = <YOUR KEY ID>
aws_secret_access_key = <YOUR ACCESS KEY>
region = eu-west-1
```

It's recomended to use IAM user keys and avoiding your root access keys. If you need help obtaining a `KEY ID` and `ACCESS KEY`, [check out the oficial AWS guides](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_CreateAccessKey).

## Deploy your project

To deploy your Booster project, run the following command:

```shell script
boost deploy
```

It will take a while, but you should have your project deployed to your cloud provider.

If you make changes to your code, you can run `boost deploy` again to update your project in the cloud.

## Deleting your cloud stack

If you want to delete the Booster application that has been deployed to the cloud, you can run:

```shell script
boost nuke
```

> **Note**: This will delete everything in your stack, including databases. This action is **not** reversible!

After deploying your application, check out [the API documentation to start using it](_09rest-api.md)!
