import { UUID } from '@boostercloud/framework-types';
export declare class PostWithFields {
    id: UUID;
    readonly title: string;
    readonly body: string;
    constructor(id: UUID, title: string, body: string);
}
