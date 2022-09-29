# Stack Cleaner

Provider agnostic way of cleaning stacks. (Although it only has an implementation for AWS).

Usage (`cd` into this folder first) :

```bash
rush build && rushx start us-east-1 a b c d
```

First argument is always the region.

And the rest of the args, meaning `a`, `b`, `c`, and `d` are prefixes of the stacks
