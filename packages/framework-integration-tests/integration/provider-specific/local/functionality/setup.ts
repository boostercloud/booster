import { expect } from 'chai'
import { readPIDFor } from '../../../helper/file-helper'
import { sandboxPath } from '../constants'

before(async () => {
  const pid: number = readPIDFor(sandboxPath)
  expect(pid).not.to.be.undefined
})
