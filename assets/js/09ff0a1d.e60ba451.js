"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[5263],{3905:(e,t,a)=>{a.d(t,{Zo:()=>c,kt:()=>u});var n=a(7294);function i(e,t,a){return t in e?Object.defineProperty(e,t,{value:a,enumerable:!0,configurable:!0,writable:!0}):e[t]=a,e}function o(e,t){var a=Object.keys(e);if(Object.getOwnPropertySymbols){var n=Object.getOwnPropertySymbols(e);t&&(n=n.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),a.push.apply(a,n)}return a}function r(e){for(var t=1;t<arguments.length;t++){var a=null!=arguments[t]?arguments[t]:{};t%2?o(Object(a),!0).forEach((function(t){i(e,t,a[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(a)):o(Object(a)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(a,t))}))}return e}function s(e,t){if(null==e)return{};var a,n,i=function(e,t){if(null==e)return{};var a,n,i={},o=Object.keys(e);for(n=0;n<o.length;n++)a=o[n],t.indexOf(a)>=0||(i[a]=e[a]);return i}(e,t);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);for(n=0;n<o.length;n++)a=o[n],t.indexOf(a)>=0||Object.prototype.propertyIsEnumerable.call(e,a)&&(i[a]=e[a])}return i}var d=n.createContext({}),l=function(e){var t=n.useContext(d),a=t;return e&&(a="function"==typeof e?e(t):r(r({},t),e)),a},c=function(e){var t=l(e.components);return n.createElement(d.Provider,{value:t},e.children)},p={inlineCode:"code",wrapper:function(e){var t=e.children;return n.createElement(n.Fragment,{},t)}},m=n.forwardRef((function(e,t){var a=e.components,i=e.mdxType,o=e.originalType,d=e.parentName,c=s(e,["components","mdxType","originalType","parentName"]),m=l(a),u=i,h=m["".concat(d,".").concat(u)]||m[u]||p[u]||o;return a?n.createElement(h,r(r({ref:t},c),{},{components:a})):n.createElement(h,r({ref:t},c))}));function u(e,t){var a=arguments,i=t&&t.mdxType;if("string"==typeof e||i){var o=a.length,r=new Array(o);r[0]=m;var s={};for(var d in t)hasOwnProperty.call(t,d)&&(s[d]=t[d]);s.originalType=e,s.mdxType="string"==typeof e?e:i,r[1]=s;for(var l=2;l<o;l++)r[l]=a[l];return n.createElement.apply(null,r)}return n.createElement.apply(null,a)}m.displayName="MDXCreateElement"},4199:(e,t,a)=>{a.r(t),a.d(t,{assets:()=>d,contentTitle:()=>r,default:()=>p,frontMatter:()=>o,metadata:()=>s,toc:()=>l});var n=a(7462),i=(a(7294),a(3905));const o={description:"Learn how to migrate data in Booster"},r="Migrations",s={unversionedId:"going-deeper/data-migrations",id:"going-deeper/data-migrations",title:"Migrations",description:"Learn how to migrate data in Booster",source:"@site/docs/10_going-deeper/data-migrations.md",sourceDirName:"10_going-deeper",slug:"/going-deeper/data-migrations",permalink:"/going-deeper/data-migrations",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/data-migrations.md",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1686145545,formattedLastUpdatedAt:"Jun 7, 2023",frontMatter:{description:"Learn how to migrate data in Booster"},sidebar:"docs",previous:{title:"Testing",permalink:"/going-deeper/testing"},next:{title:"TouchEntities",permalink:"/going-deeper/touch-entities"}},d={},l=[{value:"Schema migrations",id:"schema-migrations",level:2},{value:"Data migrations",id:"data-migrations",level:2}],c={toc:l};function p(e){let{components:t,...a}=e;return(0,i.kt)("wrapper",(0,n.Z)({},c,a,{components:t,mdxType:"MDXLayout"}),(0,i.kt)("h1",{id:"migrations"},"Migrations"),(0,i.kt)("p",null,"Migrations are a mechanism for updating or transforming the schemas of events and entities as your system evolves. This allows you to make changes to your data model without losing or corrupting existing data. There are two types of migration tools available in Booster: schema migrations and data migrations."),(0,i.kt)("ul",null,(0,i.kt)("li",{parentName:"ul"},(0,i.kt)("p",{parentName:"li"},(0,i.kt)("strong",{parentName:"p"},"Schema migrations")," are used to incrementally upgrade an event or entity from a past version to the next. They are applied lazily, meaning that they are performed on-the-fly whenever an event or entity is loaded. This allows you to make changes to your data model without having to manually update all existing artifacts, and makes it possible to apply changes without running lenghty migration processes.")),(0,i.kt)("li",{parentName:"ul"},(0,i.kt)("p",{parentName:"li"},(0,i.kt)("strong",{parentName:"p"},"Data migrations"),", on the other hand, behave as background processes that can actively change the existing values in the database for existing entities and read models. They are particularly useful for data migrations that cannot be performed automatically with schema migrations, or for updating existing read models after a schema change."))),(0,i.kt)("p",null,"Together, schema and data migrations provide a flexible and powerful toolset for managing the evolution of your data model over time."),(0,i.kt)("h2",{id:"schema-migrations"},"Schema migrations"),(0,i.kt)("p",null,"Booster handles classes annotated with ",(0,i.kt)("inlineCode",{parentName:"p"},"@Migrates")," as ",(0,i.kt)("strong",{parentName:"p"},"schema migrations"),". The migration functions defined inside will update an existing artifact (either an event or an entity) from a previous version to a newer one whenever that artifact is visited. Schema migrations are applied to events and entities lazyly, meaning that they are only applied when the event or entity is loaded. This ensures that the migration process is non-disruptive and does not affect the performance of your system. Schema migrations are also performed on-the-fly and the results are not written back to the database, as events are not revisited once the next snapshot is written in the database."),(0,i.kt)("p",null,"For example, to upgrade a ",(0,i.kt)("inlineCode",{parentName:"p"},"Product")," entity from version 1 to version 2, you can write the following migration class:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-typescript"},"@Migrates(Product)\nexport class ProductMigration {\n  @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })\n  public async changeNameFieldToDisplayName(old: ProductV1): Promise<ProductV2> {\n    return new ProductV2(\n      old.id,\n      old.sku,\n      old.name,\n      old.description,\n      old.price,\n      old.pictures,\n      old.deleted\n    )\n  }\n}\n")),(0,i.kt)("p",null,"Notice that we've used the ",(0,i.kt)("inlineCode",{parentName:"p"},"@ToVersion")," decorator in the above example. This decorator not only tells Booster what schema upgrade this migration performs, it also informs it about the existence of a version, which is always an integer number. Booster will always use the latest version known to tag newly created artifacts, defaulting to 1 when no migrations are defined. This ensures that the schema of newly created events and entities is up-to-date and that they can be migrated as needed in the future."),(0,i.kt)("p",null,"The ",(0,i.kt)("inlineCode",{parentName:"p"},"@ToVersion")," decorator takes two parameters in addition to the version: ",(0,i.kt)("inlineCode",{parentName:"p"},"fromSchema")," and ",(0,i.kt)("inlineCode",{parentName:"p"},"toSchema"),". The fromSchema parameter is set to ",(0,i.kt)("inlineCode",{parentName:"p"},"ProductV1"),", while the ",(0,i.kt)("inlineCode",{parentName:"p"},"toSchema")," parameter is set to ",(0,i.kt)("inlineCode",{parentName:"p"},"ProductV2"),". This tells Booster that the migration is updating the ",(0,i.kt)("inlineCode",{parentName:"p"},"Product")," object from version 1 (as defined by the ",(0,i.kt)("inlineCode",{parentName:"p"},"ProductV1")," schema) to version 2 (as defined by the ",(0,i.kt)("inlineCode",{parentName:"p"},"ProductV2")," schema)."),(0,i.kt)("p",null,"As Booster can easily read the structure of your classes, the schemas are described as plain classes that you can maintain as part of your code. The ",(0,i.kt)("inlineCode",{parentName:"p"},"ProductV1")," class represents the schema of the previous version of the ",(0,i.kt)("inlineCode",{parentName:"p"},"Product")," object with the properties and structure of the ",(0,i.kt)("inlineCode",{parentName:"p"},"Product")," object as it was defined in version 1. The ",(0,i.kt)("inlineCode",{parentName:"p"},"ProductV2")," class is an alias for the latest version of the Product object. You can use the ",(0,i.kt)("inlineCode",{parentName:"p"},"Product")," class here, there's no difference, but it's a good practice to create an alias for clarity."),(0,i.kt)("p",null,"It's a good practice to define the schema classes (",(0,i.kt)("inlineCode",{parentName:"p"},"ProductV1")," and ",(0,i.kt)("inlineCode",{parentName:"p"},"ProductV2"),") as non-exported classes in the same migration file. This allows you to see the changes made between versions and helps to understand how the migration works:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-typescript"},"class ProductV1 {\n  public constructor(\n    public id: UUID,\n    readonly sku: string,\n    readonly name: string,\n    readonly description: string,\n    readonly price: Money,\n    readonly pictures: Array<Picture>,\n    public deleted: boolean = false\n  ) {}\n}\n\nclass ProductV2 extends Product {}\n")),(0,i.kt)("p",null,"When you want to upgrade your artifacts from V2 to V3, you can add a new function decorated with ",(0,i.kt)("inlineCode",{parentName:"p"},"@ToVersion")," to the same migrations class. You're free to structure the code the way you want, but we recommend keeping all migrations for the same artifact in the same migration class. For instance:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-typescript"},"@Migrates(Product)\nexport class ProductMigration {\n  @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })\n  public async changeNameFieldToDisplayName(old: ProductV1): Promise<ProductV2> {\n    return new ProductV2(\n      old.id,\n      old.sku,\n      old.name, // It's now called `displayName`\n      old.description,\n      old.price,\n      old.pictures,\n      old.deleted\n    )\n  }\n\n  @ToVersion(3, { fromSchema: ProductV2, toSchema: ProductV3 })\n  public async addNewField(old: ProductV2): Promise<ProductV3> {\n    return new ProductV3(\n      old.id,\n      old.sku,\n      old.displayName,\n      old.description,\n      old.price,\n      old.pictures,\n      old.deleted,\n      42 // We set a default value to initialize this field\n    )\n  }\n}\n")),(0,i.kt)("p",null,"In this example, the ",(0,i.kt)("inlineCode",{parentName:"p"},"changeNameFieldToDisplayName")," function updates the ",(0,i.kt)("inlineCode",{parentName:"p"},"Product")," entity from version 1 to version 2 by renaming the ",(0,i.kt)("inlineCode",{parentName:"p"},"name")," field to ",(0,i.kt)("inlineCode",{parentName:"p"},"displayName"),". Then, ",(0,i.kt)("inlineCode",{parentName:"p"},"addNewField")," function updates the ",(0,i.kt)("inlineCode",{parentName:"p"},"Product")," entity from version 2 to version 3 by adding a new field called ",(0,i.kt)("inlineCode",{parentName:"p"},"newField")," to the entity's schema. Notice that at this point, your database could have snapshots set as v1, v2, or v3, so while it might be tempting to redefine the original migration to keep a single 1-to-3 migration, it's usually a good idea to keep the intermediate steps. This way Booster will be able to handle any scenario."),(0,i.kt)("h2",{id:"data-migrations"},"Data migrations"),(0,i.kt)("p",null,"Data migrations can be seen as background processes that can actively update the values of existing entities and read models in the database. They can be useful to perform data migrations that cannot be handled with schema migrations, for example when you need to update the values exposed by the GraphQL API, or to initialize new read models that are projections of previously existing entities."),(0,i.kt)("p",null,"To create a data migration in Booster, you can use the ",(0,i.kt)("inlineCode",{parentName:"p"},"@DataMigration")," decorator on a class that implements a ",(0,i.kt)("inlineCode",{parentName:"p"},"start")," method. The ",(0,i.kt)("inlineCode",{parentName:"p"},"@DataMigration")," decorator takes an object with a single parameter, ",(0,i.kt)("inlineCode",{parentName:"p"},"order"),", which specifies the order in which the data migration should be run relative to other data migrations."),(0,i.kt)("p",null,"Data migrations are not run automatically, you need to invoke the ",(0,i.kt)("inlineCode",{parentName:"p"},"BoosterDataMigrations.run()")," method from an event handler or a command. This will emit a ",(0,i.kt)("inlineCode",{parentName:"p"},"BoosterDataMigrationStarted")," event, which will make Booster check for any pending migrations and run them in the specified order. A common pattern to be able to run migrations on demand is to add a special command, with access limited to an administrator role which calls this function. "),(0,i.kt)("p",null,"Take into account that, depending on your cloud provider implementation, data migrations are executed in the context of a lambda or function app, so it's advisable to design these functions in a way that allow to re-run them in case of failures (i.e. lambda timeouts). In order to tell Booster that your migration has been applied successfully, at the end of each ",(0,i.kt)("inlineCode",{parentName:"p"},"DataMigration.start")," method, you must emit a ",(0,i.kt)("inlineCode",{parentName:"p"},"BoosterDataMigrationFinished")," event manually."),(0,i.kt)("p",null,"Inside your ",(0,i.kt)("inlineCode",{parentName:"p"},"@DataMigration")," classes, you can use the ",(0,i.kt)("inlineCode",{parentName:"p"},"Booster.migrateEntity")," method to update the data for a specific entity. This method takes the old entity name, the old entity ID, and the new entity data as arguments. It will also generate an internal ",(0,i.kt)("inlineCode",{parentName:"p"},"BoosterEntityMigrated")," event before performing the migration."),(0,i.kt)("p",null,"Here is an example of how you might use the ",(0,i.kt)("inlineCode",{parentName:"p"},"@DataMigration")," decorator and the ",(0,i.kt)("inlineCode",{parentName:"p"},"Booster.migrateEntity")," method to update the quantity of the first item in a cart:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-typescript"},"@DataMigration({\n  order: 2,\n})\nexport class CartIdDataMigrateV2 {\n  public constructor() {}\n\n\n  public static async start(register: Register): Promise<void> {\n    const entitiesIdsResult = await Booster.entitiesIDs('Cart', 500, undefined)\n    const paginatedEntityIdResults = entitiesIdsResult.items\n\n    const carts = await Promise.all(\n      paginatedEntityIdResults.map(async (entity) => await Booster.entity(Cart, entity.entityID))\n    )\n    return await Promise.all(\n        carts.map(async (cart) => {\n          cart.cartItems[0].quantity = 100\n          const newCart = new Cart(cart.id, cart.cartItems, cart.shippingAddress, cart.checks)\n          await Booster.migrateEntity('Cart', validCart.id, newCart)\n          return validCart.id\n      })\n    )\n\n    register.events(new BoosterDataMigrationFinished('CartIdDataMigrateV2'))\n  }\n}\n")),(0,i.kt)("h1",{id:"migrate-from-previous-booster-versions"},"Migrate from Previous Booster Versions"),(0,i.kt)("ul",null,(0,i.kt)("li",{parentName:"ul"},"To migrate to new versions of Booster, check that you have the latest development dependencies required:")),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-json"},'"devDependencies": {\n    "rimraf": "^3.0.1",\n    "@typescript-eslint/eslint-plugin": "4.22.1",\n    "@typescript-eslint/parser": "4.22.1",\n    "eslint": "7.26.0",\n    "eslint-config-prettier": "8.3.0",\n    "eslint-plugin-prettier": "3.4.0",\n    "mocha": "8.4.0",\n    "@types/mocha": "8.2.2",\n    "nyc": "15.1.0",\n    "prettier":  "2.3.0",\n    "typescript": "4.5.4",\n    "ts-node": "9.1.1",\n    "@types/node": "15.0.2",\n    "ttypescript": "1.5.13",\n    "@boostercloud/metadata-booster": "0.30.2"\n  },\n')))}p.isMDXComponent=!0}}]);