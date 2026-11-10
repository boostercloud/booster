# project_name_placeholder

description_placeholder

A [Booster](https://boosterframework.com) application written in Java.

## Requirements

- JDK 27 or newer
- Maven 3.9 or newer
- The `boost` command line tool (Booster CLI booster_version_placeholder)

## Project structure

```
src/main/java/com/example/base_package_placeholder/
├─ Main.java            starts the application (Booster.run)
├─ config/              environments (Booster.configure)
├─ commands/            boost new:command
├─ events/              boost new:event
├─ entities/            boost new:entity
├─ readmodels/          boost new:read-model
├─ eventhandlers/       boost new:event-handler
├─ queries/             boost new:query
├─ scheduledcommands/   boost new:scheduled-command
└─ common/              boost new:type
```

## Running locally

```bash
boost start -e local            # GraphQL API on http://localhost:3000/graphql
boost start -e local --port 8080
```

## Generating resources

```bash
boost new:command CreateProduct --fields sku:string price:number
boost new:event ProductCreated --fields productId:UUID sku:string price:number
boost new:entity Product --fields sku:string price:number --reduces ProductCreated
boost new:read-model ProductReadModel --fields sku:string price:number --projects Product:id
boost add:reducer --entity Product --event ProductUpdated
```

## Deploying

```bash
boost deploy -e production
boost nuke -e production         # removes every resource of the environment
```
