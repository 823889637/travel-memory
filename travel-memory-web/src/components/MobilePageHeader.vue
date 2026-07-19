<script setup>
import { ArrowLeft } from '@lucide/vue'
import { useRouter } from 'vue-router'
import UserMenu from './UserMenu.vue'

const props = defineProps({
  title: { type: String, required: true },
  backTo: { type: [String, Object], default: '' },
  backLabel: { type: String, default: '返回' },
  showUserMenu: { type: Boolean, default: true },
})

const router = useRouter()

function goBack() {
  if (props.backTo) {
    router.push(props.backTo)
    return
  }
  router.back()
}
</script>

<template>
  <header class="mobile-page-header">
    <button type="button" class="mobile-page-header-back" :aria-label="backLabel" @click="goBack">
      <ArrowLeft :size="22" :stroke-width="1.8" aria-hidden="true" />
    </button>
    <strong>{{ title }}</strong>
    <div class="mobile-page-header-actions">
      <slot name="actions" />
      <UserMenu v-if="showUserMenu" class="mobile-page-user-menu" />
    </div>
  </header>
</template>

<style scoped>
.mobile-page-header { display: none; }

@media (max-width: 760px) {
  .mobile-page-header {
    position: sticky;
    z-index: 32;
    top: 0;
    display: grid;
    min-height: calc(56px + env(safe-area-inset-top));
    grid-template-columns: minmax(64px, 1fr) minmax(0, auto) minmax(64px, 1fr);
    align-items: end;
    margin-inline: -12px;
    padding: env(safe-area-inset-top) 12px 6px;
    border-bottom: 1px solid rgba(226, 214, 201, .78);
    background: rgba(255, 253, 249, .94);
    backdrop-filter: blur(14px);
  }

  .mobile-page-header > strong {
    align-self: center;
    overflow: hidden;
    color: var(--tm-text);
    font-family: var(--tm-font-sans);
    font-size: 17px;
    font-weight: 600;
    line-height: 22px;
    letter-spacing: 0;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-page-header-back,
  .mobile-page-header-actions :deep(button),
  .mobile-page-header-actions :deep(a) {
    display: grid;
    width: 44px;
    height: 44px;
    place-items: center;
    padding: 0;
    border: 0;
    border-radius: 50%;
    background: transparent;
    color: var(--tm-text);
  }

  .mobile-page-header-actions {
    display: flex;
    min-width: 44px;
    align-items: center;
    justify-content: flex-end;
    gap: 2px;
  }

  .mobile-page-header-actions :deep(.mobile-page-user-menu) {
    min-width: 40px;
    margin-left: 0;
  }

  .mobile-page-header-actions :deep(.mobile-page-user-menu .user-trigger) {
    width: 40px;
    height: 40px;
    padding: 3px;
  }

  .mobile-page-header-actions :deep(.mobile-page-user-menu .user-avatar) {
    width: 32px;
    height: 32px;
  }

  .mobile-page-header-actions :deep(.mobile-page-user-menu .user-trigger-name),
  .mobile-page-header-actions :deep(.mobile-page-user-menu .user-trigger-caret) {
    display: none;
  }

  .mobile-page-header-actions :deep(.mobile-page-user-menu .user-popover a),
  .mobile-page-header-actions :deep(.mobile-page-user-menu .user-popover button) {
    display: flex;
    width: 100%;
    height: auto;
    min-height: 44px;
    justify-content: flex-start;
    padding: 9px;
    border-radius: 8px;
  }

  .mobile-page-header-actions :deep(.trip-draft-header-action) {
    display: inline-flex;
    width: auto;
    min-width: 76px;
    gap: 5px;
    justify-content: flex-end;
    border-radius: 0;
    color: var(--tm-accent);
    font-size: 13px;
  }
}
</style>
