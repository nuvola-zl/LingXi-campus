<script setup lang="ts">
// Shared chat view wrapper - domain-specific views can use this
</script>

<template>
  <div class="layout-container">
    <slot name="sidebar" />
    <main class="layout-main">
      <slot />
    </main>
  </div>
</template>

<style scoped lang="scss">
.layout-container {
  flex: 1; display: flex; height: calc(100vh - 56px); overflow: hidden;
}
.layout-main {
  flex: 1; overflow: hidden; display: flex; flex-direction: column;
}
</style>