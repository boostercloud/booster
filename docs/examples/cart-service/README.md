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

## Create the cart service
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
* **cartID**: This holds the ID of the cart we want to modify. Its type is `UUID`, which is a special type provided by Booster. All IDs should be of this type
* **sku**: This is the SKU of the item we want add to the cart. It is a common kind of identifier used in commerces to refer to a specific item.
* **quantity**: The amount of units of the item to add

3. Create an event that will register the modifications we will do to a cart:
```shell script
boost new:event CartChanged --fields cartId:UUID sku:string quantity:number
```

4. Now we need to create the business logic for our command. In this case, it is really simple, as the only thing we need
to do is to register an event (the one we created in the previous step) that represent the addition of an item to a cart.
This could be seen as an equivalent action to a database commit.
The code of the command should be something like this (after adding any missing import):
```typescript
// ... imports here ...

@Command({
  authorize: 'all',
})
export class ChangeCart {
  public constructor(
    readonly cartId: UUID,
    readonly sku: string,
    readonly quantity: number
  ) {}

  public handle(register: Register): void {
    register.events(new CartChanged(this.cartId, this.sku, this.quantity)) // <-- This is what we added
  }
}
```

5. Open your CartChanged event file and fill the body of the method `entityId()`. In Booster all the events are related to
a specific entity instance which, in our case, is the Cart. Therefore, the only thing we need to do is to return the `cartId`
field that our event already has.
The code of the event should be something like this:
```typescript
// ... imports here ...

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

6. Next, we will create the Cart entity. It will project all the cart-related events to build
the current state of our cart. To create it, we can use another generator:
```shell script
boost new:entity Cart --fields "items:Array<CartItem>" --reduces CartChanged
```
As you can see, our cart is just an array of cart item objects. The type `CartItem` is missing, but we will create it
manually in the Cart entity file. You can also use the type generator `boost new:type` if you prefer. Types generated like that
will be placed in the `common/` folder.

7. Then we need to write the logic that projects the events into a Cart. This is business-dependent. In our case,
the code of the Entity class would be like this:
```typescript
// ... imports here ...

// This is the CartItem type. As said, it is an auxiliary type that's only used from within Cart objects, so there's no
// need to export it
interface CartItem {
  sku: string
  quantity: number
}

@Entity
export class Cart {
  public constructor(
    readonly id: UUID, // This field is added automatically. Every entity decorated with "@Entity" must have this field
    readonly items: Array<CartItem>
  ) {}

  @Projects(CartChanged)
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
      return new Cart(event.cartId, [{
          sku: event.sku,
          quantity: event.quantity
        }]
      )
    }
  }

  // Helper function that creates a copy of the current cart items but adding the new one
  public static newItems(items: Array<CartItem>, sku: string, quantity: number): Array<CartItem> {
    return items.map(item => {
      if (item.sku == sku) {
        return {
          ...item,
          quantity: item.quantity + quantity
        }
      }
      return {...item}
    })
  }
}
```

8. Finally, we need to define the read model.
We will create a directory named `read-models` and create a new file under the new directory called `CartReadModel.ts` with the following content

``` typescript
// ... imports here ...

@ReadModel
export class CartReadModel {
  public constructor(
    readonly id: UUID,
    readonly items: Array<CartItem>,
  ) {}

  @Projection(Cart, 'id')
  public static updateWithCart(cart: Cart, oldCartReadModel?: CartReadModel): CartReadModel {
    return new CartReadModel(cart.id, cart.items)
  }
}
```

That that's it! Now you can deploy your cart service to the chosen cloud provider by doing `boost deploy`. You don't need
to think about how to structure this in lambdas, how to interconnect every part so that the events can be published and
consumed, which kind of databases you need to use to store state, etc.
Everything is inferred from he code. What is more, **this service is now capable of handling millions of requests per minute**
without problems. And thanks to the serverless foundation Booster is based on, you won't be paying anything if it is not used.

## Test the service

If you have deployed your service successfully, you should see the URL of the API of your application printed in the console.
Something like this: `https://<API ID>.execute-api.<region>.amazonaws.com/prod`

In order to send a request with the command we created, you need to do a **POST request** to the above URL followed by the `/commands`
segment. The body should be like this:
```json
{
  "typeName": "ChangeCart",
  "version": 1,
  "value": {
    "cartId": "demo-id",
    "sku": "DEMO_123",
    "quantity": 1
  }
}
```
The meaning of the fields are as follows:
- **typeName:** This indicates the command name
- **version:** The current version of the command. Don't worry about this now
- **value:** And here we specify the all the fields we defined in the command when we created it.

Additionally, if you want to retrieve information about the cart, you need to do a **GET request** to the above URL followed by the `readmodels/CartReadModel` segment. A response similar to the one below should be returned:
```json
[
    {
        "id": "demo-id",
        "items": [
            {
                "sku": "DEMO_123",
                "quantity": 1
            }
        ]
    },
    {
        "id": "demo-id2",
        "items": [
            {
                "sku": "DEMO_123",
                "quantity": 1
            }
        ]
    }
]
```

It is also possible to retrieve a specific cart by providing the cart id as follow, `readmodels/CartReadModel/demo-id`.
```json
{
    "id": "demo-id",
    "items": [
        {
            "sku": "DEMO_123",
            "quantity": 1
        }
    ]
}
```