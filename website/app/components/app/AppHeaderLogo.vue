<script setup lang="ts">
const { version } = useRuntimeConfig().public.docus as { version: string }
</script>

<template>
  <span class="flex items-center gap-2">
    <img src="/logo.svg" alt="" class="size-6 shrink-0">
    <span class="font-semibold tracking-tight">kotlin-<span class="text-primary">artisan</span></span>
    <UBadge :label="`v${version}`" variant="subtle" size="sm" class="hidden sm:inline-flex" />
  </span>
</template>
