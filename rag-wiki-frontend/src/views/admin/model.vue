<template>
  <n-card title="AI模型配置管理" class="llm-config-page">
    <!-- 搜索和操作栏 -->
    <n-space justify="space-between" align="center" class="toolbar">
      <n-space>
        <n-select
          v-model:value="searchForm.provider"
          :options="providerOptions"
          placeholder="选择提供商"
          clearable
          style="width: 160px"
        />
        <n-select
          v-model:value="searchForm.isEnabled"
          :options="statusOptions"
          placeholder="启用状态"
          clearable
          style="width: 120px"
        />
        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button @click="handleReset">重置</n-button>
      </n-space>
      <n-button type="primary" @click="handleAdd">
        <template #icon>
          <n-icon><Add /></n-icon>
        </template>
        新增配置
      </n-button>
    </n-space>

    <!-- 配置列表 -->
    <n-data-table
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
      row-key="configId"
    />

    <!-- 新增/编辑弹窗 -->
    <n-modal
      v-model:show="modalVisible"
      :title="modalTitle"
      preset="card"
      style="width: 600px"
      :mask-closable="false"
    >
      <n-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-placement="left"
        label-width="100"
      >
        <n-form-item label="配置名称" path="configName">
          <n-input v-model:value="formData.configName" placeholder="如：DeepSeek生产环境" />
        </n-form-item>
        <n-form-item label="提供商" path="provider">
          <n-select
            v-model:value="formData.provider"
            :options="providerOptions"
            placeholder="选择LLM提供商"
          />
        </n-form-item>
        <n-form-item label="模型名称" path="model">
          <n-input v-model:value="formData.model" placeholder="如：deepseek-chat" />
        </n-form-item>
        <n-form-item label="API Key" path="apiKey">
          <n-input
            v-model:value="formData.apiKey"
            type="password"
            show-password-on="click"
            placeholder="输入API密钥"
          />
          <n-text depth="3" v-if="isEdit && formData.apiKey.startsWith('****')">
            留空则保持原密钥不变
          </n-text>
        </n-form-item>
        <n-form-item label="API Base" path="apiBase">
          <n-input v-model:value="formData.apiBase" placeholder="如：https://api.deepseek.com/v1" />
        </n-form-item>
        <n-form-item label="温度参数" path="temperature">
          <n-slider v-model:value="formData.temperature" :min="0" :max="2" :step="0.1" />
          <n-text>{{ formData.temperature }}</n-text>
        </n-form-item>
        <n-form-item label="最大Token" path="maxTokens">
          <n-input-number v-model:value="formData.maxTokens" :min="1" :max="8192" />
        </n-form-item>
        <n-form-item label="优先级" path="priority">
          <n-input-number v-model:value="formData.priority" :min="0" :max="100" />
          <n-text depth="3">数值越小优先级越高，用于重试顺序</n-text>
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input
            v-model:value="formData.description"
            type="textarea"
            :rows="2"
            placeholder="配置描述"
          />
        </n-form-item>
        <n-form-item label="启用状态">
          <n-switch v-model:value="formData.isEnabled" />
        </n-form-item>
        <n-form-item label="设为默认">
          <n-switch v-model:value="formData.isDefault" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="modalVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useMessage, NTag, NSpace, NButton, NPopconfirm, type DataTableColumns, type FormInst } from 'naive-ui'
import { Add, CheckmarkCircle, CloseCircle } from '@vicons/ionicons5'
import { llmConfigApi } from '@/api'

interface ProviderOption {
  label: string
  value: string
}

interface LLMConfigRow {
  configId: string
  configName: string
  provider: string
  model: string
  apiKey?: string
  apiBase?: string
  temperature?: number
  maxTokens?: number
  priority?: number
  description?: string
  isEnabled: number
  isDefault: number
}

interface LLMConfigPageResult {
  records?: LLMConfigRow[]
  total?: number
}

interface LLMConfigForm {
  configId: string
  configName: string
  provider: string
  model: string
  apiKey: string
  apiBase: string
  temperature: number
  maxTokens: number
  priority: number
  description: string
  isEnabled: boolean
  isDefault: boolean
}

const message = useMessage()

// 搜索表单
const searchForm = reactive({
  provider: null as string | null,
  isEnabled: null as number | null,
})

// 提供商选项
const providerOptions = ref<ProviderOption[]>([])

// 状态选项
const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

// 表格数据
const tableData = ref<LLMConfigRow[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
})

// 弹窗相关
const modalVisible = ref(false)
const modalTitle = ref('新增配置')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInst | null>(null)

const formData = reactive<LLMConfigForm>({
  configId: '',
  configName: '',
  provider: '',
  model: '',
  apiKey: '',
  apiBase: '',
  temperature: 0.7,
  maxTokens: 4096,
  priority: 0,
  description: '',
  isEnabled: true,
  isDefault: false,
})

const formRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
  model: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' }],
}

