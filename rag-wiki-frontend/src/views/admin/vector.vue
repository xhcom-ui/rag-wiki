<template>
  <n-card title="向量库管理" class="vector-page">
    <!-- 搜索栏 -->
    <n-space justify="space-between" align="center" class="toolbar">
      <n-space>
        <n-select
          v-model:value="searchForm.collection"
          :options="collectionOptions"
          placeholder="选择集合"
          clearable
          style="width: 180px"
        />
        <n-input
          v-model:value="searchForm.keyword"
          placeholder="搜索文档内容"
          clearable
          style="width: 250px"
        />
        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button @click="handleReset">重置</n-button>
      </n-space>
      <n-space>
        <n-button type="warning" @click="handleRebuildIndex">
          <template #icon>
            <n-icon><RefreshOutline /></n-icon>
          </template>
          重建索引
        </n-button>
      </n-space>
    </n-space>

    <!-- 统计卡片 -->
    <n-grid :cols="4" :x-gap="16" class="stats-row">
      <n-gi>
        <n-statistic label="文档总数" :value="stats.totalDocuments" />
      </n-gi>
      <n-gi>
        <n-statistic label="向量总数" :value="stats.totalVectors" />
      </n-gi>
      <n-gi>
        <n-statistic label="存储大小" :value="formatSize(stats.storageSize)" />
      </n-gi>
      <n-gi>
        <n-statistic label="集合数" :value="stats.collectionCount" />
      </n-gi>
    </n-grid>

    <!-- 向量数据列表 -->
    <n-data-table
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
      row-key="id"
    />

    <!-- 相似度搜索弹窗 -->
    <n-modal
      v-model:show="searchVisible"
      title="相似度搜索"
      preset="card"
      style="width: 800px"
    >
      <n-space vertical>
        <n-input
          v-model:value="searchQuery"
          type="textarea"
          :rows="3"
          placeholder="输入查询文本进行相似度搜索"
        />
        <n-space justify="end">
          <n-button type="primary" :loading="searchLoading" @click="handleSimilaritySearch">
            搜索
          </n-button>
        </n-space>

        <!-- 搜索结果 -->
        <n-list v-if="searchResults.length > 0">
          <n-list-item v-for="(item, index) in searchResults" :key="index">
            <n-thing>
              <template #header>
                <n-space>
                  <n-tag type="info">相似度: {{ (item.score * 100).toFixed(2) }}%</n-tag>
                  <n-text>{{ item.documentName }}</n-text>
                </n-space>
              </template>
              <template #description>
                <n-text depth="3">{{ item.content?.slice(0, 200) }}...</n-text>
              </template>
            </n-thing>
          </n-list-item>
        </n-list>
        <n-empty v-else-if="searched" description="未找到相似内容" />
      </n-space>
    </n-modal>

    <!-- 详情弹窗 -->
    <n-modal
      v-model:show="detailVisible"
      title="向量详情"
      preset="card"
      style="width: 700px"
    >
      <n-descriptions :column="1" bordered>
        <n-descriptions-item label="ID">{{ currentVector?.id }}</n-descriptions-item>
        <n-descriptions-item label="文档ID">{{ currentVector?.documentId }}</n-descriptions-item>
        <n-descriptions-item label="文档名称">{{ currentVector?.documentName }}</n-descriptions-item>
        <n-descriptions-item label="知识库">{{ currentVector?.spaceName }}</n-descriptions-item>
        <n-descriptions-item label="分块索引">{{ currentVector?.chunkIndex }}</n-descriptions-item>
        <n-descriptions-item label="创建时间">{{ currentVector?.createdAt }}</n-descriptions-item>
        <n-descriptions-item label="内容">
          <n-text style="white-space: pre-wrap">{{ currentVector?.content }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="向量维度">{{ currentVector?.dimension }}</n-descriptions-item>
        <n-descriptions-item label="元数据">
          <n-code :code="formatJson(currentVector?.metadata)" language="json" />
        </n-descriptions-item>
      </n-descriptions>
    </n-modal>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useMessage, useDialog, NButton, NPopconfirm, NSpace, type DataTableColumns } from 'naive-ui'
import { RefreshOutline, SearchOutline, EyeOutline, TrashOutline } from '@vicons/ionicons5'
import { vectorApi, vectorAdminApi } from '@/api'

interface VectorStats {
  totalDocuments?: number
  totalVectors?: number
  storageSize?: number
  collectionCount?: number
}

interface VectorRow {
  id: string
  documentId?: string
  documentName?: string
  spaceName?: string
  chunkIndex?: number
  content?: string
  createdAt?: string
  dimension?: number
  metadata?: unknown
}

interface VectorPageResult {
  records?: VectorRow[]
  total?: number
}

interface SimilarityResult {
  score: number
  documentName?: string
  content?: string
}

const message = useMessage()
const dialog = useDialog()

// 搜索表单
const searchForm = reactive({
  collection: null as string | null,
  keyword: '',
})

