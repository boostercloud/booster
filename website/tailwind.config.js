/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    container: false,
    extend: {
      fontFamily: {
        sans: ['IamwriterquattroS', 'sans-serif'],
        mono: ['Jetbrains Mono', 'monospace'],
      },
    },
  },
  plugins: [],
}
