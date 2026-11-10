// Generates cases.json, the expectations of NedbDifferentialTest, by running random queries, cursors and
// modifiers on the real @seald-io/nedb 4.0.2 (the version the TypeScript local provider pins).
//
//   node generate.js <path to @seald-io/nedb 4.0.2 package directory> > cases.json
//
// The output is deterministic for a given seed. Regular expressions are encoded as {"$$regex": [source, flags]},
// dates as NeDB writes them ({"$$date": millis}).
'use strict'
const path = require('path')
const nedbDirectory = process.argv[2]
const Datastore = require(path.join(nedbDirectory, 'index.js'))
const model = require(path.join(nedbDirectory, 'lib', 'model.js'))

let seed = 20261110
function random() {
  // mulberry32
  seed |= 0
  seed = (seed + 0x6d2b79f5) | 0
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
const chance = (p) => random() < p
const pick = (items) => items[Math.floor(random() * items.length)]
const integer = (min, max) => min + Math.floor(random() * (max - min + 1))

const DATES = [new Date(0), new Date(1700000000000), new Date(1800000000000)]
const LEAVES = [0, 1, 2, -1, 1.5, 10, 100, 'a', 'b', 'abc', 'Abc', 'ñu', '10', '', true, false, null]
const leaf = () => (chance(0.05) ? pick(DATES) : pick(LEAVES))
const REGEXES = [['^a', ''], ['b', ''], ['A', 'i'], ['ñ', ''], ['^$', ''], ['\\d', ''], ['[ab]c', ''], ['c$', ''],
  ['^A', 'i'], ['1', '']]
const regex = () => { const [source, flags] = pick(REGEXES); return new RegExp(source, flags) }
const PATHS = ['a', 'b', 'c', 'n', 'n.x', 'n.y', 'arr', 'arr.0', 'arr.1', 'objs', 'objs.x', 'objs.0.x', 'objs.y',
  'missing', 'n.x.z']

function document(id) {
  const doc = { _id: id }
  if (chance(0.7)) doc.a = leaf()
  if (chance(0.7)) doc.b = leaf()
  if (chance(0.7)) doc.c = chance(0.5) ? leaf() : Array.from({ length: integer(0, 3) }, leaf)
  if (chance(0.7)) doc.n = chance(0.7) ? { x: leaf(), y: leaf() } : leaf()
  if (chance(0.7)) doc.arr = Array.from({ length: integer(0, 4) }, leaf)
  if (chance(0.7)) doc.objs = Array.from({ length: integer(0, 3) }, () => ({ x: leaf(), y: leaf() }))
  return doc
}

function operand() {
  const r = random()
  if (r < 0.12) return { $lt: leaf() }
  if (r < 0.24) return { $gt: leaf() }
  if (r < 0.30) return { $lte: leaf() }
  if (r < 0.36) return { $gte: leaf() }
  if (r < 0.46) return { $ne: leaf() }
  if (r < 0.56) return { $in: Array.from({ length: integer(0, 3) }, leaf) }
  if (r < 0.62) return { $nin: Array.from({ length: integer(0, 3) }, leaf) }
  if (r < 0.72) return { $exists: pick([true, false, 0, 1, '', 'x']) }
  if (r < 0.80) return { $regex: regex() }
  if (r < 0.85) return { $size: integer(0, 3) }
  if (r < 0.95) return { $elemMatch: chance(0.4) ? leaf() : chance(0.5) ? { x: leaf() } : { x: { $gt: leaf() } } }
  if (r < 0.97) return { $gt: 1, $lt: leaf() }
  if (r < 0.98) return { $gt: 1, x: 2 }
  if (r < 0.99) return { $foo: 1 }
  return { $in: leaf() }
}

function fieldValue() {
  const r = random()
  if (r < 0.35) return leaf()
  if (r < 0.40) return Array.from({ length: integer(0, 2) }, leaf)
  if (r < 0.45) return { x: leaf() }
  if (r < 0.50) return regex()
  return operand()
}

function query(depth = 0) {
  const result = {}
  const parts = integer(depth === 0 ? 0 : 1, 3)
  for (let i = 0; i < parts; i++) {
    if (depth < 2 && chance(0.2)) {
      const r = random()
      if (r < 0.4) result.$and = Array.from({ length: integer(1, 3) }, () => query(depth + 1))
      else if (r < 0.8) result.$or = Array.from({ length: integer(1, 3) }, () => query(depth + 1))
      else result.$not = query(depth + 1)
    } else {
      result[pick(PATHS)] = fieldValue()
    }
  }
  return result
}

function cursorOptions() {
  const options = {}
  if (chance(0.6)) {
    options.sort = {}
    for (let i = integer(1, 2); i > 0; i--) options.sort[pick(PATHS)] = pick([1, -1])
  }
  if (chance(0.3)) options.skip = integer(1, 3)
  if (chance(0.4)) options.limit = integer(1, 4)
  if (chance(0.3)) {
    options.projection = chance(0.5) ? { a: 1, 'n.x': 1 } : { b: 0, 'objs': 0 }
    if (chance(0.3)) options.projection._id = 0
  }
  return options
}

const MODIFY_PATHS = ['a', 'n.x', 'arr', 'arr.1', 'objs.0.x', 'new.deep', 'c']
function modifier() {
  const r = random()
  const field = pick(MODIFY_PATHS)
  if (r < 0.15) return { $set: { [field]: chance(0.7) ? leaf() : { z: leaf() } } }
  if (r < 0.25) return { $unset: { [field]: true } }
  if (r < 0.40) return { $inc: { [field]: chance(0.9) ? pick([1, -2, 0.5]) : 'x' } }
  if (r < 0.55) return { $push: { [field]: chance(0.6) ? leaf() : { $each: [leaf(), leaf()], ...(chance(0.5) ? { $slice: pick([-1, 0, 2]) } : {}) } } }
  if (r < 0.65) return { $addToSet: { [field]: chance(0.6) ? leaf() : { $each: [leaf(), leaf()] } } }
  if (r < 0.72) return { $pop: { [field]: pick([1, -1, 0, 'x']) } }
  if (r < 0.80) return { $pull: { [field]: chance(0.6) ? leaf() : { $gt: leaf() } } }
  if (r < 0.87) return { $min: { [field]: leaf() } }
  if (r < 0.94) return { $max: { [field]: leaf() } }
  if (r < 0.97) return { a: leaf(), b: leaf() }
  return { $set: { a: 1 }, b: 2 }
}

function encode(value) {
  if (value instanceof RegExp) return { $$regex: [value.source, value.flags] }
  if (value instanceof Date) return { $$date: value.getTime() }
  if (Array.isArray(value)) return value.map(encode)
  if (value !== null && typeof value === 'object') {
    const out = {}
    for (const key of Object.keys(value)) out[key] = encode(value[key])
    return out
  }
  return value
}

async function main() {
  const datasets = []
  for (let d = 0; d < 30; d++) {
    const docs = Array.from({ length: integer(4, 12) }, (_, i) => document('id' + String(i).padStart(2, '0') + d))
    const db = new Datastore()
    await db.insertAsync(docs.map((doc) => model.deepCopy(doc)))
    const cases = []
    for (let q = 0; q < 25; q++) {
      const theQuery = query()
      const options = cursorOptions()
      const item = { query: encode(theQuery), options: encode(options) }
      try {
        let cursor = db.findAsync(theQuery, options.projection || {})
        if (options.sort) cursor = cursor.sort(options.sort)
        if (options.skip) cursor = cursor.skip(options.skip)
        if (options.limit) cursor = cursor.limit(options.limit)
        const found = await cursor
        item.result = found.map((doc) => model.serialize(doc))
        item.count = await db.countAsync(theQuery)
      } catch (e) {
        item.error = e.message
      }
      cases.push(item)
    }
    const modifications = []
    for (let m = 0; m < 8; m++) {
      const target = pick(docs)
      const update = modifier()
      const item = { document: encode(target), update: encode(update) }
      try {
        item.result = model.serialize(model.modify(model.deepCopy(target), update))
      } catch (e) {
        item.error = e.message
      }
      modifications.push(item)
    }
    datasets.push({ documents: docs.map((doc) => model.serialize(doc)), cases, modifications })
  }
  const files = []
  for (let f = 0; f < 6; f++) files.push(await persistence(f))
  process.stdout.write(JSON.stringify({ generator: 'generate.js with @seald-io/nedb 4.0.2', datasets, files }) + '\n')
}

// A data file written by NeDB (inserts, replacements, modifier updates, removals, index creation and removal), the
// state NeDB loads from it, and the compacted file NeDB rewrites on load.
async function persistence(number) {
  const fs = require('fs')
  const os = require('os')
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'nedb-differential-'))
  const filename = path.join(directory, 'data.json')
  const db = new Datastore({ filename })
  await db.loadDatabaseAsync()
  if (number % 2 === 0) await db.ensureIndexAsync({ fieldName: 'a', unique: true, sparse: true })
  if (number % 3 === 0) await db.ensureIndexAsync({ fieldName: 'expires', expireAfterSeconds: 3600 })
  const ids = []
  for (let i = 0; i < 40; i++) {
    const r = random()
    if (ids.length === 0 || r < 0.45) {
      const doc = document(undefined)
      delete doc._id
      if (chance(0.5)) doc._id = 'f' + number + '-' + i
      if (doc.a !== undefined && number % 2 === 0) doc.a = 'unique-' + i
      try {
        const inserted = await db.insertAsync(doc)
        ids.push(inserted._id)
      } catch (e) {
        // a unique violation leaves nothing behind
      }
    } else if (r < 0.65) {
      const replacement = document(undefined)
      delete replacement._id
      delete replacement.a
      await db.updateAsync({ _id: pick(ids) }, replacement, {})
    } else if (r < 0.8) {
      try {
        await db.updateAsync({ _id: pick(ids) }, { $set: { 'n.x': leaf(), b: leaf() }, $inc: { counter: 1 } }, {})
      } catch (e) {
        // modifier errors leave the document unchanged
      }
    } else if (r < 0.95) {
      const id = pick(ids)
      await db.removeAsync({ _id: id }, {})
      ids.splice(ids.indexOf(id), 1)
    } else if (number % 2 === 0) {
      await db.removeIndexAsync('a')
    }
  }
  const raw = fs.readFileSync(filename, 'utf8')
  const reloaded = new Datastore({ filename })
  await reloaded.loadDatabaseAsync()
  const state = reloaded.getAllData().map((doc) => model.serialize(doc))
  const compacted = fs.readFileSync(filename, 'utf8')
  const indexes = Object.keys(reloaded.indexes)
  fs.rmSync(directory, { recursive: true })
  return { raw, state, compacted, indexes }
}

main()