// 集合选项
const collectionOptions = ref([
  { label: '文档向量库', value: 'documents' },
  { label: '问答向量库', value: 'qa_pairs' },
  { label: '记忆向量库', value: 'memories' },
])

// 统计数据
const stats = reactive({
  totalDocuments: 0,
  totalVectors: 0,
  storageSize: 0,
  collectionCount: 0,
})

// 表格数据
const tableData = ref<VectorRow[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [20, 50, 100],
})

// 相似度搜索
const searchVisible = ref(false)
const searchQuery = ref('')
const searchLoading = ref(false)
const searchResults = ref<SimilarityResult[]>([])
const searched = ref(false)

// 详情弹窗
const detailVisible = ref(false)
const currentVector = ref<VectorRow | null>(null)

// 格式化大小
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
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
const columns: DataTableColumns<VectorRow> = [
  { title: 'ID', key: 'id', width: 100, ellipsis: { tooltip: true } },
  { title: '文档名称', key: 'documentName', width: 200, ellipsis: { tooltip: true } },
  { title: '知识库', key: 'spaceName', width: 150 },
  { title: '分块', key: 'chunkIndex', width: 80, align: 'center' },
  {
    title: '内容预览',
    key: 'content',
    ellipsis: { tooltip: true },
    render(row) {
      return row.content?.slice(0, 100) + '...'
    },
  },
  { title: '创建时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render(row) {
      return h(NSpace, null, {
        default: () => [
          h(
            NButton,
            { size: 'small', onClick: () => handleSearchSimilar(row) },
            { default: () => '相似搜索', icon: () => h(SearchOutline) }
          ),
          h(
            NButton,
            { size: 'small', onClick: () => handleViewDetail(row) },
            { default: () => '详情', icon: () => h(EyeOutline) }
          ),
          h(
            NPopconfirm,
            { onPositiveClick: () => handleDelete(row) },
            {
              trigger: () =>
                h(NButton, { size: 'small', type: 'error' }, {
                  default: () => '删除',
                  icon: () => h(TrashOutline),
                }),
              default: () => '确定删除该向量吗？',
            }
          ),
        ],
      })
    },
  },
]

// 加载统计数据
async function loadStats() {
  try {
    const res = await vectorAdminApi.getStats()
    if (res.code === 200) {
      const summary = (res.data || {}) as VectorStats
      stats.totalDocuments = summary.totalDocuments || 0
      stats.totalVectors = summary.totalVectors || 0
      stats.storageSize = summary.storageSize || 0
      stats.collectionCount = summary.collectionCount || 0
    }
  } catch {
    console.error('加载统计数据失败')
  }
}

// 加载表格数据
async function loadData() {
  loading.value = true
  try {
    const params: Record<string, string | number | undefined> = {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      collection: searchForm.collection || undefined,
      keyword: searchForm.keyword || undefined,
    }
    const res = await vectorAdminApi.page(params)
    if (res.code === 200) {
      const pageData = (res.data || {}) as VectorPageResult
      tableData.value = pageData.records || []
      pagination.itemCount = pageData.total || 0
    }
  } catch {
    message.error('加载向量数据失败')
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
  searchForm.collection = null
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

// 相似度搜索
function handleSearchSimilar(row: VectorRow) {
  currentVector.value = row
  searchQuery.value = row.content || ''
  searchResults.value = []
  searched.value = false
  searchVisible.value = true
}

// 执行相似度搜索
async function handleSimilaritySearch() {
  if (!searchQuery.value.trim()) {
    message.warning('请输入查询文本')
    return
  }

  searchLoading.value = true
  searched.value = false
  try {
    const res = await vectorApi.search({
      query: searchQuery.value,
      top_k: 10,
    })
    if (res.code === 200) {
      searchResults.value = Array.isArray(res.data) ? (res.data as SimilarityResult[]) : []
      searched.value = true
    }
  } catch {
    message.error('搜索失败')
  } finally {
    searchLoading.value = false
  }
}

// 查看详情
function handleViewDetail(row: VectorRow) {
  currentVector.value = row
  detailVisible.value = true
}

// 删除向量
async function handleDelete(row: VectorRow) {
  try {
    const res = await vectorAdminApi.delete(row.id)
    if (res.code === 200) {
      message.success('删除成功')
      loadData()
    }
  } catch {
    message.error('删除失败')
  }
}

// 重建索引
function handleRebuildIndex() {
  dialog.warning({
    title: '确认重建索引',
    content: '重建索引会重新计算所有文档的向量表示，这可能需要较长时间。是否继续？',
    positiveText: '确认',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await vectorAdminApi.rebuildIndex(searchForm.collection || undefined)
        if (res.code === 200) {
          message.success(`索引重建任务已提交，任务ID: ${res.data.taskId}`)
        }
      } catch {
        message.error('操作失败')
      }
    },
  })
}

onMounted(() => {
  loadStats()
  loadData()
})
</script>

<style scoped>
.vector-page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
.stats-row {
  margin-bottom: 16px;
  padding: 16px;
  background: #f5f5f5;
  border-radius: 8px;
}
</style>
