import Layout from '@theme/Layout'
import React from 'react'

export default function Home(): JSX.Element {
  return (
    <Layout wrapperClassName="hp-layout">
      <img className="hp-hero" src="img/booster-logo.png" alt="Booster Logo" />
      <h1 className="hp-header">
        <strong>Build serverless event-sourcing microservices in minutes</strong> instead of months!
      </h1>
      <ul className="hp-list">
        <li className="hp-listitem">Open-Source & Free to use</li>
        <li className="hp-listitem">Runs in your account (AWS · Azure)</li>
        <li className="hp-listitem">CQRS + ES semantics</li>
        <li className="hp-listitem">GraphQL API & Infrastructure inferred from code</li>
        <li className="hp-listitem">It scaaaaales!!!</li>
      </ul>
      <p className="hp-text">
        Booster is an <strong>open-source</strong> minimalistic <strong>TypeScript</strong> framework to build
        <strong>event-sourced</strong> services with the minimal amount of code possible, but don't let its innocent
        appearance fool you; Booster analyzes the semantics of your code, sets up the optimal infrastructure to run your
        application at scale, and even generates a fully-working GraphQL API for you – don't even mind about writing the
        resolvers or maintaining your GraphQL schema, it will do that for you too.
      </p>
      <p className="hp-text">
        And have we mentioned it's all open-source and free? But not free like you have a few build minutes per month or
        anything like that, we mean, real free. Everything remains between you, your CI/CD scripts (wherever you want to
        put them), and your own cloud accounts. Nothing is hidden under the carpet, you can{' '}
        <span>
          <a href="">visit the Github repository</a>
        </span>{' '}
        and see every single detail.
      </p>
      <button className="app-cta">Build your first Booster app in 10m!</button>
      <section className="hp-section">
        <h2 className="hp-section-header">demo playlist</h2>
      </section>
      <section className="hp-section">
        <h2 className="hp-section-header">contribute</h2>
      </section>
      <section className="hp-section">
        <h2 className="hp-section-header">keep yourself updated</h2>
        <p className="cta-text">Info? Demo? Contributing? Sponsoring?</p>
        <div>
          <a href="" className="app-cta">
            Contact
          </a>
        </div>
      </section>
    </Layout>
  )
}
