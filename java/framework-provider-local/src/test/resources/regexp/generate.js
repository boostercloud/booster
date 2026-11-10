// Generates cases.json, the expectations of JsRegExpDifferentialTest: random JavaScript regular expressions (built
// from tokens where JavaScript and Java differ) tested against random inputs with V8's RegExp.
//
//   node generate.js > cases.json
'use strict'
let seed = 424242
function random() {
  seed |= 0
  seed = (seed + 0x6d2b79f5) | 0
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
const pick = (items) => items[Math.floor(random() * items.length)]
const integer = (min, max) => min + Math.floor(random() * (max - min + 1))

const TOKENS = ['a', 'b', 'A', 'é', '{', '}', ']', '-', '^', '$', '.', '|', '(', ')', '(?:', '(?=', '(?!',
  '(?<=', '(?<!', '(?<n>', '[', '[^', '[a-', '*', '+', '?', '{2}', '{1,}', '{,2}', '{1,2}', '\\d', '\\D', '\\s',
  '\\S', '\\w', '\\W', '\\b', '\\B', '\\1', '\\2', '\\0', '\\01', '\\8', '\\x41', '\\x4', '\\u0041', '\\u00', '\\c',
  '\\cA', '\\ca', '\\k', '\\k<n>', '\\.', '\\-', '\\e', '\\v', '\\n', '\\t', '\\/', '\\[', '\\]', '\\{', '\\p{L}', ' ',
  ' ', 'x', '1', '_', '&&', '[]', '[^]', '\\Q', '#']
const INPUT_CHARACTERS = ['a', 'b', 'A', 'é', 'É', '{', '}', ']', '[', '-', '^', '$', '.', '\n', '\r', ' ',
  ' ', ' ', '\u0085', '\u000b', 'x', '1', '2', '_', '&', '\u0001', '\u0000', 'e', 'k', 'Q', '#', '\\', 'c',
  'p', 'L', '\b', '/', '\t']
const FLAGS = ['', '', '', 'i', 'm', 's', 'im']

const cases = []
for (let i = 0; i < 1500; i++) {
  let source = ''
  for (let t = integer(1, 7); t > 0; t--) source += pick(TOKENS)
  const flags = pick(FLAGS)
  const inputs = []
  for (let n = 0; n < 4; n++) {
    let input = ''
    for (let c = integer(0, 6); c > 0; c--) input += pick(INPUT_CHARACTERS)
    inputs.push(input)
  }
  try {
    const regex = new RegExp(source, flags)
    cases.push({ source, flags, results: inputs.map((input) => [input, regex.test(input)]) })
  } catch (e) {
    cases.push({ source, flags, error: e.message })
  }
}
process.stdout.write(JSON.stringify({ generator: 'generate.js with ' + process.version, cases }) + '\n')
