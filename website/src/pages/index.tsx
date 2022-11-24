import Layout from '@theme/Layout'
import React from 'react'

export default function Home(): JSX.Element {
  return (
    <Layout wrapperClassName="max-w-3xl mx-auto">
      <img className="w-72 mt-12" src="img/booster-logo.png" alt="Booster Logo" />
      <h1 className="mt-24 mb-14 text-3xl">
        <strong>Build serverless event-sourcing microservices in minutes</strong> instead of months!
      </h1>
      <ul className="flex flex-col gap-4 ml-4">
        <li style={{ listStyleImage: 'url("img/blue-check.svg")' }} className="text-xl text-blue-700">
          Open-Source & Free to use
        </li>
        <li style={{ listStyleImage: 'url("img/blue-check.svg")' }} className="text-xl text-blue-700">
          Runs in your account (AWS · Azure)
        </li>
        <li style={{ listStyleImage: 'url("img/blue-check.svg")' }} className="text-xl text-blue-700">
          CQRS + ES semantics
        </li>
        <li style={{ listStyleImage: 'url("img/blue-check.svg")' }} className="text-xl text-blue-700">
          GraphQL API & Infrastructure inferred from code
        </li>
        <li style={{ listStyleImage: 'url("img/blue-check.svg")' }} className="text-xl text-blue-700">
          It scaaaaales!!!
        </li>
      </ul>
      <p className="mt-20 text-xl">
        Booster is an <strong>open-source</strong> minimalistic <strong>TypeScript</strong> framework to build
        <strong>event-sourced</strong> services with the minimal amount of code possible, but don't let its innocent
        appearance fool you; Booster analyzes the semantics of your code, sets up the optimal infrastructure to run your
        application at scale, and even generates a fully-working GraphQL API for you – don't even mind about writing the
        resolvers or maintaining your GraphQL schema, it will do that for you too.
      </p>
      <p className="mt-20 text-xl">
        And have we mentioned it's all open-source and free? But not free like you have a few build minutes per month or
        anything like that, we mean, real free. Everything remains between you, your CI/CD scripts (wherever you want to
        put them), and your own cloud accounts. Nothing is hidden under the carpet, you can{' '}
        <span>
          <a href="">visit the Github repository</a>
        </span>{' '}
        and see every single detail.
      </p>
      <button>Build your first Booster app in 10m!</button>
      <h2>demo playlist</h2>
    </Layout>
  )
}
