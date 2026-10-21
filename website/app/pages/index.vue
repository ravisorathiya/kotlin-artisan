<script setup lang="ts">
const title = 'Type a word. Your Android project does the rest.'
const description = 'kotlin-artisan is an artisan-style command line for Android projects: dark mode on the phone, screenshots, installs, missing translations, analytics events and releases, one word each.'

useSeo({ title: 'kotlin-artisan: a command line for Android projects', description, type: 'website' })
defineOgImage('Landing', { title, description })

const { version } = useRuntimeConfig().public.docus as { version: string }
const repo = 'https://github.com/ravisorathiya/kotlin-artisan'

const tour = [
  { cmd: 'artisan', note: 'type to search every command; Tab fills in, Enter runs' },
  { cmd: 'artisan doctor', note: 'Java, SDK, adb, phone, git: what is OK, what is missing' },
  { cmd: 'artisan app:install', note: 'build + install + launch the debug app (Ctrl+g i)' },
  { cmd: 'artisan app:dark on', note: 'dark theme on the phone' },
  { cmd: 'artisan app:shot', note: 'screenshot into the notes assets folder' },
  { cmd: 'artisan make:string call_ended "Call ended"', note: 'values/ + every values-xx/ at once' },
  { cmd: 'artisan check:strings', note: 'missing translations per module and language' },
  { cmd: 'artisan bump minor', note: '1.7 -> 1.8, versionCode +1' },
  { cmd: 'artisan open:firebase crash', note: 'Crashlytics for this project' },
]

const features = [
  { icon: 'i-lucide-zap', title: 'Zero setup', text: 'Run it anywhere inside a Gradle project. It finds the root, modules (Kotlin or Groovy DSL), app module, applicationId, version and Firebase project by itself.', to: '/getting-started/introduction' },
  { icon: 'i-lucide-smartphone', title: 'Phone switches for QA', text: 'Dark/light, font scale, rotation, per-app language, screenshots, recordings, logcat, typing, deep links, permissions, demo status bar, mirror.', to: '/commands/phone' },
  { icon: 'i-lucide-keyboard', title: 'Suggestions as you type', text: 'At your normal prompt `artisan da` shows `app:dark` in gray; → accepts. Or run `artisan` and search every command, typos included.', to: '/commands/overview' },
  { icon: 'i-lucide-factory', title: 'Generators', text: 'make:string for every locale, make:event with naming rules, make:screen, make:bundle, make:analytics, bump.', to: '/commands/make' },
  { icon: 'i-lucide-heart-pulse', title: 'Project health', text: 'Missing translations per module × language, firebaseEvent vs event.md, TODO/FIXME summary, lines of code, doctor.', to: '/commands/code-health' },
  { icon: 'i-lucide-terminal', title: 'Lives in your terminal', text: 'Ctrl+g hotkeys, project status in your prompt, git hooks that stop out-of-sync commits, clickable output paths.', to: '/commands/terminal' },
  { icon: 'i-lucide-list-checks', title: 'Pickers, not memorising', text: 'artisan app, artisan make, artisan open show just that group. Leave out a value and it shows the choices.', to: '/commands/overview' },
  { icon: 'i-lucide-bot', title: 'Built for AI agents too', text: 'Plain tab-separated output in pipes, plus an :ai workspace with plans, phases, bugs and event.md as cross-session memory.', to: '/guides/ai-agents' },
  { icon: 'i-lucide-puzzle', title: 'Your own commands', text: 'Add shell commands in artisan.json and they show up in the pickers, completion and hotkeys.', to: '/commands/custom-commands' },
]

const groups = [
  { name: 'app:', label: 'Phone', to: '/commands/phone' },
  { name: 'make:', label: 'Generators', to: '/commands/make' },
  { name: 'check:', label: 'Code health', to: '/commands/code-health' },
  { name: 'open:', label: 'Consoles', to: '/commands/open' },
  { name: 'build:', label: 'Release', to: '/commands/build' },
  { name: 'list:', label: 'Notes', to: '/commands/notes' },
]

const day = [
  { time: 'Morning', title: 'Where am I?', cmd: 'artisan status', text: 'The prompt already says [v1.8(17) phase_3 · 5 open · 2 verify].' },
  { time: 'Bug', title: 'See it on the phone', cmd: 'artisan app:reset-perms', text: 'First-run flow again, no reinstall. Then make:bug --shot.' },
  { time: 'Fix', title: 'Build it', cmd: 'artisan make:screen activity Settings', text: 'Class + layout + manifest entry, ViewBinding when on.' },
  { time: 'Commit', title: 'Nothing slips', cmd: 'git commit', text: 'The hook stops commits with event.md or translations out of sync.' },
  { time: 'Ship', title: 'Release', cmd: 'artisan build:release aab', text: 'Then open:firebase crash after the rollout.' },
]
</script>

