// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    'introduction',
    {
      type: 'category',
      label: 'Getting Started',
      link: {
        type: 'generated-index',
      },
      items: ['getting-started/installation', 'getting-started/configuration', 'getting-started/coding'],
    },
    'booster-architecture',
    'features',
    'going-deeper',
    'testing-booster-applications',
    'frequently-asked-questions',
    'contributing',
  ],
}

module.exports = sidebars