// 表格列定义
const columns: DataTableColumns<LLMConfigRow> = [
  { title: '配置名称', key: 'configName', width: 180 },
  {
    title: '提供商',
    key: 'provider',
    width: 120,
    render(row) {
      const providerMap: Record<string, string> = {
        openai: 'OpenAI',
        deepseek: 'DeepSeek',
        qwen: '千问',
        gemini: 'Gemini',
        glm: '智普GLM',
        kimi: 'Kimi',
        local: '本地模型',
      }
      return h(NTag, { type: 'info', size: 'small' }, { default: () => providerMap[row.provider] || row.provider })
    },
  },
  { title: '模型', key: 'model', width: 150 },
  {
    title: '默认',
    key: 'isDefault',
    width: 80,
    align: 'center',
    render(row) {
      return row.isDefault === 1
        ? h(CheckmarkCircle, { style: 'color: #18a058; font-size: 18px' })
        : h(CloseCircle, { style: 'color: #d9d9d9; font-size: 18px' })
    },
  },
  {
    title: '状态',
    key: 'isEnabled',
    width: 80,
    align: 'center',
    render(row) {
      return h(
        NTag,
        { type: row.isEnabled === 1 ? 'success' : 'default', size: 'small' },
        { default: () => (row.isEnabled === 1 ? '启用' : '禁用') }
      )
    },
  },
  { title: '优先级', key: 'priority', width: 80, align: 'center' },
  { title: '温度', key: 'temperature', width: 80, align: 'center' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render(row) {
      return h(NSpace, null, {
        default: () => [
          row.isDefault !== 1 &&
            h(
              NButton,
              { size: 'small', type: 'primary', onClick: () => handleSetDefault(row) },
              { default: () => '设为默认' }
            ),
          h(NButton, { size: 'small', onClick: () => handleEdit(row) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => handleDelete(row) },
            {
              trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
              default: () => '确定删除该配置吗？',
            }
          ),
        ],
      })
    },
  },
]

// 加载提供商选项
async function loadProviders() {
  try {
    const res = await llmConfigApi.getProviders()
    if (res.code === 200) {
      providerOptions.value = Array.isArray(res.data) ? res.data : []
    }
  } catch (error) {
    console.error('加载提供商列表失败', error)
  }
}

// 加载表格数据
async function loadData() {
  loading.value = true
  try {
    const res = await llmConfigApi.page({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      provider: searchForm.provider || undefined,
      isEnabled: searchForm.isEnabled ?? undefined,
    })
    if (res.code === 200) {
      const pageData = (res.data || {}) as LLMConfigPageResult
      tableData.value = pageData.records || []
      pagination.itemCount = pageData.total || 0
    }
  } catch {
    message.error('加载配置列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
function handleSearch() {
  pagination.page = 1
  loadData()
}

// 重置
function handleReset() {
  searchForm.provider = null
  searchForm.isEnabled = null
  handleSearch()
}

// 分页变化
function handlePageChange(page: number) {
  pagination.page = page
  loadData()
}

// 页大小变化
function handlePageSizeChange(size: number) {
  pagination.pageSize = size
  pagination.page = 1
  loadData()
}

// 新增
function handleAdd() {
  isEdit.value = false
  modalTitle.value = '新增配置'
  resetForm()
  modalVisible.value = true
}

// 编辑
async function handleEdit(row: LLMConfigRow) {
  isEdit.value = true
  modalTitle.value = '编辑配置'
  try {
    const res = await llmConfigApi.getById(row.configId)
    if (res.code === 200 && res.data) {
      const data = res.data as Partial<LLMConfigRow>
      Object.assign(formData, {
        configId: data.configId || '',
        configName: data.configName || '',
        provider: data.provider || '',
        model: data.model || '',
        apiKey: data.apiKey || '',
        apiBase: data.apiBase || '',
        temperature: data.temperature ?? 0.7,
        maxTokens: data.maxTokens ?? 4096,
        priority: data.priority ?? 0,
        description: data.description || '',
        isEnabled: data.isEnabled === 1,
        isDefault: data.isDefault === 1,
      })
      modalVisible.value = true
    }
  } catch {
    message.error('获取配置详情失败')
  }
}

// 设为默认
async function handleSetDefault(row: LLMConfigRow) {
  try {
    const res = await llmConfigApi.setDefault(row.configId)
    if (res.code === 200) {
      message.success('设置成功')
      loadData()
    }
  } catch {
    message.error('设置失败')
  }
}

// 删除
async function handleDelete(row: LLMConfigRow) {
  try {
    const res = await llmConfigApi.delete(row.configId)
    if (res.code === 200) {
      message.success('删除成功')
      loadData()
    }
  } catch {
    message.error('删除失败')
  }
}

// 提交表单
async function handleSubmit() {
  await formRef.value?.validate()

  submitLoading.value = true
  try {
    const data = {
      ...formData,
      isEnabled: formData.isEnabled ? 1 : 0,
      isDefault: formData.isDefault ? 1 : 0,
    }

    const res = isEdit.value
      ? await llmConfigApi.update(formData.configId, data)
      : await llmConfigApi.create(data)

    if (res.code === 200) {
      message.success(isEdit.value ? '更新成功' : '创建成功')
      modalVisible.value = false
      loadData()
    }
  } catch {
    message.error(isEdit.value ? '更新失败' : '创建失败')
  } finally {
    submitLoading.value = false
  }
}

// 重置表单
function resetForm() {
  formData.configId = ''
  formData.configName = ''
  formData.provider = ''
  formData.model = ''
  formData.apiKey = ''
  formData.apiBase = ''
  formData.temperature = 0.7
  formData.maxTokens = 4096
  formData.priority = 0
  formData.description = ''
  formData.isEnabled = true
  formData.isDefault = false
}

onMounted(() => {
  loadProviders()
  loadData()
})
</script>

<style scoped>
.llm-config-page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
</style>
