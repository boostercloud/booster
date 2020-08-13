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
    skipConfirmation: false,
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
    skipConfirmation: false,
  },
})
export class SuperUser {}

@Role({
  auth: {
    signUpMethods: ['email', 'phone'],
    skipConfirmation: true,
  },
})
export class SuperUserNoConfirmation {}
