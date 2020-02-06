# Booster Cloud Framework REST API

The API for a Booster application is very simple and is fully defined by the [commands](03-commands.md) and [read models](06-read-models.md) names and structures.

After a successful deployment you'll see a `baseURL` that you can use to perform the following requests:

## Auth API

- [ ] TODO: Write the Auth API documentation with examples

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
    "sku": "asdf465",
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
