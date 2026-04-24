<template>
  <n-card title="审计日志" class="audit-page">
    <!-- 搜索栏 -->
    <n-space justify="space-between" align="center" class="toolbar">
      <n-space>
        <n-input
          v-model:value="searchForm.keyword"
          placeholder="搜索用户/操作"
          clearable
          style="width: 200px"
        />
        <n-select
          v-model:value="searchForm.actionType"
          :options="actionTypeOptions"
          placeholder="操作类型"
          clearable
          style="width: 150px"
        />
        <n-date-picker
          v-model:value="searchForm.dateRange"
          type="daterange"
          clearable
          placeholder="选择日期范围"
        />
        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button @click="handleReset">重置</n-button>
      </n-space>
      <n-button @click="handleExport">
        <template #icon>
          <n-icon><DownloadOutline /></n-icon>
        </template>
        导出日志
      </n-button>
    </n-space>

    <!-- 日志列表 -->
    <n-data-table
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
      row-key="id"
    />

    <!-- 详情弹窗 -->
    <n-modal
      v-model:show="detailVisible"
      title="操作详情"
      preset="card"
      style="width: 700px"
    >
      <n-descriptions :column="1" bordered>
        <n-descriptions-item label="操作ID">{{ currentLog?.id }}</n-descriptions-item>
        <n-descriptions-item label="操作用户">{{ currentLog?.username }}</n-descriptions-item>
        <n-descriptions-item label="操作类型">
          <n-tag :type="getActionTypeColor(currentLog?.actionType)">
            {{ getActionTypeLabel(currentLog?.actionType) }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="操作对象">{{ currentLog?.target }}</n-descriptions-item>
        <n-descriptions-item label="操作时间">{{ currentLog?.createdAt }}</n-descriptions-item>
        <n-descriptions-item label="IP地址">{{ currentLog?.ip }}</n-descriptions-item>
        <n-descriptions-item label="操作结果">
          <n-tag :type="currentLog?.success ? 'success' : 'error'">
            {{ currentLog?.success ? '成功' : '失败' }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="请求参数">
          <n-code :code="formatJson(currentLog?.requestParams)" language="json" />
        </n-descriptions-item>
        <n-descriptions-item label="响应结果">
          <n-code :code="formatJson(currentLog?.responseResult)" language="json" />
        </n-descriptions-item>
        <n-descriptions-item label="错误信息" v-if="!currentLog?.success">
          <n-text type="error">{{ currentLog?.errorMsg }}</n-text>
        </n-descriptions-item>
      </n-descriptions>
    </n-modal>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { NButton, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { DownloadOutline, EyeOutline } from '@vicons/ionicons5'
import { auditApi } from '@/api'

type TagType = 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error'

interface AuditLogRow {
  id: number | string
  username?: string
  actionType: string
  target?: string
  success: boolean
  ip?: string
  createdAt?: string
  requestParams?: unknown
  responseResult?: unknown
  errorMsg?: string
}

interface AuditPageResult {
  records?: AuditLogRow[]
  total?: number
}

const message = useMessage()

// 搜索表单
const searchForm = reactive({
  keyword: '',
  actionType: null as string | null,
  dateRange: null as [number, number] | null,
})

// 操作类型选项
const actionTypeOptions = [
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '创建', value: 'CREATE' },
  { label: '更新', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '查询', value: 'QUERY' },
  { label: '上传', value: 'UPLOAD' },
  { label: '下载', value: 'DOWNLOAD' },
  { label: '审批', value: 'APPROVAL' },
  { label: '导出', value: 'EXPORT' },
]

// 表格数据
const tableData = ref<AuditLogRow[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [20, 50, 100],
})

// 详情弹窗
const detailVisible = ref(false)
const currentLog = ref<AuditLogRow | null>(null)

// 获取操作类型标签
function getActionTypeLabel(type?: string | null): string {
  const map: Record<string, string> = {
    LOGIN: '登录',
    LOGOUT: '登出',
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    QUERY: '查询',
    UPLOAD: '上传',
    DOWNLOAD: '下载',
    APPROVAL: '审批',
    EXPORT: '导出',
  }
  return map[type || ''] || type || '-'
}

// 获取操作类型颜色
function getActionTypeColor(type?: string | null): TagType {
  const map: Record<string, TagType> = {
    LOGIN: 'success',
    LOGOUT: 'default',
    CREATE: 'info',
    UPDATE: 'warning',
    DELETE: 'error',
    QUERY: 'default',
    UPLOAD: 'info',
    DOWNLOAD: 'info',
    APPROVAL: 'warning',
    EXPORT: 'default',
  }
  return map[type || ''] || 'default'
}

// 格式化JSON
function formatJson(data: unknown): string {
  if (!data) return ''
  try {
    const obj = typeof data === 'string' ? JSON.parse(data) : data
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(data)
  }
}

// 表格列定义
const columns: DataTableColumns<AuditLogRow> = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '操作用户', key: 'username', width: 120 },
  {
    title: '操作类型',
    key: 'actionType',
    width: 100,
    render(row) {
      return h(
        NTag,
        { type: getActionTypeColor(row.actionType), size: 'small' },
        { default: () => getActionTypeLabel(row.actionType) }
      )
    },
  },
  { title: '操作对象', key: 'target', ellipsis: { tooltip: true } },
  {
    title: '结果',
    key: 'success',
    width: 80,
    align: 'center',
    render(row) {
      return h(
        NTag,
        { type: row.success ? 'success' : 'error', size: 'small' },
        { default: () => (row.success ? '成功' : '失败') }
      )
    },
  },
  { title: 'IP地址', key: 'ip', width: 140 },
  { title: '操作时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    fixed: 'right',
    render(row) {
      return h(
        NButton,
        {
          size: 'small',
          onClick: () => handleViewDetail(row),
        },
        { default: () => '详情', icon: () => h(EyeOutline) }
      )
    },
  },
]

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const params: Record<string, string | number | undefined> = {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      actionType: searchForm.actionType || undefined,
    }

    if (searchForm.dateRange) {
      params.startTime = new Date(searchForm.dateRange[0]).toISOString()
      params.endTime = new Date(searchForm.dateRange[1]).toISOString()
    }

    const res = await auditApi.listLogs(params)
    if (res.code === 200) {
      const pageData = (res.data || {}) as AuditPageResult
      tableData.value = pageData.records || []
      pagination.itemCount = pageData.total || 0
    }
  } catch {
    message.error('加载审计日志失败')
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
  searchForm.keyword = ''
  searchForm.actionType = null
  searchForm.dateRange = null
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

// 查看详情
function handleViewDetail(row: AuditLogRow) {
  currentLog.value = row
  detailVisible.value = true
}

// 导出日志
function handleExport() {
  message.info('导出功能开发中...')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.audit-page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
</style>
