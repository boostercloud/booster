import { GraphQLOperation } from './envelope'
import { ExecutionResult } from 'graphql'

export enum MessageTypes {
  GQL_CONNECTION_INIT = 'connection_init',
  GQL_CONNECTION_ACK = 'connection_ack',
  GQL_CONNECTION_ERROR = 'connection_error',
  // GQL_CONNECTION_KEEP_ALIVE = 'ka', // Not used on server side. Socket remains open until client decides to close it
  GQL_CONNECTION_TERMINATE = 'connection_terminate',
  GQL_START = 'start',
  GQL_DATA = 'data',
  GQL_ERROR = 'error',
  GQL_COMPLETE = 'complete',
  GQL_STOP = 'stop',
}

// Client -> Server messages shapes
// They are interfaces because they are built by the client and parsed in the server
export type GraphQLClientMessage = GraphQLInit | GraphQLStart | GraphQLStop | GraphQLTerminate
export interface GraphQLInit {
  type: MessageTypes.GQL_CONNECTION_INIT
  payload: {
    Authorization?: string
  }
}
export interface GraphQLStart {
  type: MessageTypes.GQL_START
  id: string
  payload: Omit<GraphQLOperation, 'id'>
}
export interface GraphQLStop {
  type: MessageTypes.GQL_STOP
  id: string
}
export interface GraphQLTerminate {
  type: MessageTypes.GQL_CONNECTION_TERMINATE
}

// Server -> Client messages shapes
// They are classes because they need to be built by the server (Booster)
export type GraphQLServerMessage = GraphQLInitError | GraphQLInitAck | GraphQLData | GraphQLError | GraphQLComplete

export class GraphQLInitError {
  public readonly type = MessageTypes.GQL_CONNECTION_ERROR
  public constructor(public readonly payload: string) {}
}
export class GraphQLInitAck {
  public readonly type = MessageTypes.GQL_CONNECTION_ACK
}
export class GraphQLData {
  public readonly type = MessageTypes.GQL_DATA
  public constructor(public readonly id: string, public readonly payload: ExecutionResult) {}
}
export class GraphQLError {
  public readonly type = MessageTypes.GQL_ERROR
  public constructor(public readonly id: string, public readonly payload: { errors: Array<Error> }) {}
}
export class GraphQLComplete {
  public readonly type = MessageTypes.GQL_COMPLETE
  public constructor(public readonly id: string) {}
}
