const process = require('process')
const { Octokit } = require("@octokit/rest")
const util = require('util')
const exec = util.promisify(require('child_process').exec)

const octokit = new Octokit({
    auth: process.env.GITHUB_TOKEN,
    userAgent: 'booster-mirror v0.0.0'
})

async function main(argv){
    console.log(argv)
    if(argv.length > 3){
        throw new Error("usage: <script> <PR number>")
    }
    const prNumber = argv[argv.length - 1]
    const pr = await octokit.pulls.get({
        owner: 'theam',
        repo: 'booster',
        pull_number: prNumber
    })
    await exec(`git push public ${pr.data.head.ref}`)
    const newPr = await octokit.pulls.create({
        owner: 'theam',
        repo: 'booster',
        title: pr.data.title,
        head: pr.data.head.ref,
        base: 'master-mirror',
        body: pr.data.body
    })
    await octokit.pulls.merge({
        owner: 'theam',
        repo: 'booster',
        pull_number: newPr.data.number
    })
    await octokit.issues.createComment({
        owner: 'theam',
        repo: 'booster',
        issue_number: prNumber,
        body: `This PR was merged in the public repo: ${newPr.data.url}`
    })
    await octokit.issues.addLabels({
        owner: 'theam',
        repo: 'booster',
        issue_number: prNumber,
        labels: 'published-to-public'
    })
}

main(process.argv)
