<script setup lang="ts">
const options = [
  { label: 'Windows', icon: 'i-simple-icons-windows', prompt: 'PS>', command: 'irm https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/install.ps1 | iex' },
  { label: 'macOS / Linux', icon: 'i-simple-icons-linux', prompt: '$', command: 'curl -fsSL https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/install.sh | sh' },
]

const active = ref(0)
const copied = ref(false)

onMounted(() => {
  if (!/Win/i.test(navigator.userAgent)) active.value = 1
})

async function copy() {
  try {
    await navigator.clipboard.writeText(options[active.value]!.command)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  }
  catch {
    // Clipboard blocked (http, iframe): the command is still selectable
  }
}
</script>

<template>
  <div class="not-prose w-full max-w-2xl overflow-hidden rounded-xl bg-term-bg text-left ring-1 ring-term-border">
    <div class="flex border-b border-term-border">
      <button
        v-for="(option, i) in options"
        :key="option.label"
        type="button"
        class="flex items-center gap-1.5 px-4 py-2 text-xs font-medium transition-colors"
        :class="i === active ? 'border-b-2 border-emerald-400 text-term-text' : 'text-term-dim hover:text-term-text'"
        @click="active = i"
      >
        <UIcon :name="option.icon" class="size-3.5" />
        {{ option.label }}
      </button>
    </div>
    <div class="flex items-center gap-3 px-4 py-3 font-mono text-[13px]">
      <span class="shrink-0 select-none text-emerald-400">{{ options[active]!.prompt }}</span>
      <code class="min-w-0 flex-1 overflow-x-auto whitespace-nowrap text-term-text">{{ options[active]!.command }}</code>
      <UButton
        :icon="copied ? 'i-lucide-check' : 'i-lucide-copy'"
        :aria-label="copied ? 'Copied' : 'Copy install command'"
        color="neutral"
        variant="ghost"
        size="sm"
        class="shrink-0 text-term-dim hover:text-term-text"
        @click="copy"
      />
    </div>
  </div>
</template>
