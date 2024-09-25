"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[5263],{5298:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>d,contentTitle:()=>r,default:()=>h,frontMatter:()=>i,metadata:()=>s,toc:()=>c});var o=n(5893),a=n(1151);const i={description:"Learn how to migrate data in Booster"},r="Migrations",s={id:"going-deeper/data-migrations",title:"Migrations",description:"Learn how to migrate data in Booster",source:"@site/docs/10_going-deeper/data-migrations.md",sourceDirName:"10_going-deeper",slug:"/going-deeper/data-migrations",permalink:"/going-deeper/data-migrations",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/data-migrations.md",tags:[],version:"current",lastUpdatedBy:"Mario Castro Squella",lastUpdatedAt:1727285420,formattedLastUpdatedAt:"Sep 25, 2024",frontMatter:{description:"Learn how to migrate data in Booster"},sidebar:"docs",previous:{title:"Testing",permalink:"/going-deeper/testing"},next:{title:"TouchEntities",permalink:"/going-deeper/touch-entities"}},d={},c=[{value:"Schema migrations",id:"schema-migrations",level:2},{value:"Data migrations",id:"data-migrations",level:2},{value:"Migrate to Booster version 1.19.0",id:"migrate-to-booster-version-1190",level:2},{value:"Migrate to Booster version 2.3.0",id:"migrate-to-booster-version-230",level:2},{value:"Migrate to Booster version 2.6.0",id:"migrate-to-booster-version-260",level:2}];function l(e){const t={code:"code",h1:"h1",h2:"h2",li:"li",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,a.a)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsx)(t.h1,{id:"migrations",children:"Migrations"}),"\n",(0,o.jsx)(t.p,{children:"Migrations are a mechanism for updating or transforming the schemas of events and entities as your system evolves. This allows you to make changes to your data model without losing or corrupting existing data. There are two types of migration tools available in Booster: schema migrations and data migrations."}),"\n",(0,o.jsxs)(t.ul,{children:["\n",(0,o.jsxs)(t.li,{children:["\n",(0,o.jsxs)(t.p,{children:[(0,o.jsx)(t.strong,{children:"Schema migrations"})," are used to incrementally upgrade an event or entity from a past version to the next. They are applied lazily, meaning that they are performed on-the-fly whenever an event or entity is loaded. This allows you to make changes to your data model without having to manually update all existing artifacts, and makes it possible to apply changes without running lenghty migration processes."]}),"\n"]}),"\n",(0,o.jsxs)(t.li,{children:["\n",(0,o.jsxs)(t.p,{children:[(0,o.jsx)(t.strong,{children:"Data migrations"}),", on the other hand, behave as background processes that can actively change the existing values in the database for existing entities and read models. They are particularly useful for data migrations that cannot be performed automatically with schema migrations, or for updating existing read models after a schema change."]}),"\n"]}),"\n"]}),"\n",(0,o.jsx)(t.p,{children:"Together, schema and data migrations provide a flexible and powerful toolset for managing the evolution of your data model over time."}),"\n",(0,o.jsx)(t.h2,{id:"schema-migrations",children:"Schema migrations"}),"\n",(0,o.jsxs)(t.p,{children:["Booster handles classes annotated with ",(0,o.jsx)(t.code,{children:"@SchemaMigration"})," as ",(0,o.jsx)(t.strong,{children:"schema migrations"}),". The migration functions defined inside will update an existing artifact (either an event or an entity) from a previous version to a newer one whenever that artifact is visited. Schema migrations are applied to events and entities lazyly, meaning that they are only applied when the event or entity is loaded. This ensures that the migration process is non-disruptive and does not affect the performance of your system. Schema migrations are also performed on-the-fly and the results are not written back to the database, as events are not revisited once the next snapshot is written in the database."]}),"\n",(0,o.jsxs)(t.p,{children:["For example, to upgrade a ",(0,o.jsx)(t.code,{children:"Product"})," entity from version 1 to version 2, you can write the following migration class:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"@SchemaMigration(Product)\nexport class ProductMigration {\n  @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })\n  public async changeNameFieldToDisplayName(old: ProductV1): Promise<ProductV2> {\n    return new ProductV2(\n      old.id,\n      old.sku,\n      old.name,\n      old.description,\n      old.price,\n      old.pictures,\n      old.deleted\n    )\n  }\n}\n"})}),"\n",(0,o.jsxs)(t.p,{children:["Notice that we've used the ",(0,o.jsx)(t.code,{children:"@ToVersion"})," decorator in the above example. This decorator not only tells Booster what schema upgrade this migration performs, it also informs it about the existence of a version, which is always an integer number. Booster will always use the latest version known to tag newly created artifacts, defaulting to 1 when no migrations are defined. This ensures that the schema of newly created events and entities is up-to-date and that they can be migrated as needed in the future."]}),"\n",(0,o.jsxs)(t.p,{children:["The ",(0,o.jsx)(t.code,{children:"@ToVersion"})," decorator takes two parameters in addition to the version: ",(0,o.jsx)(t.code,{children:"fromSchema"})," and ",(0,o.jsx)(t.code,{children:"toSchema"}),". The fromSchema parameter is set to ",(0,o.jsx)(t.code,{children:"ProductV1"}),", while the ",(0,o.jsx)(t.code,{children:"toSchema"})," parameter is set to ",(0,o.jsx)(t.code,{children:"ProductV2"}),". This tells Booster that the migration is updating the ",(0,o.jsx)(t.code,{children:"Product"})," object from version 1 (as defined by the ",(0,o.jsx)(t.code,{children:"ProductV1"})," schema) to version 2 (as defined by the ",(0,o.jsx)(t.code,{children:"ProductV2"})," schema)."]}),"\n",(0,o.jsxs)(t.p,{children:["As Booster can easily read the structure of your classes, the schemas are described as plain classes that you can maintain as part of your code. The ",(0,o.jsx)(t.code,{children:"ProductV1"})," class represents the schema of the previous version of the ",(0,o.jsx)(t.code,{children:"Product"})," object with the properties and structure of the ",(0,o.jsx)(t.code,{children:"Product"})," object as it was defined in version 1. The ",(0,o.jsx)(t.code,{children:"ProductV2"})," class is an alias for the latest version of the Product object. You can use the ",(0,o.jsx)(t.code,{children:"Product"})," class here, there's no difference, but it's a good practice to create an alias for clarity."]}),"\n",(0,o.jsxs)(t.p,{children:["It's a good practice to define the schema classes (",(0,o.jsx)(t.code,{children:"ProductV1"})," and ",(0,o.jsx)(t.code,{children:"ProductV2"}),") as non-exported classes in the same migration file. This allows you to see the changes made between versions and helps to understand how the migration works:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"class ProductV1 {\n  public constructor(\n    public id: UUID,\n    readonly sku: string,\n    readonly name: string,\n    readonly description: string,\n    readonly price: Money,\n    readonly pictures: Array<Picture>,\n    public deleted: boolean = false\n  ) {}\n}\n\nclass ProductV2 extends Product {}\n"})}),"\n",(0,o.jsxs)(t.p,{children:["When you want to upgrade your artifacts from V2 to V3, you can add a new function decorated with ",(0,o.jsx)(t.code,{children:"@ToVersion"})," to the same migrations class. You're free to structure the code the way you want, but we recommend keeping all migrations for the same artifact in the same migration class. For instance:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"@SchemaMigration(Product)\nexport class ProductMigration {\n  @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })\n  public async changeNameFieldToDisplayName(old: ProductV1): Promise<ProductV2> {\n    return new ProductV2(\n      old.id,\n      old.sku,\n      old.name, // It's now called `displayName`\n      old.description,\n      old.price,\n      old.pictures,\n      old.deleted\n    )\n  }\n\n  @ToVersion(3, { fromSchema: ProductV2, toSchema: ProductV3 })\n  public async addNewField(old: ProductV2): Promise<ProductV3> {\n    return new ProductV3(\n      old.id,\n      old.sku,\n      old.displayName,\n      old.description,\n      old.price,\n      old.pictures,\n      old.deleted,\n      42 // We set a default value to initialize this field\n    )\n  }\n}\n"})}),"\n",(0,o.jsxs)(t.p,{children:["In this example, the ",(0,o.jsx)(t.code,{children:"changeNameFieldToDisplayName"})," function updates the ",(0,o.jsx)(t.code,{children:"Product"})," entity from version 1 to version 2 by renaming the ",(0,o.jsx)(t.code,{children:"name"})," field to ",(0,o.jsx)(t.code,{children:"displayName"}),". Then, ",(0,o.jsx)(t.code,{children:"addNewField"})," function updates the ",(0,o.jsx)(t.code,{children:"Product"})," entity from version 2 to version 3 by adding a new field called ",(0,o.jsx)(t.code,{children:"newField"})," to the entity's schema. Notice that at this point, your database could have snapshots set as v1, v2, or v3, so while it might be tempting to redefine the original migration to keep a single 1-to-3 migration, it's usually a good idea to keep the intermediate steps. This way Booster will be able to handle any scenario."]}),"\n",(0,o.jsx)(t.h2,{id:"data-migrations",children:"Data migrations"}),"\n",(0,o.jsx)(t.p,{children:"Data migrations can be seen as background processes that can actively update the values of existing entities and read models in the database. They can be useful to perform data migrations that cannot be handled with schema migrations, for example when you need to update the values exposed by the GraphQL API, or to initialize new read models that are projections of previously existing entities."}),"\n",(0,o.jsxs)(t.p,{children:["To create a data migration in Booster, you can use the ",(0,o.jsx)(t.code,{children:"@DataMigration"})," decorator on a class that implements a ",(0,o.jsx)(t.code,{children:"start"})," method. The ",(0,o.jsx)(t.code,{children:"@DataMigration"})," decorator takes an object with a single parameter, ",(0,o.jsx)(t.code,{children:"order"}),", which specifies the order in which the data migration should be run relative to other data migrations."]}),"\n",(0,o.jsxs)(t.p,{children:["Data migrations are not run automatically, you need to invoke the ",(0,o.jsx)(t.code,{children:"BoosterDataMigrations.run()"})," method from an event handler or a command. This will emit a ",(0,o.jsx)(t.code,{children:"BoosterDataMigrationStarted"})," event, which will make Booster check for any pending migrations and run them in the specified order. A common pattern to be able to run migrations on demand is to add a special command, with access limited to an administrator role which calls this function."]}),"\n",(0,o.jsxs)(t.p,{children:["Take into account that, depending on your cloud provider implementation, data migrations are executed in the context of a lambda or function app, so it's advisable to design these functions in a way that allow to re-run them in case of failures (i.e. lambda timeouts). In order to tell Booster that your migration has been applied successfully, at the end of each ",(0,o.jsx)(t.code,{children:"DataMigration.start"})," method, you must emit a ",(0,o.jsx)(t.code,{children:"BoosterDataMigrationFinished"})," event manually."]}),"\n",(0,o.jsxs)(t.p,{children:["Inside your ",(0,o.jsx)(t.code,{children:"@DataMigration"})," classes, you can use the ",(0,o.jsx)(t.code,{children:"BoosterDataMigrations.migrateEntity"})," method to update the data for a specific entity. This method takes the old entity name, the old entity ID, and the new entity data as arguments. It will also generate an internal ",(0,o.jsx)(t.code,{children:"BoosterEntityMigrated"})," event before performing the migration."]}),"\n",(0,o.jsx)(t.p,{children:(0,o.jsx)(t.strong,{children:"Note that Data migrations are only available in the Azure provider at the moment."})}),"\n",(0,o.jsxs)(t.p,{children:["Here is an example of how you might use the ",(0,o.jsx)(t.code,{children:"@DataMigration"})," decorator and the ",(0,o.jsx)(t.code,{children:"Booster.migrateEntity"})," method to update the quantity of the first item in a cart (",(0,o.jsxs)(t.strong,{children:["Notice that at the time of writing this document, the method ",(0,o.jsx)(t.code,{children:"Booster.entitiesIDs"})," used in the following example is only available in the Azure provider, so you may need to approach the migration differently in AWS."]}),"):"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"@DataMigration({\n  order: 2,\n})\nexport class CartIdDataMigrateV2 {\n  public constructor() {}\n\n\n  public static async start(register: Register): Promise<void> {\n    const entitiesIdsResult = await Booster.entitiesIDs('Cart', 500, undefined)\n    const paginatedEntityIdResults = entitiesIdsResult.items\n\n    const carts = await Promise.all(\n      paginatedEntityIdResults.map(async (entity) => await Booster.entity(Cart, entity.entityID))\n    )\n    return await Promise.all(\n        carts.map(async (cart) => {\n          cart.cartItems[0].quantity = 100\n          const newCart = new Cart(cart.id, cart.cartItems, cart.shippingAddress, cart.checks)\n          await BoosterDataMigrations.migrateEntity('Cart', validCart.id, newCart)\n          return validCart.id\n      })\n    )\n\n    register.events(new BoosterDataMigrationFinished('CartIdDataMigrateV2'))\n  }\n}\n"})}),"\n",(0,o.jsx)(t.h1,{id:"migrate-from-previous-booster-versions",children:"Migrate from Previous Booster Versions"}),"\n",(0,o.jsxs)(t.ul,{children:["\n",(0,o.jsx)(t.li,{children:"To migrate to new versions of Booster, check that you have the latest development dependencies required:"}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-json",children:'"devDependencies": {\n    "rimraf": "^5.0.0",\n    "@typescript-eslint/eslint-plugin": "4.22.1",\n    "@typescript-eslint/parser": "4.22.1",\n    "eslint": "7.26.0",\n    "eslint-config-prettier": "8.3.0",\n    "eslint-plugin-prettier": "3.4.0",\n    "mocha": "10.2.0",\n    "@types/mocha": "10.0.1",\n    "nyc": "15.1.0",\n    "prettier":  "2.3.0",\n    "typescript": "4.5.4",\n    "ts-node": "9.1.1",\n    "@types/node": "15.0.2",\n    "ts-patch": "3.1.2",\n    "@boostercloud/metadata-booster": "0.30.2"\n  },\n'})}),"\n",(0,o.jsx)(t.h2,{id:"migrate-to-booster-version-1190",children:"Migrate to Booster version 1.19.0"}),"\n",(0,o.jsxs)(t.p,{children:["Booster version 1.19.0 requires updating your index.ts file to export the ",(0,o.jsx)(t.code,{children:"boosterHealth"})," method. If you have an index.ts file created from a previous Booster version, update it accordingly. Example:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"import { Booster } from '@boostercloud/framework-core'\nexport {\n  Booster,\n  boosterEventDispatcher,\n  boosterServeGraphQL,\n  boosterHealth,\n  boosterNotifySubscribers,\n  boosterTriggerScheduledCommand,\n  boosterRocketDispatcher,\n} from '@boostercloud/framework-core'\n\nBooster.start(__dirname)\n\n"})}),"\n",(0,o.jsx)(t.h2,{id:"migrate-to-booster-version-230",children:"Migrate to Booster version 2.3.0"}),"\n",(0,o.jsxs)(t.p,{children:["Booster version 2.3.0 updates the url for the GraphQL API, sensors, etc. for the Azure Provider. New base url is ",(0,o.jsx)(t.code,{children:"http://[resourcegroupname]apis.eastus.cloudapp.azure.com"})]}),"\n",(0,o.jsx)(t.p,{children:"Also, Booster version 2.3.0 deprecated the Azure Api Management in favor of Azure Application Gateway. You don't need to do anything to migrate to the new Application Gateway."}),"\n",(0,o.jsxs)(t.p,{children:["Booster 2.3.0 provides an improved Rocket process to handle Rockets with more than one function. To use this new feature, you need to implement method ",(0,o.jsx)(t.code,{children:"mountCode"})," in your ",(0,o.jsx)(t.code,{children:"Rocket"})," class. Example:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"const AzureWebhook = (params: WebhookParams): InfrastructureRocket => ({\n  mountStack: Synth.mountStack.bind(Synth, params),\n  mountCode: Functions.mountCode.bind(Synth, params),\n  getFunctionAppName: Functions.getFunctionAppName.bind(Synth, params),\n})\n"})}),"\n",(0,o.jsx)(t.p,{children:"This method will return an Array of functions definitions, the function name, and the host.json file. Example:"}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"export interface FunctionAppFunctionsDefinition<T extends Binding = Binding> {\n  functionAppName: string\n  functionsDefinitions: Array<FunctionDefinition<T>>\n  hostJsonPath?: string\n}\n"})}),"\n",(0,o.jsxs)(t.p,{children:["Booster 2.3.0 allows you to set the Azure App Service Plan used to deploy the main function app. Setting the ",(0,o.jsx)(t.code,{children:"BOOSTER_AZURE_SERVICE_PLAN_BASIC"})," (default value false) environment variable to true will force the use of a basic service plan instead of the default consumption plan."]}),"\n",(0,o.jsx)(t.h2,{id:"migrate-to-booster-version-260",children:"Migrate to Booster version 2.6.0"}),"\n",(0,o.jsxs)(t.p,{children:["Booster 2.6.0 allows you to set the Azure Application Gateway SKU used. Setting the ",(0,o.jsx)(t.code,{children:"BOOSTER_USE_WAF"})," (default value false) environment variable to true will force the use of a WAF sku instead of the Standard sku."]})]})}function h(e={}){const{wrapper:t}={...(0,a.a)(),...e.components};return t?(0,o.jsx)(t,{...e,children:(0,o.jsx)(l,{...e})}):l(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>s,a:()=>r});var o=n(7294);const a={},i=o.createContext(a);function r(e){const t=o.useContext(i);return o.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function s(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(a):e.components||a:r(e.components),o.createElement(i.Provider,{value:t},e.children)}}}]);