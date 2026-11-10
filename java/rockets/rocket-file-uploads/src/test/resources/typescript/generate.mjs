// Generates the TypeScript-side expectations of the Java port's parity tests.
//
// Usage (Node >= 23.6, which runs TypeScript files by stripping their types):
//   node generate.mjs <rocket-file-uploads repository> <output directory>
//
// 1. functions.js / function-name-errors.json: the code TypeScript deploys to the rocket's function app, produced by
//    importing the rocket's own generator (packages/rocket-file-uploads-azure-infrastructure/src/functions/
//    rocket-files-file-uploaded-function.ts) and joining it as Functions.mountFunctionsV4 does, for the README's two
//    storages; and the messages of the name validation.
// 2. sas-vectors.jsonl: user delegation SAS query strings (version 2018-11-09) written from the public specification
//    ("Create a user delegation SAS", string-to-sign of versions 2018-11-09 to 2020-01-10) with Node's crypto and
//    encodeURIComponent, in the parameter order of @azure/storage-blob's SASQueryParameters.toString(). This is an
//    independent implementation: nothing is taken from the Java code.
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { pathToFileURL } from 'node:url'

const [repository, output] = process.argv.slice(2)
if (!repository || !output) {
  console.error('Usage: node generate.mjs <rocket-file-uploads repository> <output directory>')
  process.exit(2)
}
fs.mkdirSync(output, { recursive: true })

// --- 1. functions.js -------------------------------------------------------------------------------------------
const generatorFile = path.join(repository, 'packages/rocket-file-uploads-azure-infrastructure/src/functions',
  'rocket-files-file-uploaded-function.ts')
const { RocketFilesFileUploadedFunction } = await import(pathToFileURL(generatorFile).href)
const userConfiguration = [
  { storageName: 'clientst', containerName: 'rocketfiles', directories: ['client1', 'client2'] },
  { storageName: 'cmsst', containerName: 'rocketfiles', directories: ['cms1', 'cms2'] },
]
const functionsCode = RocketFilesFileUploadedFunction.sharedImports().concat(
  userConfiguration
    .map((uc) => RocketFilesFileUploadedFunction.generateFunctionsCode(uc.containerName, uc.storageName))
    .join('\n'))
fs.writeFileSync(path.join(output, 'functions.js'), functionsCode)

const errors = []
for (const [containerName, storageName] of [['Bad', 'x'], ['x', 'Bad'], ['', 'x'], ['ok', ''], ['a.b', 'c'],
  ["x'y", 'z'], ['Bad', 'Bad']]) {
  try {
    RocketFilesFileUploadedFunction.generateFunctionsCode(containerName, storageName)
    errors.push({ containerName, storageName, error: null })
  } catch (e) {
    errors.push({ containerName, storageName, error: e.message })
  }
}
fs.writeFileSync(path.join(output, 'function-name-errors.json'), JSON.stringify(errors, null, 2) + '\n')

// --- 2. SAS vectors --------------------------------------------------------------------------------------------
// truncatedISO8061Date(date, false): ISO 8601 without milliseconds
const iso = (date) => date.toISOString().replace(/\.\d{3}Z$/, 'Z')
const canonicalOrder = 'racwdxtmeiy'
const vectors = [
  { account: 'clientst', container: 'rocketfiles', blob: 'client1/myfile.txt', permissions: 'w',
    start: '2026-11-10T10:00:00.123Z', expiry: '2026-11-10T10:01:26.523Z' },
  { account: 'clientst', container: 'rocketfiles', blob: 'client1/myfile.txt', permissions: 'r',
    start: '2026-11-10T10:00:00.950Z', expiry: '2026-11-10T10:01:27.350Z' },
  { account: 'cmsst', container: 'media-assets', blob: 'active-pool/sales fdm/résumé + notes (1).pdf',
    permissions: 'w', start: '2026-02-28T23:59:59.999Z', expiry: '2026-03-01T00:01:26.399Z' },
  { account: 'testrfproduction', container: 'c', blob: 'a/b/c/%41~!*\'().txt', permissions: 'wr',
    start: '2026-12-31T23:59:00.000Z', expiry: '2027-01-01T00:00:26.400Z' },
  { account: 'MixedCaseApprfdev', container: 'files_2', blob: '日本/😀.bin', permissions: 'r',
    start: '2026-06-01T12:00:00.000Z', expiry: '2026-06-01T12:01:26.400Z' },
]
const keyValues = [
  'Ym9vc3Rlci1yb2NrZXQtZmlsZXMtdGVzdC1rZXktMzI=',
  crypto.createHash('sha256').update('second key').digest('base64'),
  crypto.createHash('sha256').update('third key').digest('base64'),
]
const lines = []
vectors.forEach((vector, index) => {
  const start = new Date(vector.start)
  const expiry = new Date(vector.expiry)
  const key = {
    signedObjectId: `0000000${index}-2222-3333-4444-555555555555`,
    signedTenantId: '66666666-7777-8888-9999-000000000000',
    signedStartsOn: iso(start),
    signedExpiresOn: iso(expiry),
    signedService: 'b',
    signedVersion: index % 2 === 0 ? '2025-11-05' : '2018-11-09',
    value: keyValues[index % keyValues.length],
  }
  const permissions = [...canonicalOrder].filter((p) => vector.permissions.includes(p)).join('')
  const stringToSign = [
    permissions, // signedPermissions
    iso(start), // signedStart
    iso(expiry), // signedExpiry
    `/blob/${vector.account}/${vector.container}/${vector.blob}`, // canonicalizedResource
    key.signedObjectId, // signedKeyObjectId
    key.signedTenantId, // signedKeyTenantId
    key.signedStartsOn, // signedKeyStart
    key.signedExpiresOn, // signedKeyExpiry
    key.signedService, // signedKeyService
    key.signedVersion, // signedKeyVersion
    '', // signedIP
    'https,http', // signedProtocol
    '2018-11-09', // signedVersion
    'b', // signedResource
    '', // signedSnapshotTime
    '', // rscc
    '', // rscd
    '', // rsce
    '', // rscl
    '', // rsct
  ].join('\n')
  const signature = crypto.createHmac('sha256', Buffer.from(key.value, 'base64')).update(stringToSign, 'utf8')
    .digest('base64')
  const query = [
    ['sv', '2018-11-09'], ['spr', 'https,http'], ['st', iso(start)], ['se', iso(expiry)],
    ['skoid', key.signedObjectId], ['sktid', key.signedTenantId], ['skt', key.signedStartsOn],
    ['ske', key.signedExpiresOn], ['sks', key.signedService], ['skv', key.signedVersion], ['sr', 'b'],
    ['sp', permissions], ['sig', signature],
  ].map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&')
  lines.push(JSON.stringify({ ...vector, key, stringToSign, signature, query }))
})
fs.writeFileSync(path.join(output, 'sas-vectors.jsonl'), lines.join('\n') + '\n')
console.log(`Wrote functions.js, function-name-errors.json and ${lines.length} SAS vectors to ${output}`)
