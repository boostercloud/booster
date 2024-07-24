"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ApolloService = void 0;
var client_1 = require("@apollo/client");
var ws_1 = require("@apollo/client/link/ws");
var utilities_1 = require("@apollo/client/utilities");
var subscriptions_transport_ws_1 = require("subscriptions-transport-ws");
var ApolloService = /** @class */ (function () {
    function ApolloService() {
    }
    ApolloService.initClient = function (httpUri, wsUri) {
        var httpLink = new client_1.HttpLink({
            uri: httpUri,
        });
        var wsLink = new ws_1.WebSocketLink(new subscriptions_transport_ws_1.SubscriptionClient(wsUri, {
            reconnect: true,
        }));
        var splitLink = (0, client_1.split)(function (_a) {
            var query = _a.query;
            var definition = (0, utilities_1.getMainDefinition)(query);
            return definition.kind === 'OperationDefinition' && definition.operation === 'subscription';
        }, wsLink, httpLink);
        return new client_1.ApolloClient({
            cache: new client_1.InMemoryCache(),
            link: client_1.ApolloLink.from([splitLink]),
        });
    };
    return ApolloService;
}());
exports.ApolloService = ApolloService;
