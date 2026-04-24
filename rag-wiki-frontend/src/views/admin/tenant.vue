<template>
  <div class="page-container fade-in">
    <div class="page-header">
      <h2>租户管理</h2>
      <p>多租户隔离配置与管理</p>
    </div>

    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">租户总数</div>
        <div class="stat-value">{{ tenantList.length }}</div>
        <div class="stat-footer"><span>租户实例</span></div>
      </div>
      <div class="stat-card success">
        <div class="stat-label">活跃租户</div>
        <div class="stat-value">{{ tenantList.filter(t => t.status === 1).length }}</div>
        <div class="stat-footer"><span>正常运行</span></div>
      </div>
    </div>

    <n-card class="content-card">
      <n-space justify="space-between" align="center" style="margin-bottom: 16px">
        <n-space>
          <n-input v-model:value="keyword" placeholder="搜索租户名称" clearable style="width: 200px" />
          <n-button type="primary" @click="loadData">查询</n-button>
        </n-space>
        <n-button type="primary" @click="openCreateModal">创建租户</n-button>
      </n-space>

      <n-data-table :columns="columns" :data="tenantList" :loading="loading" :row-key="(row: any) => row.tenantId" />
    </n-card>

    <!-- 创建/编辑弹窗 -->
    <n-modal v-model:show="showModal" preset="dialog" :title="isEdit ? '编辑租户' : '创建租户'" positive-text="确认" negative-text="取消"
      @positive-click="handleSubmit">
      <n-form>
        <n-form-item label="租户名称">
          <n-input v-model:value="formData.tenantName" placeholder="请输入租户名称" />
        </n-form-item>
        <n-form-item label="隔离级别">
          <n-select v-model:value="formData.isolationLevel" :options="isolationOptions" />
        </n-form-item>
        <n-form-item label="最大用户数">
          <n-input-number v-model:value="formData.maxUsers" :min="1" :max="10000" style="width: 100%" />
        </n-form-item>
        <n-form-item label="最大空间数">
          <n-input-number v-model:value="formData.maxSpaces" :min="1" :max="1000" style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态">
          <n-switch v-model:value="formData.statusBool" />
        </n-form-item>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useMessage, NTag, NButton, NSpace, type DataTableColumns } from 'naive-ui'
import { tenantApi } from '@/api'

const message = useMessage()
const loading = ref(false)

type IsolationLevel = 'METADATA_FILTER' | 'PARTITION' | 'COLLECTION'

interface TenantRow {
  tenantId: string
  tenantName: string
  isolationLevel: IsolationLevel
  maxUsers: number
  maxSpaces: number
  status: number
}

interface TenantForm {
  tenantId: string
  tenantName: string
  isolationLevel: IsolationLevel
  maxUsers: number
  maxSpaces: number
  statusBool: boolean
}

const tenantList = ref<TenantRow[]>([])
const showModal = ref(false)
const isEdit = ref(false)
const keyword = ref('')

const formData = reactive<TenantForm>({
  tenantId: '',
  tenantName: '',
  isolationLevel: 'METADATA_FILTER',
  maxUsers: 100,
  maxSpaces: 20,
  statusBool: true,
})

const isolationOptions = [
  { label: '元数据过滤（低隔离）', value: 'METADATA_FILTER' },
  { label: '分区隔离（中隔离）', value: 'PARTITION' },
  { label: '集合隔离（高隔离）', value: 'COLLECTION' },
]

const columns: DataTableColumns<TenantRow> = [
  { title: '租户ID', key: 'tenantId', width: 120 },
  { title: '租户名称', key: 'tenantName' },
  { title: '隔离级别', key: 'isolationLevel', width: 160, render: (row) => h(NTag, { size: 'small' }, { default: () => row.isolationLevel }) },
  { title: '最大用户数', key: 'maxUsers', width: 100 },
  { title: '最大空间数', key: 'maxSpaces', width: 100 },
  { title: '状态', key: 'status', width: 80, render: (row) => h(NTag, { type: row.status === 1 ? 'success' : 'error', size: 'small' }, { default: () => row.status === 1 ? '启用' : '禁用' }) },
  { title: '操作', key: 'actions', width: 140, render: (row) => h(NSpace, null, { default: () => [
    h(NButton, { size: 'small', onClick: () => openEditModal(row) }, { default: () => '编辑' }),
    h(NButton, { size: 'small', type: row.status === 1 ? 'error' : 'success', onClick: () => toggleStatus(row) }, { default: () => row.status === 1 ? '禁用' : '启用' }),
  ] }) },
]

const loadData = async () => {
  loading.value = true
  try {
    const res = await tenantApi.page({ pageNum: 1, pageSize: 100, tenantName: keyword.value || undefined })
    tenantList.value = normalizeTenants(res.data?.records)
  } catch {
    message.error('加载失败')
  }
  finally { loading.value = false }
}

const openCreateModal = () => {
  isEdit.value = false
  Object.assign(formData, { tenantId: '', tenantName: '', isolationLevel: 'METADATA_FILTER', maxUsers: 100, maxSpaces: 20, statusBool: true })
  showModal.value = true
}

const openEditModal = (row: TenantRow) => {
  isEdit.value = true
  Object.assign(formData, { ...row, statusBool: row.status === 1 })
  showModal.value = true
}

const handleSubmit = async () => {
  try {
    const payload = {
      tenantId: formData.tenantId || undefined,
      tenantName: formData.tenantName,
      isolationLevel: formData.isolationLevel,
      maxUsers: formData.maxUsers,
      maxSpaces: formData.maxSpaces,
      status: formData.statusBool ? 1 : 0,
    }
    if (isEdit.value) await tenantApi.update(payload)
    else await tenantApi.create(payload)
    message.success(isEdit.value ? '更新成功' : '创建成功')
    showModal.value = false
    loadData()
  } catch { message.error('操作失败') }
}

const toggleStatus = async (row: TenantRow) => {
  try {
    await tenantApi.toggleStatus(row.tenantId, row.status === 1 ? 0 : 1)
    message.success('状态更新成功')
    loadData()
  } catch { message.error('操作失败') }
}

onMounted(() => { loadData() })

function normalizeTenants(records: unknown): TenantRow[] {
  if (!Array.isArray(records)) return []
  return records.map((item) => {
    const row = item as Partial<TenantRow>
    return {
      tenantId: row.tenantId || '',
      tenantName: row.tenantName || '',
      isolationLevel: (row.isolationLevel as IsolationLevel) || 'METADATA_FILTER',
      maxUsers: Number(row.maxUsers || 0),
      maxSpaces: Number(row.maxSpaces || 0),
      status: Number(row.status ?? 1),
    }
  })
}
</script>

<style scoped>
</style>
