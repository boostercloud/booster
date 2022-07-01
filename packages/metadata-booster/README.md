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
"metadata-booster" is a transformer so, until the Typescript team decides to accept plugins (you can track the status in [this issue](https://github.com/microsoft/TypeScript/issues/14419)), you would want to use the [Typescript](https://github.com/cevek/ttypescript) wrapper "TTypescript" to be able to use any transformer. That being said, it works really well.

Here are the steps:
1. Add the "metadata-booster" transformer and "ttypescript" to your `"devDependencies"`
```shell
npm install --save-dev "metadata-booster"
npm install --save-dev "ttypescript"
```
2. Add the official module "reflect-metadata" to your `"dependencies"` (you need this to access the metadata)
```shell
npm install --save-prod "reflect-metadata"
```
3. Go to your `tsconfig.json` file and 
   - a) Ensure you have the option `"experimentalDecorators": true`. The reason is that the metadata is automatically added as a decorator to the class. In any case, you don't need to write any decorator.
   - b) Add "metadata-booster" as a transformer plugin inside the `"compilerOptions"` section
```shell
{
  "compilerOptions": {
    (...)
    "experimentalDecorators": true
    "plugins": [
      { "transform": "metadata-booster"}
    ]
  },
}
```
4. _[Optional]_ From now on, to compile your code you need to use the command `ttsc` (the TTypescript wrapper), instead of `tsc`. I normally have a "compile" script in my "package.json" file that calls `"tsc -b tsconfig.json"`, so I compile my code by running `npm run compile`. I would recommend you to do that and change "tsc" by "ttsc". Like this:
```json
  ... other "package.json" options fields ...
  "scripts": {
    "compile": "ttsc -b tsconfig.json",
    "test": "..."
  }
```

Now you can compile your project by running `npm run compile` and have access to a full detailed metadata for all your Typescript classes.

## Compatibility
This transformer is compatible with Typescript version 4.x.x

## Missing features
- [ ] Gather interfaces metadata
- [ ] Gather method parameters metadata



