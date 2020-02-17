// TODO: Find a way to generalize this into configuration. This file is duplicated in packages `framework-provider-aws` and `framework-provider-aws-infrastructure`
export const eventStorePartitionKeyAttributeName = 'entityTypeName_entityID_kind'
export const eventStoreSortKeyAttributeName = 'createdAt'
