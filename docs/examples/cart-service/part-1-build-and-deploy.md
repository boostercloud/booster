# Build a scalable cart service in 10 minutes

In this example we will be creating a cart service that will be able to handle virtually infinite number of request in
10 minutes using Booster code generators. You don't need to use the generators and can write the code by yourself, but
they can save you some time and are specially useful when you are getting familiar with the Booster concepts.

The following steps are a transcript and simplification of the demo that was presented in the Serverlessconf New York 2019:
_[What Comes after Serverless? Less "Servers" and More "Less"!](https://acloud.guru/series/serverlessconf-nyc-2019/view/after-serverless)_.

You can follow the steps or watch the following video:
<p align="center">
  <a href="https://www.youtube.com/watch?v=DyYB7YscN_c">
    <img src="https://img.youtube.com/vi/DyYB7YscN_c/0.jpg" alt="Booster cart service">
  </a>
</p>

## Build the service
The following steps assumes you have booster already installed. If you don't, you can follow the [installation steps](http://docs.booster.cloud/#installing-booster)
in the [Booster documentation site](http://docs.booster.cloud). 

1. First of all, we need to create a new project called "cart-demo"
```shell script
boost new:project cart-demo
```
It will ask you some questions to fill some basic information about your project. If you don't want to answer them now,
you can just hit enter.

2. Go inside the project folder and create a new command called "ChangeCart". It will allow us to modify the cart and add items to it:
```shell script
cd cart-demo
boost new:command ChangeCart --fields cartId:UUID sku:string quantity:number
```
As you can see we have created it with three fields:
* **cartId**: This holds the ID of the cart we want to modify. Its type is `UUID`, which is a special type provided by Booster. All IDs should be of this type
* **sku**: This is the SKU of the item we want add to the cart. It is a common kind of identifier used in commerces to refer to a specific item.
* **quantity**: The amount of units of the item to add or remove

3. Create an event that will register the modifications we will do to a cart:
```shell script
boost new:event CartChanged --fields cartId:UUID sku:string quantity:number
```

4. Now we need to create the business logic for our command. In this case, it is really simple, as the only thing we need
to do is to register an event (the one we created in the previous step) that represent the addition of an item to a cart.
This could be seen as an equivalent action to a database commit.
To do this, we open and modify the command file (`commands/ChangeCart.ts`). Its code should be something like this:
```typescript
import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartChanged } from "../events/CartChanged";

@Command({
  authorize: 'all' // <-- Here we specify the "who" can access to this command. We'll use 'all' now (public access)
})
export class ChangeCart {
  public constructor(
    readonly cartId: UUID,
    readonly sku: string,
    readonly quantity: number,
  ) {}

  public handle(register: Register): void {
    register.events(new CartChanged(this.cartId, this.sku, this.quantity)) // <-- This is the main change we did
  }
}
```

5. Open your CartChanged event file and fill the body of the method `entityId()`. In Booster all the events are related to
a specific entity instance which, in our case, is the Cart (it will be created in the next step). Therefore, the only thing we need to do is to return the `cartId` field that our event already has.
The code of the event should be something like this:
```typescript
import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CartChanged {
  public constructor(
    readonly cartId: UUID,
    readonly sku: string,
    readonly quantity: number
  ) {}

  public entityID(): string {
    return this.cartId // <-- This is what we added
  }
}
```

6. Next, we will create the Cart entity. It will reduce all the cart-related events to build
the current state of our cart. To create it, we can use another generator:
```shell script
boost new:entity Cart --fields "items:Array<CartItem>" --reduces CartChanged
```
As you can see, our cart is just an array of cart item objects. The type `CartItem` is missing. You can create it either manually or with the `new:type` generator. Types generated like that will be placed in the `common/` folder. Let's use the generator:
```shell script
boost new:type CartItem --fields sku:string quantity:number
```

7. Then we need to write the logic that reduces the events into a Cart. This is business-dependent. In our case,
the code of the Entity class would be like this:
```typescript
import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CartChanged } from '../events/CartChanged'
import { CartItem } from "../common/CartItem";

@Entity
export class Cart {
  public constructor(
      readonly id: UUID, // This field is added automatically. Every entity decorated with "@Entity" must have this field
      readonly items: Array<CartItem>
  ) {}

  @Reduces(CartChanged)
  public static projectCartChanged(event: CartChanged, currentCart?: Cart): Cart {
    if (currentCart) {
      // This is the common case: we receive the previous state of the cart and modify it according to the event received.
      // In this case, we just add the new item.
      return new Cart(
          currentCart.id,
          Cart.newItems(currentCart.items, event.sku, event.quantity)
      )
    } else {
      // If there wasn't any previous Cart, we return one with the new item in it
      return new Cart(
          event.cartId,
          [{
            sku: event.sku,
            quantity: event.quantity
          }]
      )
    }
  }

  // Helper function that creates a copy of the current cart items and adds the new one
  public static newItems(items: Array<CartItem>, sku: string, quantity: number): Array<CartItem> {
    let current_item = items.find(i => i.sku === sku)
    if (current_item) {
      current_item.quantity += quantity
      return [...items]
    } else {
      return [...items, {
        sku,
        quantity
      }]
    }
  }
}
```

8. Finally, we need to define a read model so that we can access cart data through the public API. This read model will only project the Cart entity but, if we had more entities, we could project them too to and make this read model act like an "agreagate" of those entities. 
```shell script
boost new:read-model CartReadModel --fields "items:Array<CartItem>" --projects Cart:id
```
Now we can open the file (`read-models/CartReadModel.ts`), add the missing imports, and fill the projection method, which basically returns a new read model with its fields updated (read model projections work similarly to entity reducers: they receive the current state and return the updated state). 

``` typescript
import { ReadModel, Projects } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { Cart } from '../entities/Cart'
import { CartItem } from "../common/CartItem";

@ReadModel({
  authorize: 'all' // <-- We can define "who" can access ReadModels too. This time, everyone can access
})
export class CartReadModel {
  public constructor(
    public id: UUID,
    readonly items: Array<CartItem>,
  ) {}

  @Projects(Cart, "id")
  public static projectCart(entity: Cart, currentCartReadModel?: CartReadModel): CartReadModel {
    // In this case, we can just return a new ReadModel ignoring any previous state
    return new CartReadModel(entity.id, entity.items) 
  }

}
```
## Deploy it
That's it! Now you can deploy your cart service to the chosen cloud provider by doing 
```shell script
boost deploy -e production
```
You don't need to think about how to structure this in lambdas, how to interconnect every part so that the events can be published and
consumed, which kind of databases you need to use to store state, etc.
Everything is inferred from the code. What is more, **this service is now capable of handling millions of requests per minute**
without problems. And thanks to the serverless foundation Booster is based on, you won't be paying anything if it is not used.

After the deployment has finished successfully you will probably want to test the service.
We will do so in the next part of the example: _"[Part 2: Send mutations, queries, and subscriptions](part-2-send-mutations-queries-subscriptions.md)"_
