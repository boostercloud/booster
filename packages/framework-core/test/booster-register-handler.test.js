"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("./expect");
const framework_types_1 = require("@boostercloud/framework-types");
const sinon_1 = require("sinon");
const src_1 = require("../src");
const booster_entity_migrated_1 = require("../src/core-concepts/data-migration/events/booster-entity-migrated");
class SomeEntity {
    constructor(id) {
        this.id = id;
    }
}
class SomeEvent {
    constructor(someField) {
        this.someField = someField;
    }
    entityID() {
        return '42';
    }
}
class SomeNotification {
    constructor() { }
}
describe('the `RegisterHandler` class', () => {
    const testConfig = new framework_types_1.BoosterConfig('Test');
    testConfig.logLevel = framework_types_1.Level.debug;
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    it('handles a register', async () => {
        const config = new framework_types_1.BoosterConfig('test');
        config.provider = {
            events: {
                store: (0, sinon_1.fake)(),
            },
        };
        config.reducers['SomeEvent'] = { class: SomeEntity, methodName: 'whatever' };
        const register = new framework_types_1.Register('1234', {}, src_1.RegisterHandler.flush);
        const event1 = new SomeEvent('a');
        const event2 = new SomeEvent('b');
        register.events(event1, event2);
        const registerHandler = src_1.RegisterHandler;
        (0, sinon_1.spy)(registerHandler, 'wrapEvent');
        await src_1.RegisterHandler.handle(config, register);
        (0, expect_1.expect)(registerHandler.wrapEvent).to.have.been.calledTwice;
        (0, expect_1.expect)(registerHandler.wrapEvent).to.have.been.calledWith(config, event1, register);
        (0, expect_1.expect)(registerHandler.wrapEvent).to.have.been.calledWith(config, event2, register);
        (0, expect_1.expect)(config.provider.events.store).to.have.been.calledOnce;
    });
    it('does nothing when there are no events', async () => {
        const config = new framework_types_1.BoosterConfig('test');
        config.provider = {
            events: {
                store: (0, sinon_1.fake)(),
            },
        };
        config.reducers['SomeEvent'] = { class: SomeEntity, methodName: 'whatever' };
        const register = new framework_types_1.Register('1234', {}, src_1.RegisterHandler.flush);
        await src_1.RegisterHandler.handle(config, register);
        (0, expect_1.expect)(config.provider.events.store).to.not.have.been.called;
    });
    it('stores wrapped events', async () => {
        const config = new framework_types_1.BoosterConfig('test');
        config.provider = {
            events: {
                store: (0, sinon_1.fake)(),
            },
        };
        config.reducers['SomeEvent'] = {
            class: SomeEntity,
            methodName: 'aReducer',
        };
        (0, sinon_1.replace)(Date.prototype, 'toISOString', sinon_1.fake.returns('just the right time'));
        const register = new framework_types_1.Register('1234', {}, src_1.RegisterHandler.flush);
        const event1 = new SomeEvent('a');
        const event2 = new SomeEvent('b');
        register.events(event1, event2);
        await src_1.RegisterHandler.handle(config, register);
        (0, expect_1.expect)(config.provider.events.store).to.have.been.calledOnce;
        (0, expect_1.expect)(config.provider.events.store).to.have.been.calledWithMatch([
            {
                currentUser: undefined,
                entityID: '42',
                entityTypeName: 'SomeEntity',
                kind: 'event',
                superKind: 'domain',
                requestID: '1234',
                typeName: 'SomeEvent',
                value: event1,
                version: 1,
            },
            {
                currentUser: undefined,
                entityID: '42',
                entityTypeName: 'SomeEntity',
                kind: 'event',
                superKind: 'domain',
                requestID: '1234',
                typeName: 'SomeEvent',
                value: event2,
                version: 1,
            },
        ], config);
    });
    it('can wrap events to produce eventEnvelopes', () => {
        const config = new framework_types_1.BoosterConfig('test');
        config.reducers['SomeEvent'] = {
            class: SomeEntity,
            methodName: 'someReducer',
        };
        const user = {
            username: 'paco@example.com',
            roles: ['Paco'],
            claims: {},
        };
        const register = new framework_types_1.Register('1234', {}, src_1.RegisterHandler.flush, user);
        const event = new SomeEvent('a');
        const registerHandler = src_1.RegisterHandler;
        (0, expect_1.expect)(registerHandler.wrapEvent(config, event, register)).to.deep.equal({
            version: 1,
            kind: 'event',
            superKind: 'domain',
            entityID: '42',
            requestID: '1234',
            entityTypeName: 'SomeEntity',
            value: event,
            currentUser: user,
            typeName: 'SomeEvent',
        });
    });
    it('can wrap notifications to produce eventEnvelopes', () => {
        const config = new framework_types_1.BoosterConfig('test');
        config.notifications[SomeNotification.name] = {
            class: SomeNotification,
        };
        const user = {
            username: 'paco@example.com',
            roles: ['Paco'],
            claims: {},
        };
        const register = new framework_types_1.Register('1234', {}, src_1.RegisterHandler.flush, user);
        const notification = new SomeNotification();
        const registerHandler = src_1.RegisterHandler;
        (0, expect_1.expect)(registerHandler.wrapEvent(config, notification, register)).to.deep.equal({
            version: 1,
            kind: 'event',
            superKind: 'domain',
            entityID: 'default',
            requestID: '1234',
            entityTypeName: 'defaultTopic',
            value: notification,
            currentUser: user,
            typeName: SomeNotification.name,
        });
    });
    it('can wrap internal events to produce eventEnvelopes', () => {
        const config = new framework_types_1.BoosterConfig('test');
        config.reducers['BoosterEntityMigrated'] = {
            class: SomeEntity,
            methodName: 'someReducer',
        };
        const user = {
            username: 'paco@example.com',
            roles: ['Paco'],
            claims: {},
        };
        const register = new framework_types_1.Register('1234', {}, src_1.RegisterHandler.flush, user);
        const someEntity = new SomeEntity('42');
        const event = new booster_entity_migrated_1.BoosterEntityMigrated('oldEntity', 'oldEntityId', 'newEntityName', someEntity);
        const registerHandler = src_1.RegisterHandler;
        (0, expect_1.expect)(registerHandler.wrapEvent(config, event, register)).to.deep.equal({
            version: 1,
            kind: 'event',
            superKind: 'booster',
            entityTypeName: 'oldEntity',
            entityID: 'oldEntityId',
            requestID: '1234',
            value: event,
            currentUser: user,
            typeName: 'BoosterEntityMigrated',
        });
    });
});
