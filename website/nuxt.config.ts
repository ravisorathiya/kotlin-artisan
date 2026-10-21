import { readFileSync } from 'node:fs'

// The site shows the CLI's version, so read it from the Gradle build instead of keeping a second copy.
const version = readFileSync(new URL('../build.gradle.kts', import.meta.url), 'utf-8')
  .match(/^version = "(.+)"/m)?.[1] ?? '0.0.0'

export default defineNuxtConfig({
  extends: ['docus'],
  site: {
    url: 'https://kotlin-artisan.netlify.app',
    name: 'kotlin-artisan',
  },
  // The site is static on Netlify, so there is no server for the MCP endpoint.
  mcp: {
    enabled: false,
  },
  runtimeConfig: {
    public: {
      docus: { version },
    },
  },
  content: {
    build: {
      markdown: {
        highlight: {
          // Added to Docus's own list (bash, shell, json, md, ...); `text` needs no grammar.
          langs: ['bash', 'diff', 'json', 'js', 'ts', 'html', 'css', 'vue', 'shell', 'mdc', 'md', 'yaml', 'powershell', 'kotlin', 'xml', 'toml'],
        },
      },
    },
  },
})
