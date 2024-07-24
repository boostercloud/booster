import { UUID } from '@boostercloud/framework-types';
export declare class PostCreated {
    readonly postId: UUID;
    readonly title: string;
    readonly body: string;
    constructor(postId: UUID, title: string, body: string);
    entityID(): UUID;
}
