import * as chai from 'chai'
import * as sinonChai from 'sinon-chai'
import * as chaiAsPromised from 'chai-as-promised'
import * as chaiArrays from 'chai-arrays'

chai.use(sinonChai)
chai.use(chaiAsPromised)
chai.use(chaiArrays)

export const expect = chai.expect
