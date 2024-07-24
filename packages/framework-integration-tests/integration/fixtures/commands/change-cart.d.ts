import { Register } from '@boostercloud/framework-types';
export declare class ChangeCart {
    constructor();
    static handle(command: ChangeCart, register: Register): Promise<void>;
}
