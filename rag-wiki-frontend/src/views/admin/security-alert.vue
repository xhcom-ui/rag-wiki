<template>
  <div class="page-container fade-in">
    <div class="page-header">
      <h2>安全告警中心</h2>
      <p>实时安全事件检测、告警与处置</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card error">
        <div class="stat-label">待处理</div>
        <div class="stat-value">{{ stats.unhandledCount || 0 }}</div>
        <div class="stat-footer">
          <span>需要关注</span>
        </div>
      </div>
      <div class="stat-card warning">
        <div class="stat-label">今日新增</div>
        <div class="stat-value">{{ stats.todayNew || 0 }}</div>
        <div class="stat-footer">
          <span>告警事件</span>
        </div>
      </div>
      <div class="stat-card info">
        <div class="stat-label">CRITICAL</div>
        <div class="stat-value">{{ severityCount('CRITICAL') }}</div>
        <div class="stat-footer">
          <span>严重告警</span>
        </div>
      </div>
      <div class="stat-card success">
        <div class="stat-label">已处理</div>
        <div class="stat-value">{{ statusCount('RESOLVED') }}</div>
        <div class="stat-footer">
          <span>已关闭</span>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <n-card class="content-card">
      <n-space justify="space-between" align="center">
        <n-space>
          <n-select v-model:value="searchForm.severity" :options="severityOptions" placeholder="严重程度" clearable style="width: 140px" />
          <n-select v-model:value="searchForm.status" :options="statusOptions" placeholder="处理状态" clearable style="width: 140px" />
          <n-select v-model:value="searchForm.alertType" :options="typeOptions" placeholder="告警类型" clearable style="width: 160px" />
          <n-button type="primary" @click="handleSearch">查询</n-button>
          <n-button @click="handleReset">重置</n-button>
        </n-space>
      </n-space>
    </n-card>

    <!-- 告警列表 -->
    <n-card class="content-card mt-md">
      <n-data-table :columns="columns" :data="alertList" :loading="loading" :pagination="pagination"
        :row-key="row => row.alertId" @update:page="handlePageChange" />
    </n-card>

    <!-- 处理弹窗 -->
    <n-modal v-model:show="showHandleModal" preset="dialog" title="处理告警" positive-text="确认" negative-text="取消"
      @positive-click="handleAlert">
      <n-form>
        <n-form-item label="处理结果">
          <n-select v-model:value="handleForm.newStatus" :options="handleStatusOptions" />
        </n-form-item>
        <n-form-item label="处理说明">
          <n-input v-model:value="handleForm.handleResult" type="textarea" :rows="3" placeholder="请输入处理说明" />
        </n-form-item>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useMessage, NTag, NButton, NSpace, type DataTableColumns } from 'naive-ui'

type AlertSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
type AlertStatus = 'NEW' | 'CONFIRMED' | 'PROCESSING' | 'RESOLVED' | 'IGNORED'
type AlertType =
  | 'HIGH_FREQUENCY'
  | 'UNAUTHORIZED_ACCESS'
  | 'OFF_HOURS_ACCESS'
  | 'SQL_INJECTION'
  | 'SENSITIVE_DOC_ACCESS'
  | 'CROSS_DEPT_ACCESS'

interface SecurityAlert {
  alertId: string
  alertType: AlertType | string
  severity: AlertSeverity
  title: string
  sourceIp?: string
  userId?: string
  status: AlertStatus
  createdAt: string
}

interface AlertCountBySeverity {
  severity: AlertSeverity
  count: number
}

interface AlertCountByStatus {
  status: AlertStatus
  count: number
}

interface SecurityAlertStats {
  unhandledCount?: number
  todayNew?: number
  bySeverity: AlertCountBySeverity[]
  byStatus: AlertCountByStatus[]
}

const message = useMessage()
const loading = ref(false)
const alertList = ref<SecurityAlert[]>([])
const showHandleModal = ref(false)

