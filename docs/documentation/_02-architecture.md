# Architecture and core concepts

Booster’s architecture is heavily inspired by the [CQRS](https://www.martinfowler.com/bliki/CQRS.html) and [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) patterns.
These patterns have proven to work well for highly-distributed high available systems, being a tool to make resilient
software that is fast and scales very well, especially in distributed scenarios.

With these patterns combined, in a Booster Application:

- The "write operations" are separated from the "read operations" (called [_commands_](03-commands.md) and [_queries_](04-read-models.md), respectively) and dependencies are limited to data.
- Instead of storing and mutating the data in a single stateful database, Booster stores the state as a virtually infinite append-only list of events (think of your bank account, where all the movements are stored as individual movements).
- The event stream is the system source of truth, and the "current state" can be queried anytime reducing it on the fly as [entities](05-entities.md).

This architecture has many advantages:

- The whole architecture is designed for high availability and eventual consistency.
- The code is much easier to change because modules are loosely coupled.
- Old code can live along with new code without affecting each other, so it's easier to smoothly test and introduce new features.
- System boundaries are clearly defined and are easy to maintain.

It's usually non-trivial to get event-driven architecture design right and implement a maintainable event-driven solution that scales, but Booster has been built around these concepts and will greatly help you and your team to keep things under control. Booster integrates event-driven design in a way that simplifies their usage and understanding.

The Booster high-level architecture diagram looks like this:

![Booster architecture](../img/booster-arch.png)

Learn more about each topic in the corresponding sections:
- [Commands](03-commands.md): The entry point of your application (Write API).
- [Events](04-events.md): The source of truth of your application.
- [Entities](05-entities.md): The domain model of your application.
- [Read Models](06-read-models.md): Highly optimized Read API