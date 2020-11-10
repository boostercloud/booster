import * as deployer from './deploy'
import * as nuker from './nuke'

// Re-exported in this way to allow replacing (mocking) them in tests
export const deploy = deployer.deploy
export const nuke = nuker.nuke
