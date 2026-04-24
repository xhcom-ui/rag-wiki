<template>
  <div class="lowcode-platform">
    <n-card title="可视化低代码平台">
      <template #header-extra>
        <n-space>
          <n-button @click="handlePreview">预览</n-button>
          <n-button type="primary" @click="handleSave">保存</n-button>
        </n-space>
      </template>

      <n-grid :cols="24" :x-gap="12">
        <!-- 左侧组件面板 -->
        <n-gi :span="5">
          <n-card title="组件库" size="small">
            <n-space vertical>
              <n-button block secondary @click="addComponent('TEXT')">文本输入</n-button>
              <n-button block secondary @click="addComponent('SELECT')">下拉选择</n-button>
              <n-button block secondary @click="addComponent('DATE')">日期选择</n-button>
              <n-button block secondary @click="addComponent('RADIO')">单选框</n-button>
              <n-button block secondary @click="addComponent('CHECKBOX')">多选框</n-button>
              <n-button block secondary @click="addComponent('TEXTAREA')">多行文本</n-button>
              <n-button block secondary @click="addComponent('NUMBER')">数字输入</n-button>
              <n-button block secondary @click="addComponent('FILE')">文件上传</n-button>
              <n-button block secondary @click="addComponent('TABLE')">数据表格</n-button>
              <n-button block secondary @click="addComponent('CHART')">图表</n-button>
              <n-button block secondary @click="addComponent('LLM')">AI对话</n-button>
              <n-button block secondary @click="addComponent('KNOWLEDGE')">知识检索</n-button>
            </n-space>
          </n-card>
        </n-gi>

        <!-- 中间画布 -->
        <n-gi :span="13">
          <n-card title="页面画布" size="small">
            <div class="canvas-area">
              <div
                v-for="(comp, index) in components"
                :key="comp.id"
                class="canvas-component"
                :class="{ selected: selectedId === comp.id }"
                @click="selectedId = comp.id"
              >
                <div class="comp-header">
                  <n-text strong>{{ comp.label || comp.type }}</n-text>
                  <n-button text size="tiny" @click.stop="removeComponent(index)">删除</n-button>
                </div>
                <div class="comp-body">
                  <!-- 根据组件类型渲染预览 -->
                  <n-input v-if="comp.type === 'TEXT'" :placeholder="comp.placeholder || '请输入'" disabled />
                  <n-select v-else-if="comp.type === 'SELECT'" :options="comp.options || []" disabled placeholder="请选择" />
                  <n-date-picker v-else-if="comp.type === 'DATE'" disabled style="width: 100%" />
                  <n-radio-group v-else-if="comp.type === 'RADIO'" disabled>
                    <n-radio v-for="opt in (comp.options || [])" :key="opt.value" :value="opt.value">{{ opt.label }}</n-radio>
                  </n-radio-group>
                  <n-input v-else-if="comp.type === 'TEXTAREA'" type="textarea" :rows="3" disabled />
                  <n-input-number v-else-if="comp.type === 'NUMBER'" disabled style="width: 100%" />
                  <n-text v-else-if="comp.type === 'LLM'" depth="3">AI智能对话组件</n-text>
                  <n-text v-else-if="comp.type === 'KNOWLEDGE'" depth="3">知识库检索组件</n-text>
                  <n-text v-else depth="3">{{ comp.type }} 组件</n-text>
                </div>
              </div>
              <n-text v-if="components.length === 0" depth="3" style="text-align: center; display: block; padding: 40px 0;">
                从左侧拖拽组件到此处
              </n-text>
            </div>
          </n-card>
        </n-gi>

        <!-- 右侧属性面板 -->
        <n-gi :span="6">
          <n-card title="属性配置" size="small">
            <template v-if="selectedComponent">
              <n-form label-placement="top" size="small">
                <n-form-item label="字段标识">
                  <n-input v-model:value="selectedComponent.fieldKey" />
                </n-form-item>
                <n-form-item label="标签名称">
                  <n-input v-model:value="selectedComponent.label" />
                </n-form-item>
                <n-form-item label="占位提示">
                  <n-input v-model:value="selectedComponent.placeholder" />
                </n-form-item>
                <n-form-item label="是否必填">
                  <n-switch v-model:value="selectedComponent.required" />
                </n-form-item>
                <n-form-item v-if="['SELECT', 'RADIO', 'CHECKBOX'].includes(selectedComponent.type)" label="选项列表">
                  <n-dynamic-tags v-model:value="optionTags" />
                </n-form-item>
                <n-form-item v-if="selectedComponent.type === 'LLM'" label="系统提示词">
                  <n-input v-model:value="selectedComponent.systemPrompt" type="textarea" :rows="3" />
                </n-form-item>
                <n-form-item v-if="selectedComponent.type === 'KNOWLEDGE'" label="知识库空间ID">
                  <n-input v-model:value="selectedComponent.spaceId" />
                </n-form-item>
              </n-form>
            </template>
            <n-text v-else depth="3">选择组件以编辑属性</n-text>
          </n-card>
        </n-gi>
      </n-grid>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMessage } from 'naive-ui'

