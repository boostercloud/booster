import { UserEnvelope } from '../envelope'

export interface TokenVerifier {
  verify(token: string): Promise<UserEnvelope>
}
