import { Class } from "..";
import { ReadModelInterface } from "./read-model";

export interface ReadModelReferenceMetadata {
    readonly referencedReadModel: Class<ReadModelInterface>
    readonly foreignKey: string
}