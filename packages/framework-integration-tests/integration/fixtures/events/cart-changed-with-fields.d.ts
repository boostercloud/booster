import { UUID } from '@boostercloud/framework-types';
export declare class CartChangedWithFields {
    readonly cartId: UUID;
    readonly sku: string;
    readonly quantity: number;
    constructor(cartId: UUID, sku: string, quantity: number);
    entityID(): UUID;
}
