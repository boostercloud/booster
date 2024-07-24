import { CartItemChanged } from '../events/cart-item-changed';
import { Register } from '@boostercloud/framework-types';
export declare class HandleCartChange {
    static handle(event: CartItemChanged, register: Register): Promise<void>;
}
