// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer/themes/github')
const darkCodeTheme = require('prism-react-renderer/themes/dracula')

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Booster Framework',
  url: 'https://your-docusaurus-test-site.com',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.ico',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'boostercloud', // Usually your GitHub org/user name.
  projectName: 'booster', // Usually your repo name.

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl: 'https://github.com/facebook/docusaurus/tree/main/packages/create-docusaurus/templates/shared/',
        },
        blog: {
          showReadingTime: true,
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl: 'https://github.com/facebook/docusaurus/tree/main/packages/create-docusaurus/templates/shared/',
        },
        theme: {
          customCss: [require.resolve('./src/custom.css')],
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        logo: {
          alt: 'Booster Logo',
          src: 'img/booster-logo.png',
        },
        items: [
          {
            type: 'doc',
            docId: 'introduction',
            position: 'left',
            label: 'Docs',
          },
          {
            href: 'https://github.com/facebook/docusaurus',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        copyright: `Booster is an open-source initiative from <a href="https://www.theagilemonkeys.com/">The Agile Monkeys.</a>`,
        links: [
          {
            title: 'Join Us',
            items: [
              {
                label: 'Github',
                to: 'https://github.com/boostercloud',
              },
              {
                label: 'Discord',
                to: 'https://discord.gg/bDY8MKx',
              },
              {
                label: 'Twitter',
                to: 'https://twitter.com/boostthecloud',
              },
              {
                label: 'Linkedin',
                to: 'https://www.linkedin.com/company/boosterin-labs/',
              },
            ],
          },
          {
            title: 'Resources',
            items: [
              {
                label: 'Docs',
                to: 'https://docs.booster.cloud/',
              },
              {
                label: 'YouTube',
                to: 'https://www.youtube.com/channel/UCpUTONI8OG19pr9A4cn35DA',
              },
              {
                label: 'Podcast',
                to: 'https://www.youtube.com/channel/UCxUYk1SVyNRCGNV-9SYjEFQ',
              },
              {
                label: 'Press kit',
                to: 'https://www.dropbox.com/sh/wyt7rdq7l873iyl/AADzGIae_adkFyjB_VTW2UYka?dl=0',
              },
            ],
          },
          {
            title: 'Read more',
            items: [
              {
                label: 'Dev.to',
                to: 'https://dev.to/boostercloud',
              },
              {
                label: 'Medium',
                to: 'https://medium.com/@theam',
              },
            ],
          },
        ],
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
      },
    }),

  plugins: [
    async function TailwindCSSPlugin(context, options) {
      return {
        name: 'docusaurus-tailwindcss',
        configurePostCss(postcssOptions) {
          // Appends TailwindCSS and AutoPrefixer.
          postcssOptions.plugins.push(require('tailwindcss'))
          postcssOptions.plugins.push(require('autoprefixer'))
          return postcssOptions
        },
      }
    },
  ],
}

module.exports = config
