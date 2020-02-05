# Booster Cloud Framework REST API

The API for a Booster application is very simple and is fully defined by auth endpoints and the [commands](03-commands.md)
and [read models](06-read-models.md) names and structures.

After a successful deployment you'll see an "Outputs:" section in your terminal with several values that you need to use 
when doing requests to the API. Those values are:

- `baseURL`: This is the base URL for all your endpoints
- `clientID`: Needed for authentication/authorization endpoints. This is only shown if there are roles defined in your app.

The only remaining thing you need to know is that the `Content-Type` for all requests is `application/json`.

## Authentication and Authorization API

The following endpoints are provisioned if your application have at least one role defined. For more information about how 
to use roles to restrict the access to your application, see the section [Authentication and Authorization](07-auth.md).

#### Sign-up. Register a user in your application
###### Endpoint
```http request
POST https://<baseURL>/auth/sign-up
```
###### Request body
```json
{
	"clientId": "string",
	"username": "string",
	"password": "string",
	"userAttributes": {
   		"roles": ["string"]
	}
}
```
- _clientId_: The application client Id that you got as an output when the application was deployed.
- _username_: The username of the user you want to register. It **must be an email**.
- _password_: The password the user will use to later login into your application and get access tokens.
- _userAttributes_: Here you can specify the attributes of your user. These are:
    - _roles_: An array of roles this user will have. You can only specify here roles with the property `allowSelfSignUp = true` 

## Write API (commands submission)

- [ ] TODO: Improve this documentation

`POST https://<baseURL>/commands`
Body:
```json
{
  "typeName": "ChangeCartItem",
  "version": 1,
  "value": {
    "cartId": "demo",
    "sku": "ABC-10",
    "quantity": 1
  }
}
```

## Read API (retrieve a read model)

- [ ] Improve this documentation

### Get a list

`GET https://<baseURL>/readmodels/<read model class name>`

Example:

`GET https://<baseURL>/readmodels/CartReadModel`

### Get a specific read model

`GET https://<baseURL>/readmodels/<read model class name>/<read model ID>`

Example:

`GET https://<baseURL>/readmodels/CartReadModel/42`
