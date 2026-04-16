<template>
  <n-config-provider :theme="theme">
    <n-message-provider>
      <n-dialog-provider>
        <n-loading-bar-provider>
          <GlobalApiSetup />
          <router-view />
        </n-loading-bar-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { ref, defineComponent, h, onMounted, onUnmounted } from 'vue'
import { useMessage, useLoadingBar } from 'naive-ui'
import { setGlobalMessage, onGlobalLoading } from './api'

const theme = ref(null) // null = light, darkTheme = dark

// 内部组件：注册全局API消息通知
const GlobalApiSetup = defineComponent({
  setup() {
    const message = useMessage()
    const loadingBar = useLoadingBar()

    // 注册全局消息通知
    onMounted(() => {
      setGlobalMessage(
        (content: string) => message.error(content),
        (content: string) => message.warning(content)
      )
    })

    // 注册全局Loading
    const unsubscribe = onGlobalLoading((loading: boolean) => {
      if (loading) {
        loadingBar.start()
      } else {
        loadingBar.finish()
      }
    })

    onUnmounted(() => {
      unsubscribe()
    })

    return () => null
  },
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
</style>
