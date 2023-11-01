# Metadata Booster

This is a transformer (also known as plugin) for Typescript to generate detailed metadata for all your classes.

## Why?

There are many applications that need to know the schema of your classes to work properly, like ORMs (to know the names of the database tables and columns), GraphQL APIs (to generate the GraphQL schema), etc.

Currently, Typescript emits some metadata when you add decorators to your classes and enable the following compiler options: `"experimentalDecorators": true` and `"emitDecoratorMetadata": true`.

However, that metadata is very limited, as it doesn't include property names and information about the type parameters.

For example, if we had the following code:

```typescript
@AnyDecorator //We need to decorate the class to emit metadata
class User {
    constructor(
        public name: string,
        public friends: Set<User>
    ) {}
}

enum Size {
    Small,
    Medium,
    Big,
}
@AnyDecorator //We need to decorate the class to emit metadata
class Car {
    constructor(
        public size: Size,
        public driversByName: Map<string, User>
    ) {}

    public engageAutoPilot(): Promise<boolean> {
        // Asume a log task here
        return Promise.resolve(true)
    }
}
```

And now we call `Reflect.getMetadata('design:paramtypes')` on each class, we would get the following:

```typescript
Reflect.getMetadata('design:paramtypes', User)
> [ [Function: String], [Function: Set] ]

Reflect.getMetadata('design:paramtypes', Car)
> [ [Function: Number], [Function: Map] ]
```

Not very helpful: we are lacking a lot of information about property names, type parameters and methods.

## Welcome to full detailed metadata

With this transformer, you will get much more detailed metadata for all your classes, without the need of using any decorator.

Following with the previous example, you can get this detailed metadata using the key `'booster:typeinfo'`:

```typescript
Reflect.getMetadata('booster:typeinfo', User)
> {
    name: 'User',
    type: [Function: User],
    fields: [
        {
            name: 'name',
            typeInfo: {
                type: [Function: String],
                parameters: []
            }
        },{
            name: 'friends',
            typeInfo: {
                type: [Function: Set],
                parameters: [
                    {
                        type: [Function: User],
                        parameters: []
                    }
                ]
            }
        }
    ],
    methods: []
}

Reflect.getMetadata('booster:typeinfo', Car)
> {
    name: 'Car',
    type: [Function: Car],
    fields: [
        {
            name: 'size',
            typeInfo: {
                type: {
                    '0': 'Small',
                    '1': 'Medium',
                    '2': 'Big',
                    Small: 0,
                    Medium: 1,
                    Big: 2
                },
                parameters: []
            }
        },
        {
            name: 'driversByName',
            typeInfo: {
                type: [Function: Map],
                parameters: [
                    {
                        type: [Function: String],
                        parameters: []
                    },{
                        type: [Function: User],
                        parameters: []
                    }
                ]
            }
        }
    ],
    methods: [
        {
            name: 'engageAutoPilot',
            typeInfo: {
                type: [Function: Promise],
                parameters: [
                    {
                        type: [Function: Boolean],
                        parameters: []
                    }
                ]
            }
        }
    ]
}
```

As you can see, you can now have runtime access to the information about all the properties, type parameters, methods, return types, etc. of your classes.

## How to use it

"@boostercloud/metadata-booster" is a transformer so, until the Typescript team decides to accept plugins (you can track the status in [this issue](https://github.com/microsoft/TypeScript/issues/14419)), you would want to use [TS Patch](https://github.com/nonara/ts-patch).

Here are the steps:

1. Add the "@boostercloud/metadata-booster" transformer and "ts-patch" to your `"devDependencies"`

    ```shell
    npm install --save-dev "@boostercloud/metadata-booster"
    npm install --save-dev "ts-patch"
    ```

2. Add the official module "reflect-metadata" to your `"dependencies"` (you need this to access the metadata)

    ```shell
    npm install --save-prod "reflect-metadata"
    ```

3. Go to your `tsconfig.json` file and
   - a) Ensure you have the option `"experimentalDecorators": true`. The reason is that the metadata is automatically added as a decorator to the class. In any case, you don't need to write any decorator.
   - b) Add "@boostercloud/metadata-booster" as a transformer plugin inside the `"compilerOptions"` section

    ```shell
    {
    "compilerOptions": {
        (...)
        "experimentalDecorators": true
        "plugins": [
        { "transform": "@boostercloud/metadata-booster"}
        ]
    },
    }
    ```

4. Create a "prepare" script to patch your typescript installation automatically after installing your dependencies. You can also add a convenient "build" script in your "package.json" file to compile your code by running `npm run build` instead of calling the compiler directly:

    ```json
    ... other "package.json" options fields ...
    "scripts": {
        "prepare": "ts-patch install -s",
        "build": "tsc -b tsconfig.json",
        "test": "..."
    }
    ```

Now you can compile your project by running `npm run build` and have access to a full detailed metadata for all your Typescript classes.

## Compatibility

This transformer is compatible with Typescript version 4.x.x

## Missing features

- [ ] Gather interfaces metadata
- [ ] Gather method parameters metadata
