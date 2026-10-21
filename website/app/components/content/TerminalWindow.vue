<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<template>
  <div class="not-prose my-6 overflow-hidden rounded-xl bg-term-bg text-term-text ring-1 ring-term-border">
    <div class="flex items-center gap-2 border-b border-term-border px-4 py-2.5">
      <span class="size-3 rounded-full bg-red-400" />
      <span class="size-3 rounded-full bg-yellow-400" />
      <span class="size-3 rounded-full bg-emerald-400" />
      <span v-if="title" class="ml-2 truncate font-mono text-xs text-term-dim">{{ title }}</span>
    </div>
    <div class="overflow-x-auto p-4 font-mono text-[13px] leading-6 [&_pre]:!m-0 [&_pre]:!border-0 [&_pre]:!bg-transparent [&_pre]:!p-0">
      <slot />
    </div>
  </div>
</template>
