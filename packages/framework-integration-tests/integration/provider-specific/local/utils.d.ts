import { ApolloClient, NormalizedCacheObject } from '@apollo/client';
export declare function createUser(username: string, password: string, role?: string): Promise<void>;
export declare function confirmUser(username: string): Promise<void>;
export declare function signUpURL(): string;
export declare function confirmUserURL(username: string): string;
export declare function signInURL(): string;
export declare function signOutURL(): string;
export declare function graphQLClient(authToken?: string): Promise<ApolloClient<NormalizedCacheObject>>;
export declare function changeCartItem(client: ApolloClient<NormalizedCacheObject>, cartId: string, productId: string, quantity: number): Promise<any>;
