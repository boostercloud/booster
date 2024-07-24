import { UUID } from '@boostercloud/framework-types';
import { PostCreated } from '../events/post-created';
export declare class PostWithReducer {
    id: UUID;
    readonly title: string;
    readonly body: string;
    constructor(id: UUID, title: string, body: string);
    static reducePostCreated(event: PostCreated, currentPostWithReducer?: PostWithReducer): PostWithReducer;
}