type ComponentType =
  | 'TEXT'
  | 'SELECT'
  | 'DATE'
  | 'RADIO'
  | 'CHECKBOX'
  | 'TEXTAREA'
  | 'NUMBER'
  | 'FILE'
  | 'TABLE'
  | 'CHART'
  | 'LLM'
  | 'KNOWLEDGE'

interface ComponentOption {
  label: string
  value: string
}

interface LowcodeComponent {
  id: string
  type: ComponentType
  fieldKey: string
  label: string
  placeholder: string
  required: boolean
  options: ComponentOption[]
  systemPrompt?: string
  spaceId?: string
}

const message = useMessage()
const components = ref<LowcodeComponent[]>([])
const selectedId = ref('')
let counter = 0

const selectedComponent = computed(() => {
  return components.value.find(c => c.id === selectedId.value)
})

const optionTags = computed({
  get: () => (selectedComponent.value?.options || []).map((option: ComponentOption) => option.label),
  set: (vals: string[]) => {
    if (selectedComponent.value) {
      selectedComponent.value.options = vals.map(v => ({ label: v, value: v }))
    }
  }
})

function addComponent(type: ComponentType) {
  counter++
  const comp: LowcodeComponent = {
    id: `comp_${counter}`,
    type,
    fieldKey: `field_${counter}`,
    label: getLabel(type),
    placeholder: `请输入${getLabel(type)}`,
    required: false,
    options: ['RADIO', 'CHECKBOX', 'SELECT'].includes(type)
      ? [{ label: '选项1', value: 'opt1' }, { label: '选项2', value: 'opt2' }]
      : [],
  }
  components.value.push(comp)
  selectedId.value = comp.id
}

function removeComponent(index: number) {
  components.value.splice(index, 1)
  selectedId.value = ''
}

function getLabel(type: ComponentType): string {
  const labels: Record<ComponentType, string> = {
    TEXT: '文本输入', SELECT: '下拉选择', DATE: '日期选择',
    RADIO: '单选框', CHECKBOX: '多选框', TEXTAREA: '多行文本',
    NUMBER: '数字输入', FILE: '文件上传', TABLE: '数据表格',
    CHART: '图表', LLM: 'AI对话', KNOWLEDGE: '知识检索',
  }
  return labels[type] || type
}

function handlePreview() {
  message.info('预览功能：将当前组件配置渲染为实际页面')
}

function handleSave() {
  message.success(`保存成功，共 ${components.value.length} 个组件`)
}
</script>

<style scoped>
.lowcode-platform { padding: 16px; }
.canvas-area { min-height: 400px; border: 1px dashed #ddd; border-radius: 4px; padding: 12px; }
.canvas-component {
  border: 1px solid #e8e8e8; border-radius: 4px; padding: 8px 12px; margin-bottom: 8px;
  cursor: pointer; transition: border-color 0.2s;
}
.canvas-component:hover { border-color: #18a058; }
.canvas-component.selected { border-color: #18a058; box-shadow: 0 0 0 2px rgba(24,160,88,0.2); }
.comp-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.comp-body { padding: 4px 0; }
</style>
