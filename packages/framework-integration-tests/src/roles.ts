import { Role } from '@boostercloud/framework-core'

@Role({
  auth: {
    // Do not specify (or use an empty array) if you don't want to allow sign-ups
    signUpMethods: [],
  },
})
export class Admin {}

@Role({
  auth: {
    signUpMethods: ['email'],
    requiresConfirmation: true,
  },
})
export class UserWithEmail {}

@Role({
  auth: {
    signUpMethods: ['phone'],
  },
})
export class UserWithPhone {}

@Role({
  auth: {
    signUpMethods: ['email', 'phone'],
    requiresConfirmation: true,
  },
})
export class SuperUser {}

@Role({
  auth: {
    signUpMethods: ['email', 'phone'],
    requiresConfirmation: false,
  },
})
export class SuperUserNoConfirmation {}