const stats = ref<SecurityAlertStats>({
  bySeverity: [],
  byStatus: [],
})
const searchForm = reactive<{
  severity: AlertSeverity | null
  status: AlertStatus | null
  alertType: AlertType | null
}>({ severity: null, status: null, alertType: null })
const handleForm = reactive({ alertId: '', newStatus: 'RESOLVED', handleResult: '' })

const pagination = reactive({ page: 1, pageSize: 20, itemCount: 0 })

const severityOptions = [
  { label: 'CRITICAL', value: 'CRITICAL' },
  { label: 'HIGH', value: 'HIGH' },
  { label: 'MEDIUM', value: 'MEDIUM' },
  { label: 'LOW', value: 'LOW' },
]
const statusOptions = [
  { label: '新建', value: 'NEW' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已忽略', value: 'IGNORED' },
]
const typeOptions = [
  { label: '高频查询', value: 'HIGH_FREQUENCY' },
  { label: '越权访问', value: 'UNAUTHORIZED_ACCESS' },
  { label: '非工作时间访问', value: 'OFF_HOURS_ACCESS' },
  { label: 'SQL注入', value: 'SQL_INJECTION' },
  { label: '敏感文档访问', value: 'SENSITIVE_DOC_ACCESS' },
  { label: '跨部门访问', value: 'CROSS_DEPT_ACCESS' },
]
const handleStatusOptions = [
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已忽略', value: 'IGNORED' },
]

const severityColorMap: Record<AlertSeverity, 'error' | 'warning' | 'info' | 'default'> = {
  CRITICAL: 'error',
  HIGH: 'warning',
  MEDIUM: 'info',
  LOW: 'default',
}

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}

const columns: DataTableColumns<SecurityAlert> = [
  { title: '告警类型', key: 'alertType', width: 140 },
  {
    title: '严重程度',
    key: 'severity',
    width: 100,
    render: (row: SecurityAlert) =>
      h(NTag, { type: severityColorMap[row.severity] || 'default', size: 'small' }, { default: () => row.severity }),
  },
  { title: '标题', key: 'title', ellipsis: { tooltip: true } },
  { title: '来源IP', key: 'sourceIp', width: 130 },
  { title: '用户', key: 'userId', width: 100 },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row: SecurityAlert) =>
      h(NTag, { type: row.status === 'NEW' ? 'error' : row.status === 'RESOLVED' ? 'success' : 'warning', size: 'small' }, { default: () => row.status }),
  },
  { title: '时间', key: 'createdAt', width: 170 },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row: SecurityAlert) =>
      row.status === 'NEW' || row.status === 'CONFIRMED'
        ? h(NButton, { type: 'primary', size: 'small', onClick: () => openHandleModal(row) }, { default: () => '处理' })
        : null,
  },
]

const severityCount = (sev: AlertSeverity) => {
  const item = stats.value.bySeverity.find((entry: AlertCountBySeverity) => entry.severity === sev)
  return item ? item.count : 0
}
const statusCount = (st: AlertStatus) => {
  const item = stats.value.byStatus.find((entry: AlertCountByStatus) => entry.status === st)
  return item ? item.count : 0
}

const loadData = async () => {
  loading.value = true
  try {
    // const res = await securityAlertApi.page(pagination.page, pagination.pageSize, searchForm)
    // alertList.value = res.data.records
    // pagination.itemCount = res.data.total
    // stats.value = (await securityAlertApi.getStats()).data
  } catch (error: unknown) {
    message.error(`加载失败: ${getErrorMessage(error, '未知错误')}`)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { severity: null, status: null, alertType: null }); loadData() }
const handlePageChange = (page: number) => { pagination.page = page; loadData() }
const openHandleModal = (row: SecurityAlert) => { handleForm.alertId = row.alertId; showHandleModal.value = true }
const handleAlert = async () => {
  try {
    // await securityAlertApi.handle(handleForm.alertId, handleForm)
    message.success('处理成功')
    loadData()
  } catch (error: unknown) {
    message.error(`处理失败: ${getErrorMessage(error, '未知错误')}`)
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
/* 使用全局样式类 */
</style>
