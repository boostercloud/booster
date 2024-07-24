"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.changeCartItem = exports.graphQLClient = exports.signOutURL = exports.signInURL = exports.confirmUserURL = exports.signUpURL = exports.confirmUser = exports.createUser = void 0;
const cross_fetch_1 = require("cross-fetch");
const client_1 = require("@apollo/client");
const constants_1 = require("./constants");
// --- Auth helpers ---
async function createUser(username, password, role = '') {
    const response = await (0, cross_fetch_1.default)(signUpURL(), {
        method: 'POST',
        body: JSON.stringify({
            username: username,
            password: password,
            userAttributes: {
                role: role,
            },
        }),
        headers: {
            'Content-Type': 'application/json',
        },
    });
    await response.json();
    if (response.status != 200) {
        throw new Error('Failed to create a new user');
    }
}
exports.createUser = createUser;
async function confirmUser(username) {
    const response = await (0, cross_fetch_1.default)(confirmUserURL(username), {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        },
    });
    await response.json();
    if (response.status != 200) {
        throw new Error('Failed to confirm user');
    }
}
exports.confirmUser = confirmUser;
// --- URL helpers ---
function signUpURL() {
    return new URL('auth/sign-up', constants_1.LOCAL_PROVIDER_HOST).href;
}
exports.signUpURL = signUpURL;
function confirmUserURL(username) {
    return new URL(`auth/confirm/${username}`, constants_1.LOCAL_PROVIDER_HOST).href;
}
exports.confirmUserURL = confirmUserURL;
function signInURL() {
    return new URL('auth/sign-in', constants_1.LOCAL_PROVIDER_HOST).href;
}
exports.signInURL = signInURL;
function signOutURL() {
    return new URL('auth/sign-out', constants_1.LOCAL_PROVIDER_HOST).href;
}
exports.signOutURL = signOutURL;
// --- GraphQL helpers ---
async function graphQLClient(authToken) {
    const cache = new client_1.InMemoryCache();
    const headers = authToken ? { Authorization: `Bearer ${authToken}` } : {};
    const link = new client_1.HttpLink({
        uri: new URL('graphql', constants_1.LOCAL_PROVIDER_HOST).href,
        headers,
        fetch: cross_fetch_1.default,
    });
    return new client_1.ApolloClient({
        cache: cache,
        link: link,
        defaultOptions: {
            query: {
                fetchPolicy: 'no-cache',
            },
        },
    });
}
exports.graphQLClient = graphQLClient;
async function changeCartItem(client, cartId, productId, quantity
// eslint-disable-next-line @typescript-eslint/no-explicit-any
) {
    return client.mutate({
        variables: {
            cartId: cartId,
            productId: productId,
            quantity: quantity,
        },
        mutation: (0, client_1.gql) `
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
    `,
    });
}
exports.changeCartItem = changeCartItem;
