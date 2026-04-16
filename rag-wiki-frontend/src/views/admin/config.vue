<template>
  <div class="config-page">
    <n-card title="系统配置管理">
      <n-tabs v-model:value="activeGroup" type="card" @update:value="loadConfigs">
        <n-tab v-for="group in groups" :key="group.groupKey" :name="group.groupKey">
          {{ group.groupLabel }}
        </n-tab>
      </n-tabs>

      <n-space vertical style="margin-top: 16px">
        <div v-for="config in currentConfigs" :key="config.configKey" class="config-item">
          <n-card size="small" :title="config.description || config.configKey">
            <template #header-extra>
              <n-tag v-if="config.isSystem" size="small" type="info">系统内置</n-tag>
              <n-tag v-if="config.isReadonly" size="small" type="warning">只读</n-tag>
            </template>
            <n-space align="center">
              <n-input
                v-if="config.configType === 'STRING'"
                v-model:value="config.editValue"
                :disabled="config.isReadonly === 1"
                style="width: 400px"
              />
              <n-input-number
                v-else-if="config.configType === 'NUMBER'"
                v-model:value="config.editNumber"
                :disabled="config.isReadonly === 1"
                style="width: 200px"
              />
              <n-switch
                v-else-if="config.configType === 'BOOLEAN'"
                v-model:value="config.editBool"
                :disabled="config.isReadonly === 1"
              />
              <n-input
                v-else
                v-model:value="config.editValue"
                type="textarea"
                :disabled="config.isReadonly === 1"
                style="width: 400px"
                :rows="3"
              />
              <n-button
                v-if="config.isReadonly !== 1"
                type="primary"
                size="small"
                @click="saveConfig(config)"
              >保存</n-button>
            </n-space>
            <n-text depth="3" style="font-size: 12px">Key: {{ config.configKey }} | Type: {{ config.configType }}</n-text>
          </n-card>
        </div>
      </n-space>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { configApi } from '@/api'

const message = useMessage()
const activeGroup = ref('BASIC')
const groups = ref<any[]>([])
const currentConfigs = ref<any[]>([])

onMounted(() => {
  loadGroups()
})

async function loadGroups() {
  try {
    const res: any = await configApi.getAllGroups()
    groups.value = res.data || []
    if (groups.value.length > 0) {
      activeGroup.value = groups.value[0].groupKey
      currentConfigs.value = groups.value[0].configs?.map((c: any) => ({
        ...c,
        editValue: c.configValue || '',
        editNumber: Number(c.configValue) || 0,
        editBool: c.configValue === 'true',
      })) || []
    }
  } catch (e: any) {
    message.error(e.message || '加载配置失败')
  }
}

async function loadConfigs(groupKey: string) {
  const group = groups.value.find(g => g.groupKey === groupKey)
  if (group) {
    currentConfigs.value = group.configs?.map((c: any) => ({
      ...c,
      editValue: c.configValue || '',
      editNumber: Number(c.configValue) || 0,
      editBool: c.configValue === 'true',
    })) || []
  }
}

async function saveConfig(config: any) {
  try {
    let value = config.editValue
    if (config.configType === 'NUMBER') value = String(config.editNumber)
    if (config.configType === 'BOOLEAN') value = String(config.editBool)
    await configApi.updateValue(config.configKey, value)
    config.configValue = value
    message.success('配置已保存')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  }
}
</script>

<style scoped>
.config-page { padding: 16px; }
.config-item { margin-bottom: 8px; }
</style>
