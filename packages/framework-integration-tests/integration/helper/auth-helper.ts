import { internet } from 'faker'

export const createPassword = (): string => {
  return `${internet.password(8)}Passw0rd!`
}