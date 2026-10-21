export default defineAppConfig({
  seo: {
    title: 'kotlin-artisan',
    description: 'An artisan-style command line for Android projects. Type a word and the phone switches to dark mode, takes a screenshot, installs the build, or shows missing translations.',
  },
  header: {
    title: 'kotlin-artisan',
    logo: {
      alt: 'kotlin-artisan',
      light: '/logo.svg',
      dark: '/logo.svg',
    },
  },
  socials: {
    github: 'https://github.com/ravisorathiya/kotlin-artisan',
  },
  github: {
    url: 'https://github.com/ravisorathiya/kotlin-artisan',
    branch: 'main',
    rootDir: 'website',
  },
  toc: {
    title: 'On this page',
    bottom: {
      title: 'Project',
      links: [
        { icon: 'i-lucide-star', label: 'Star on GitHub', to: 'https://github.com/ravisorathiya/kotlin-artisan', target: '_blank' },
        { icon: 'i-lucide-tag', label: 'Latest release', to: 'https://github.com/ravisorathiya/kotlin-artisan/releases/latest', target: '_blank' },
        { icon: 'i-lucide-bug', label: 'Report an issue', to: 'https://github.com/ravisorathiya/kotlin-artisan/issues/new', target: '_blank' },
      ],
    },
  },
  ui: {
    colors: {
      primary: 'emerald',
      neutral: 'zinc',
    },
  },
})
