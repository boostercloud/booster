import { Register, UUID } from '@boostercloud/framework-types';
export declare class ChangeCartWithFields {
    readonly cartId: UUID;
    readonly sku: string;
    readonly quantity: number;
    constructor(cartId: UUID, sku: string, quantity: number);
    static handle(command: ChangeCartWithFields, register: Register): Promise<void>;
}
