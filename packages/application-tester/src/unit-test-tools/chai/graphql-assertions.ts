import { Assertion } from 'chai'

Assertion.addMethod('acceptsQuery', function (query: string) {
  const obj = this._obj

  new Assertion(obj).to.has.property('boosterServeGraphQL')

  obj.boosterServeGraphQL(query).then(result => {
    this._attrs['result'] = result
    this.assert()
  })
})
