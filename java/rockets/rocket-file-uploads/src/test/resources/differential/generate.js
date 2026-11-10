// Generates the differential corpora of the Java port: minimatch 3.1.5 (default options) and brace-expansion 1.1.16.
// Usage: node generate.js <output directory>
const fs = require('fs')
const path = require('path')
const minimatch = require('minimatch')
const expand = require('brace-expansion')

let seed = 20261110
function random() {
  // mulberry32
  seed |= 0
  seed = (seed + 0x6d2b79f5) | 0
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
function pick(list) {
  return list[Math.floor(random() * list.length)]
}
function build(tokens, maxLength) {
  const length = Math.floor(random() * maxLength)
  let out = ''
  for (let i = 0; i < length; i++) out += pick(tokens)
  return out
}

const patternTokens = ['a', 'b', 'c', 'x', '.', '*', '**', '?', '/', '/', '[', ']', '[a-c]', '[!a]', '[^b]', '!', '{', '}',
  '{a,b}', '{1..3}', ',', '(', ')', '|', '+', '@', '+(', '@(', '!(', '*(', '?(', '\\', '-', '#', ' ', '..', '$', '^',
  '[z-a]', '[]', '[[:alpha:]]', 'ä', '\t']
const pathTokens = ['a', 'b', 'c', 'x', 'ab', '.', '..', '.a', '/', '/', '/', '[', ']', '*', '-', '1', '2', '{', '}',
  'a,b', '(', ')', '|', '+', '!', '#', ' ', 'ä', '\\', '$']

const fixedPatterns = ['', '*', '**', 'exact', '*/test', 'test/*', 'exact/**', 'main/**/other', 'deep/**/sub/**', '!b',
  '{a,b}', 'a?', '[a-f]older', 'a/', '#comment', '  exact  ', '!!a', '!', '**/x/**/**', 'a/**/**/b', '*[', 'a*[**',
  '+(a|b)', '!(a|b)', '*.!(x).!(y|z)', '*(*.js|!(*.json))', '[!a]*', '[]a]', '[z-a]', 'a\\*b', 'a\\', '@(a)', '.*',
  '**/.*', 'a/{b,c}/d', '{,a}', '{a}', 'x{{a,b}}y', '{1..3..0}', '{a..c}', '\\{a,b}', 'a{},b}c', '{},a}b', '${a,b}']
const fixedPaths = ['', '/', 'a', 'b', 'exact', 'exact/', '/exact', 'exact//', './exact', 'exact/other', 'exact/other/',
  '.hidden', 'a/.hidden', 'Client1', 'client1', 'ab', 'x/y', 'x/y/', 'folder', 'a/b', 'a/b/', 'main/1/2/other',
  'deep/1/sub/1/', 'a.xyz.yz', 'x[undefined', 'a\\b', '..', 'a/..', 'a/./b', 'test.js', 'x.json', 'x/a/b', 'x']

const cases = []
function record(pattern, file) {
  let result
  try {
    result = minimatch(file, pattern)
  } catch (e) {
    result = 'error:' + e.message
  }
  cases.push({ p: pattern, f: file, r: result })
}

for (const p of fixedPatterns) for (const f of fixedPaths) record(p, f)
for (let i = 0; i < 2500; i++) {
  const pattern = build(patternTokens, 7)
  const paths = [build(pathTokens, 6), build(pathTokens, 4), pattern.replace(/[*?]/g, 'a')]
  for (const f of paths) record(pattern, f)
}

const braceCases = []
const braceTokens = ['a', 'b', '{', '}', ',', '..', '1', '2', '-', '0', '\\', '$', 'z', 'A', '{a,b}', '{1..3}', '{a..c}',
  '{01..03}', '{3..1}', '{a,}', '{,}', '{}', '\\{', '\\}', '\\,']
const fixedBraces = ['', 'a{b,c}d', 'a{b,}c', 'a{0..3}d', 'a{b,c{d,e}f}g', 'a{b,c}d{e,f}g', 'a{2..}b', 'a{b}c', '{a},b}',
  '{a},b},c}', '{1..10..3}', '{10..1..-3}', '{-01..5}', '{a..e..2}', '{Z..a}', '{1..3..0}', 'x{{a,b}}y', '${a,b}c',
  '{},a}b', 'a{},b}c', '{{a,b},c}', '{a,{b,c}}', '\\{a,b}', '{a\\,b,c}']
function recordBrace(str) {
  let result
  try {
    result = expand(str)
  } catch (e) {
    result = 'error:' + e.message
  }
  braceCases.push({ s: str, r: result })
}
for (const s of fixedBraces) recordBrace(s)
for (let i = 0; i < 1500; i++) recordBrace(build(braceTokens, 6))

const out = process.argv[2]
fs.mkdirSync(out, { recursive: true })
fs.writeFileSync(path.join(out, 'minimatch-3.1.5-corpus.jsonl'), cases.map((c) => JSON.stringify(c)).join('\n') + '\n')
fs.writeFileSync(path.join(out, 'brace-expansion-1.1.16-corpus.jsonl'),
  braceCases.map((c) => JSON.stringify(c)).join('\n') + '\n')
console.log(cases.length, 'minimatch cases', cases.filter((c) => c.r === true).length, 'true',
  cases.filter((c) => typeof c.r === 'string').length, 'errors;', braceCases.length, 'brace cases')
