<template>
  <div class="page-container fade-in">
    <div class="page-header">
      <h2>Badcase管理</h2>
      <p>AI问答质量问题收集、分析与闭环优化</p>
    </div>
    
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card warning">
        <div class="stat-label">待处理</div>
        <div class="stat-value">{{ stats.pending || 0 }}</div>
        <div class="stat-footer">
          <n-icon :component="WarningOutlined" />
          <span>需要关注</span>
        </div>
      </div>
      
      <div class="stat-card info">
        <div class="stat-label">处理中</div>
        <div class="stat-value">{{ stats.processing || 0 }}</div>
        <div class="stat-footer">
          <n-icon :component="ClockCircleOutlined" />
          <span>正在分析</span>
        </div>
      </div>
      
      <div class="stat-card success">
        <div class="stat-label">已解决</div>
        <div class="stat-value">{{ stats.resolved || 0 }}</div>
        <div class="stat-footer">
          <n-icon :component="CheckCircleOutlined" />
          <span>已完成</span>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-label">本周新增</div>
        <div class="stat-value">{{ stats.weeklyNew || 0 }}</div>
        <div class="stat-footer">
          <n-icon :component="PlusOutlined" />
          <span>近7天</span>
        </div>
      </div>
    </div>
    
    <!-- 搜索栏 -->
    <div class="search-bar content-card">
      <n-select
        v-model:value="searchForm.severity"
        :options="severityOptions"
        placeholder="严重程度"
        clearable
      />
      <n-select
        v-model:value="searchForm.status"
        :options="statusOptions"
        placeholder="处理状态"
        clearable
      />
      <n-input
        v-model:value="searchForm.keyword"
        placeholder="搜索问题描述"
        clearable
      />
      <n-button type="primary" @click="handleSearch">查询</n-button>
      <n-button @click="handleReset">重置</n-button>
    </div>

    <!-- Badcase列表 -->
    <n-data-table
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
      row-key="id"
    />

    <!-- 处理弹窗 -->
    <n-modal
      v-model:show="processVisible"
      title="处理Badcase"
      preset="card"
      style="width: 600px"
    >
      <n-form
        ref="formRef"
        :model="processForm"
        :rules="formRules"
        label-placement="left"
        label-width="100"
      >
        <n-form-item label="问题描述">
          <n-text>{{ currentBadcase?.description }}</n-text>
        </n-form-item>
        <n-form-item label="严重程度">
          <n-tag :type="getSeverityType(currentBadcase?.severity)">
            {{ getSeverityLabel(currentBadcase?.severity) }}
          </n-tag>
        </n-form-item>
        <n-form-item label="处理状态" path="status">
          <n-radio-group v-model:value="processForm.status">
            <n-radio value="PROCESSING">处理中</n-radio>
            <n-radio value="RESOLVED">已解决</n-radio>
            <n-radio value="IGNORED">忽略</n-radio>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="处理说明" path="resolution">
          <n-input
            v-model:value="processForm.resolution"
            type="textarea"
            :rows="4"
            placeholder="描述问题原因和处理方案"
          />
        </n-form-item>
        <n-form-item label="改进措施">
          <n-input
            v-model:value="processForm.improvement"
            type="textarea"
            :rows="3"
            placeholder="描述为避免类似问题采取的改进措施"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="processVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitLoading" @click="handleProcessSubmit">
            提交
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 详情弹窗 -->
    <n-modal
      v-model:show="detailVisible"
      title="Badcase详情"
      preset="card"
      style="width: 700px"
    >
      <n-descriptions :column="1" bordered>
        <n-descriptions-item label="ID">{{ currentBadcase?.id }}</n-descriptions-item>
        <n-descriptions-item label="会话ID">{{ currentBadcase?.sessionId }}</n-descriptions-item>
        <n-descriptions-item label="用户问题">
          <n-text>{{ currentBadcase?.query }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="AI回答">
          <n-text>{{ currentBadcase?.answer }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="问题描述">
          <n-text type="error">{{ currentBadcase?.description }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="严重程度">
          <n-tag :type="getSeverityType(currentBadcase?.severity)">
            {{ getSeverityLabel(currentBadcase?.severity) }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="状态">
          <n-tag :type="getStatusType(currentBadcase?.status)">
            {{ getStatusLabel(currentBadcase?.status) }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="创建时间">{{ currentBadcase?.createdAt }}</n-descriptions-item>
        <n-descriptions-item label="处理说明" v-if="currentBadcase?.resolution">
          <n-text>{{ currentBadcase?.resolution }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="改进措施" v-if="currentBadcase?.improvement">
          <n-text>{{ currentBadcase?.improvement }}</n-text>
        </n-descriptions-item>
      </n-descriptions>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { NButton, NSpace, NTag, useMessage, type DataTableColumns, type FormInst } from 'naive-ui'
import {
  WarningOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  PlusOutlined,
} from '@vicons/antd'
import { badcaseApi } from '@/api'

type TagType = 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error'

interface BadcaseRow {
  id: number
  sessionId?: string
  query?: string
  answer?: string
  description?: string
  severity: string
  status: string
  resolution?: string
  improvement?: string
  createdAt?: string
}

interface BadcaseStats {
  pending?: number
  processing?: number
  resolved?: number
  weeklyNew?: number
}

interface BadcasePageResult {
  records?: BadcaseRow[]
  total?: number
}

interface BadcaseProcessForm {
  status: string
  resolution: string
  improvement: string
}

const message = useMessage()

// 搜索表单
const searchForm = reactive({
  severity: null as string | null,
  status: null as string | null,
  keyword: '',
})

// 严重程度选项
const severityOptions = [
  { label: '严重', value: 'CRITICAL' },
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' },
]

// 状态选项
const statusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已忽略', value: 'IGNORED' },
]

// 统计数据
const stats = reactive({
  pending: 0,
  processing: 0,
  resolved: 0,
  weeklyNew: 0,
})

// 表格数据
const tableData = ref<BadcaseRow[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [20, 50, 100],
})

// 处理弹窗
const processVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInst | null>(null)
const currentBadcase = ref<BadcaseRow | null>(null)

const processForm = reactive<BadcaseProcessForm>({
  status: 'PROCESSING',
  resolution: '',
  improvement: '',
})

const formRules = {
  status: [{ required: true, message: '请选择处理状态', trigger: 'change' }],
  resolution: [{ required: true, message: '请输入处理说明', trigger: 'blur' }],
}

// 详情弹窗
const detailVisible = ref(false)

// 获取严重程度标签
function getSeverityLabel(severity?: string | null): string {
  const map: Record<string, string> = {
    CRITICAL: '严重',
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低',
  }
  return map[severity || ''] || severity || '-'
}

// 获取严重程度类型
function getSeverityType(severity?: string | null): TagType {
  const map: Record<string, TagType> = {
    CRITICAL: 'error',
    HIGH: 'warning',
    MEDIUM: 'default',
    LOW: 'success',
  }
  return map[severity || ''] || 'default'
}

// 获取状态标签
function getStatusLabel(status?: string | null): string {
  const map: Record<string, string> = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    RESOLVED: '已解决',
    IGNORED: '已忽略',
  }
  return map[status || ''] || status || '-'
}

// 获取状态类型
function getStatusType(status?: string | null): TagType {
  const map: Record<string, TagType> = {
    PENDING: 'error',
    PROCESSING: 'warning',
    RESOLVED: 'success',
    IGNORED: 'default',
  }
  return map[status || ''] || 'default'
}

// 表格列定义
const columns: DataTableColumns<BadcaseRow> = [
  { title: 'ID', key: 'id', width: 80 },
  {
    title: '严重程度',
    key: 'severity',
    width: 100,
    render(row) {
      return h(
        NTag,
        { type: getSeverityType(row.severity), size: 'small' },
        { default: () => getSeverityLabel(row.severity) }
      )
    },
  },
  { title: '问题描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render(row) {
      return h(
        NTag,
        { type: getStatusType(row.status), size: 'small' },
        { default: () => getStatusLabel(row.status) }
      )
    },
  },
  { title: '创建时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    fixed: 'right',
    render(row) {
      return h(NSpace, null, {
        default: () => [
          row.status === 'PENDING' &&
            h(
              NButton,
              { size: 'small', type: 'primary', onClick: () => handleProcess(row) },
              { default: () => '处理' }
            ),
          h(
            NButton,
            { size: 'small', onClick: () => handleViewDetail(row) },
            { default: () => '详情' }
          ),
        ],
      })
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
      severity: searchForm.severity || undefined,
      status: searchForm.status || undefined,
      keyword: searchForm.keyword || undefined,
    }

    const res = await badcaseApi.page(params)
    if (res.code === 200) {
      const pageData = (res.data || {}) as BadcasePageResult
      tableData.value = pageData.records || []
      pagination.itemCount = pageData.total || 0
    }
    
    // 加载统计数据
    try {
      const statsRes = await badcaseApi.getStats()
      if (statsRes.code === 200) {
        const summary = (statsRes.data || {}) as BadcaseStats
        stats.pending = summary.pending || 0
        stats.processing = summary.processing || 0
        stats.resolved = summary.resolved || 0
        stats.weeklyNew = summary.weeklyNew || 0
      }
    } catch {
      console.error('加载统计数据失败')
    }
  } catch {
    message.error('加载Badcase列表失败')
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
  searchForm.severity = null
  searchForm.status = null
  searchForm.keyword = ''
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

// 处理Badcase
function handleProcess(row: BadcaseRow) {
  currentBadcase.value = row
  processForm.status = 'PROCESSING'
  processForm.resolution = ''
  processForm.improvement = ''
  processVisible.value = true
}

// 提交处理
async function handleProcessSubmit() {
  if (!currentBadcase.value) return

  await formRef.value?.validate()

  submitLoading.value = true
  try {
    const res = await badcaseApi.process(currentBadcase.value.id, {
      status: processForm.status,
      resolution: processForm.resolution,
      improvement: processForm.improvement,
    })
    if (res.code === 200) {
      message.success('处理成功')
      processVisible.value = false
      loadData()
    }
  } catch {
    message.error('处理失败')
  } finally {
    submitLoading.value = false
  }
}

// 查看详情
function handleViewDetail(row: BadcaseRow) {
  currentBadcase.value = row
  detailVisible.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* 使用全局样式类，无需额外定义 */
</style>