<template>
  <div class="relative isolate overflow-hidden">
    <!-- Hero -->
    <section class="relative">
      <div aria-hidden="true" class="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(60rem_30rem_at_50%_-10%,color-mix(in_oklab,var(--color-emerald-500)_22%,transparent),transparent)]" />
      <div aria-hidden="true" class="pointer-events-none absolute inset-0 -z-10 bg-[linear-gradient(to_right,var(--ui-border)_1px,transparent_1px),linear-gradient(to_bottom,var(--ui-border)_1px,transparent_1px)] bg-[size:3.5rem_3.5rem] opacity-40 [mask-image:radial-gradient(ellipse_at_top,black_20%,transparent_70%)]" />

      <UContainer class="flex flex-col items-center gap-8 pt-20 pb-16 text-center sm:pt-28">
        <NuxtLink :to="`${repo}/releases/latest`" target="_blank" class="group inline-flex items-center gap-2 rounded-full border border-default bg-default/60 px-3 py-1 text-xs text-muted backdrop-blur transition hover:border-primary/50">
          <span class="rounded-full bg-primary/15 px-2 py-0.5 font-semibold text-primary">v{{ version }}</span>
          The daily Android loop, wired into the terminal
          <UIcon name="i-lucide-arrow-right" class="size-3 transition group-hover:translate-x-0.5" />
        </NuxtLink>

        <h1 class="max-w-4xl text-4xl font-bold tracking-tight text-highlighted sm:text-6xl lg:text-7xl">
          Type a word.
          <span class="block bg-gradient-to-r from-emerald-400 via-teal-400 to-sky-400 bg-clip-text text-transparent">Your Android project does the rest.</span>
        </h1>

        <p class="max-w-2xl text-lg text-muted sm:text-xl">
          An <code class="rounded bg-elevated px-1.5 py-0.5 font-mono text-base text-highlighted">artisan</code>-style command line for Android.
          Dark mode on the phone, a screenshot, the build installed, missing translations, Crashlytics: one word each.
          Written in Kotlin, runs on Windows, macOS and Linux.
        </p>

        <InstallCommand />

        <div class="flex flex-wrap justify-center gap-3">
          <UButton to="/getting-started/introduction" size="xl" trailing-icon="i-lucide-arrow-right">
            Get started
          </UButton>
          <UButton :to="repo" target="_blank" size="xl" color="neutral" variant="outline" icon="i-simple-icons-github">
            Star on GitHub
          </UButton>
        </div>
      </UContainer>
    </section>

    <!-- Terminal tour -->
    <UContainer class="pb-24">
      <div class="term-glow mx-auto max-w-4xl overflow-hidden rounded-2xl bg-term-bg text-left">
        <div class="flex items-center gap-2 border-b border-term-border px-4 py-3">
          <span class="size-3 rounded-full bg-red-400" />
          <span class="size-3 rounded-full bg-yellow-400" />
          <span class="size-3 rounded-full bg-emerald-400" />
          <span class="ml-3 font-mono text-xs text-term-dim">~/work/MyApp</span>
        </div>
        <div class="overflow-x-auto p-5 font-mono text-[13px] leading-7 sm:text-sm">
          <div class="text-term-dim">
            <span class="text-emerald-400">[v1.8-alpha(17) phase_3 · 5 open · 2 verify]</span> ~/work/MyApp
          </div>
          <div v-for="line in tour" :key="line.cmd" class="flex flex-wrap gap-x-3 whitespace-nowrap">
            <span><span class="select-none text-emerald-400">$ </span><span class="text-term-text">{{ line.cmd }}</span></span>
            <span class="text-term-dim"># {{ line.note }}</span>
          </div>
          <div><span class="select-none text-emerald-400">$ </span><span class="caret inline-block h-4 w-2 translate-y-0.5 bg-term-text" /></div>
        </div>
      </div>
    </UContainer>

    <!-- Real output -->
    <section class="border-y border-default bg-elevated/40 py-20">
      <UContainer>
        <div class="mx-auto mb-12 max-w-2xl text-center">
          <p class="mb-2 text-sm font-semibold uppercase tracking-wider text-primary">Real output</p>
          <h2 class="text-3xl font-bold tracking-tight text-highlighted sm:text-4xl">Readable tables, not walls of text</h2>
          <p class="mt-4 text-muted">Coloured boxed tables that fit your terminal. In a pipe or an AI agent, the same data prints as compact tab-separated rows.</p>
        </div>
        <div class="grid gap-6 lg:grid-cols-2">
          <figure v-for="shot in [{ src: '/img/app.svg', alt: 'artisan app: a numbered picker of every phone command', cap: 'artisan app' }, { src: '/img/strings.svg', alt: 'artisan check:strings: missing translations per module and language', cap: 'artisan check:strings' }, { src: '/img/doctor.svg', alt: 'artisan doctor: Java, SDK, adb, phone, git and notes checks', cap: 'artisan doctor' }, { src: '/img/stats.svg', alt: 'artisan stats: Kotlin and Java lines per module', cap: 'artisan stats' }]" :key="shot.src" class="group">
            <img :src="shot.src" :alt="shot.alt" loading="lazy" class="w-full rounded-xl shadow-lg ring-1 ring-term-border transition group-hover:-translate-y-1 group-hover:shadow-2xl">
            <figcaption class="mt-3 text-center font-mono text-xs text-muted">{{ shot.cap }}</figcaption>
          </figure>
        </div>
      </UContainer>
    </section>

    <!-- Features -->
    <section class="py-24">
      <UContainer>
        <div class="mx-auto mb-14 max-w-2xl text-center">
          <p class="mb-2 text-sm font-semibold uppercase tracking-wider text-primary">Why artisan</p>
          <h2 class="text-3xl font-bold tracking-tight text-highlighted sm:text-4xl">The clicks you do fifty times a day, gone</h2>
        </div>
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <NuxtLink v-for="f in features" :key="f.title" :to="f.to" class="group relative rounded-2xl border border-default bg-default p-6 transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-xl hover:shadow-primary/5">
            <div class="mb-4 inline-flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary ring-1 ring-primary/20">
              <UIcon :name="f.icon" class="size-5" />
            </div>
            <h3 class="mb-2 font-semibold text-highlighted">{{ f.title }}</h3>
            <p class="text-sm leading-6 text-muted">{{ f.text }}</p>
            <UIcon name="i-lucide-arrow-up-right" class="absolute top-6 right-6 size-4 text-dimmed opacity-0 transition group-hover:opacity-100" />
          </NuxtLink>
        </div>
      </UContainer>
    </section>

    <!-- Command groups -->
    <section class="pb-24">
      <UContainer>
        <div class="rounded-3xl border border-default bg-gradient-to-br from-emerald-500/10 via-transparent to-sky-500/10 p-8 sm:p-12">
          <div class="flex flex-col gap-8 lg:flex-row lg:items-center lg:justify-between">
            <div class="max-w-md">
              <h2 class="text-2xl font-bold tracking-tight text-highlighted sm:text-3xl">60+ commands, six groups, zero memorising</h2>
              <p class="mt-3 text-muted">Type a group (<code class="font-mono text-highlighted">artisan app</code>) for a picker, press Tab for descriptions, or just type what you want: <code class="font-mono text-highlighted">translation</code> finds <code class="font-mono text-highlighted">check:strings</code>.</p>
            </div>
            <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
              <NuxtLink v-for="g in groups" :key="g.name" :to="g.to" class="rounded-xl border border-default bg-default/80 px-4 py-3 backdrop-blur transition hover:border-primary/50">
                <div class="font-mono text-sm font-semibold text-primary">{{ g.name }}</div>
                <div class="text-xs text-muted">{{ g.label }}</div>
              </NuxtLink>
            </div>
          </div>
        </div>
      </UContainer>
    </section>

    <!-- A day with artisan -->
    <section class="border-t border-default bg-elevated/40 py-24">
      <UContainer>
        <div class="mx-auto mb-14 max-w-2xl text-center">
          <p class="mb-2 text-sm font-semibold uppercase tracking-wider text-primary">A day with artisan</p>
          <h2 class="text-3xl font-bold tracking-tight text-highlighted sm:text-4xl">From the first coffee to the release</h2>
        </div>
        <ol class="relative grid gap-6 md:grid-cols-5">
          <li v-for="(step, i) in day" :key="step.time" class="relative rounded-2xl border border-default bg-default p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="flex size-6 items-center justify-center rounded-full bg-primary text-xs font-bold text-inverted">{{ i + 1 }}</span>
              <span class="text-xs font-semibold uppercase tracking-wider text-muted">{{ step.time }}</span>
            </div>
            <h3 class="mb-2 font-semibold text-highlighted">{{ step.title }}</h3>
            <code class="mb-2 block truncate rounded bg-term-bg px-2 py-1 font-mono text-xs text-emerald-400">{{ step.cmd }}</code>
            <p class="text-sm text-muted">{{ step.text }}</p>
          </li>
        </ol>
        <div class="mt-10 text-center">
          <UButton to="/guides/a-day-with-artisan" variant="link" trailing-icon="i-lucide-arrow-right">Read the whole day</UButton>
        </div>
      </UContainer>
    </section>

    <!-- CTA -->
    <section class="py-24">
      <UContainer>
        <div class="relative mx-auto max-w-3xl overflow-hidden rounded-3xl bg-term-bg px-6 py-14 text-center text-term-text ring-1 ring-term-border sm:px-12">
          <div aria-hidden="true" class="pointer-events-none absolute inset-0 bg-[radial-gradient(40rem_20rem_at_50%_120%,color-mix(in_oklab,var(--color-emerald-500)_30%,transparent),transparent)]" />
          <h2 class="relative text-3xl font-bold tracking-tight sm:text-4xl">One install. Every Android project.</h2>
          <p class="relative mx-auto mt-4 max-w-xl text-term-dim">Free and MIT licensed. If artisan saves you a few clicks, a star helps other Android devs find it.</p>
          <div class="relative mt-8 flex flex-wrap justify-center gap-3">
            <UButton to="/getting-started/installation" size="xl" trailing-icon="i-lucide-download">Install</UButton>
            <UButton :to="repo" target="_blank" size="xl" color="neutral" variant="subtle" icon="i-lucide-star">Star on GitHub</UButton>
          </div>
        </div>
      </UContainer>
    </section>
  </div>
</template>
