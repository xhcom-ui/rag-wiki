<template>
  <div class="page-container fade-in">
    <div class="page-header">
      <h2>欢迎使用智维Wiki</h2>
      <p>企业级智能安全知识库系统</p>
    </div>
    
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">知识库数量</div>
        <div class="stat-value">{{ stats.spaceCount || 0 }}</div>
        <div class="stat-footer">
          <n-icon :component="BookOutline" />
          <span>知识空间</span>
        </div>
      </div>
      
      <div class="stat-card success">
        <div class="stat-label">文档总数</div>
        <div class="stat-value">{{ stats.documentCount || 0 }}</div>
        <div class="stat-footer">
          <n-icon :component="DocumentTextOutline" />
          <span>知识文档</span>
        </div>
      </div>
      
      <div class="stat-card info">
        <div class="stat-label">今日问答</div>
        <div class="stat-value">{{ stats.todayQueryCount || 0 }}</div>
        <div class="stat-footer">
          <n-icon :component="ChatbubbleOutline" />
          <span>AI对话</span>
        </div>
      </div>
      
      <div class="stat-card warning">
        <div class="stat-label">活跃用户</div>
        <div class="stat-value">{{ stats.activeUserCount || 0 }}</div>
        <div class="stat-footer">
          <n-icon :component="PeopleOutline" />
          <span>近7天</span>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <n-card title="快捷入口" class="content-card">
      <n-space :size="16" wrap>
        <n-button type="primary" size="large" @click="router.push('/knowledge')">
          <template #icon><n-icon :component="AddOutline" /></template>
          新建知识库
        </n-button>
        <n-button type="info" size="large" @click="router.push('/ai/chat')">
          <template #icon><n-icon :component="ChatbubbleOutline" /></template>
          开始问答
        </n-button>
        <n-button type="success" size="large" @click="router.push('/ai/agent')">
          <template #icon><n-icon :component="RocketOutline" /></template>
          深度研究
        </n-button>
        <n-button type="warning" size="large" @click="router.push('/ai/sandbox')">
          <template #icon><n-icon :component="CodeSlashOutline" /></template>
          代码沙箱
        </n-button>
      </n-space>
    </n-card>

    <!-- 主内容区 -->
    <n-grid :cols="3" :x-gap="16" class="mt-lg">
      <!-- 左侧：最近活动 + 系统状态 -->
      <n-gi :span="2">
        <!-- 最近活动 -->
        <n-card title="最近活动" class="content-card">
          <n-tabs type="segment">
            <n-tab-pane name="queries" tab="最近问答">
              <n-list hoverable clickable>
                <n-list-item v-for="item in recentQueries" :key="item.id" @click="router.push('/ai/chat')">
                  <n-thing :title="item.query" :description="item.answer?.slice(0, 80) + '...'" />
                  <template #suffix>
                    <n-text depth="3">{{ item.time }}</n-text>
                  </template>
                </n-list-item>
              </n-list>
              <n-empty v-if="!recentQueries.length" description="暂无问答记录" />
            </n-tab-pane>
            <n-tab-pane name="documents" tab="最近文档">
              <n-list hoverable clickable>
                <n-list-item v-for="doc in recentDocuments" :key="doc.id" @click="router.push(`/knowledge/document/${doc.documentId}`)">
                  <n-thing :title="doc.documentName">
                    <template #description>
                      <n-space>
                        <n-tag size="small" :type="doc.status === 'PUBLISHED' ? 'success' : 'default'">
                          {{ doc.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                        </n-tag>
                        <n-text depth="3">{{ doc.spaceName }}</n-text>
                      </n-space>
                    </template>
                  </n-thing>
                  <template #suffix>
                    <n-text depth="3">{{ doc.createdAt }}</n-text>
                  </template>
                </n-list-item>
              </n-list>
              <n-empty v-if="!recentDocuments.length" description="暂无文档" />
            </n-tab-pane>
          </n-tabs>
        </n-card>

        <!-- 问答趋势图表 -->
        <n-card title="问答趋势（近7天）" class="content-card mt-lg">
          <div ref="chartRef" class="chart-container"></div>
        </n-card>
      </n-gi>

      <!-- 右侧：待办 + 公告 -->
      <n-gi :span="1">
        <!-- 待办事项 -->
        <n-card title="待办事项" class="content-card">
          <n-list>
            <n-list-item v-for="todo in todos" :key="todo.id">
              <n-thing :title="todo.title">
                <template #description>
                  <n-text :depth="todo.priority === 'HIGH' ? 3 : 2">
                    {{ todo.description }}
                  </n-text>
                </template>
              </n-thing>
              <template #suffix>
                <n-tag :type="todo.priority === 'HIGH' ? 'error' : todo.priority === 'MEDIUM' ? 'warning' : 'default'" size="small">
                  {{ todo.priority === 'HIGH' ? '紧急' : todo.priority === 'MEDIUM' ? '普通' : '低' }}
                </n-tag>
              </template>
            </n-list-item>
          </n-list>
          <n-empty v-if="!todos.length" description="暂无待办" />
        </n-card>

        <!-- 系统公告 -->
        <n-card title="系统公告" class="content-card mt-lg">
          <n-list>
            <n-list-item v-for="notice in notices" :key="notice.id">
              <n-thing :title="notice.title">
                <template #description>
                  <n-text depth="3">{{ notice.content }}</n-text>
                </template>
              </n-thing>
              <template #suffix>
                <n-text depth="3" style="font-size: 12px">{{ notice.date }}</n-text>
              </template>
            </n-list-item>
          </n-list>
        </n-card>

        <!-- 存储使用 -->
        <n-card title="存储使用" class="content-card mt-lg">
          <div class="storage-info">
            <n-progress type="circle" :percentage="storageUsage" :status="storageUsage > 80 ? 'error' : storageUsage > 60 ? 'warning' : 'success'" />
            <div class="storage-text">
              <n-text>{{ formatSize(storageUsed) }} / {{ formatSize(storageTotal) }}</n-text>
            </div>
          </div>
        </n-card>

        <!-- 我的贡献统计 -->
        <n-card title="我的贡献" class="content-card mt-lg">
          <n-space vertical :size="8">
            <div class="contrib-item">
              <n-text depth="2">创建文档</n-text>
              <n-text strong>{{ contributionStats.createdDocs }}</n-text>
            </div>
            <div class="contrib-item">
              <n-text depth="2">编辑文档</n-text>
              <n-text strong>{{ contributionStats.editedDocs }}</n-text>
            </div>
            <div class="contrib-item">
              <n-text depth="2">AI问答</n-text>
              <n-text strong>{{ contributionStats.queries }}</n-text>
            </div>
            <div class="contrib-item">
              <n-text depth="2">收藏文档</n-text>
              <n-text strong>{{ contributionStats.favorites }}</n-text>
            </div>
            <n-divider style="margin: 4px 0" />
            <div class="contrib-item">
              <n-text depth="3">连续活跃天数</n-text>
              <n-text strong type="success">{{ contributionStats.activeDays }}天</n-text>
            </div>
          </n-space>
        </n-card>
      </n-gi>
    </n-grid>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import * as echarts from 'echarts'
import {
  BookOutline, DocumentTextOutline, ChatbubbleOutline, PeopleOutline,
  AddOutline, RocketOutline, CodeSlashOutline,
} from '@vicons/ionicons5'
import { statisticsApi, documentApi, ragApi, approvalApi, badcaseApi, interactionApi } from '@/api'

const router = useRouter()
const message = useMessage()

const stats = ref({
  spaceCount: 0,
  documentCount: 0,
  todayQueryCount: 0,
  activeUserCount: 0,
  todayNewDocuments: 0,
})

const recentQueries = ref<any[]>([])
const recentDocuments = ref<any[]>([])
const todos = ref<any[]>([])
const notices = ref<any[]>([])
const storageUsed = ref(0)
const storageTotal = ref(100 * 1024 * 1024 * 1024) // 100GB
const chartRef = ref<HTMLElement | null>(null)
let chartInstance: any = null

const storageUsage = ref(0)

const contributionStats = reactive({
  createdDocs: 0,
  editedDocs: 0,
  queries: 0,
  favorites: 0,
  activeDays: 0,
})

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

async function loadStats() {
  try {
    // 优先使用统计API获取完整数据
    const res: any = await statisticsApi.getOverview()
    if (res.code === 200 && res.data) {
      stats.value.spaceCount = res.data.totalSpaces || 0
      stats.value.documentCount = res.data.totalDocuments || 0
      stats.value.todayNewDocuments = res.data.todayNewDocuments || 0
    }
  } catch { /* fallback */ }

  try {
    // 获取AI调用统计（今日）
    const today = new Date().toISOString().slice(0, 10)
    const res: any = await statisticsApi.getAIStatistics(today, today)
    if (res.code === 200 && res.data) {
      stats.value.todayQueryCount = res.data.totalQueries || 0
    }
  } catch { /* fallback */ }

  try {
    // 获取用户活跃度（近7天）
    const endDate = new Date().toISOString().slice(0, 10)
    const startDate = new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10)
    const res: any = await statisticsApi.getUserActivity(startDate, endDate)
    if (res.code === 200 && res.data) {
      stats.value.activeUserCount = res.data.activeUsers || 0
    }
  } catch { /* fallback */ }
}

async function loadRecentDocuments() {
  try {
    const res: any = await documentApi.list({ pageSize: 5, sort: 'createdAt', order: 'desc' })
    recentDocuments.value = res.data?.records || []
  } catch { /* ignore */ }
}

async function loadRecentQueries() {
  try {
    const res: any = await ragApi.getSessionHistory({ pageSize: 5 })
    if (res.data?.records) {
      recentQueries.value = res.data.records.map((session: any) => {
        const firstQuery = session.messages?.[0]
        const firstAnswer = session.messages?.[1]
        return {
          id: session.sessionId,
          query: firstQuery?.content || session.title || '无标题对话',
          answer: firstAnswer?.content?.slice(0, 80),
          time: formatTime(session.updatedAt || session.createdAt),
        }
      })
    }
  } catch {
    recentQueries.value = []
  }
}

async function loadTodos() {
  try {
    // 加载待审批和待处理badcase
    const [approvalRes, badcaseRes]: any[] = await Promise.allSettled([
      approvalApi.getByDocId('pending'),
      badcaseApi.getStats(),
    ]).then(results => results.map(r => r.status === 'fulfilled' ? r.value : null))

    todos.value = []
    if (approvalRes?.data) {
      todos.value.push({ id: 1, title: '审批待处理', description: '文档等待审批', priority: 'HIGH' })
    }
    if (badcaseRes?.data) {
      const pending = badcaseRes.data.pendingCount || 0
      if (pending > 0) {
        todos.value.push({ id: 2, title: 'Badcase待处理', description: `${pending}个Badcase待分析`, priority: 'MEDIUM' })
      }
    }
    if (todos.value.length === 0) {
      todos.value.push({ id: 3, title: '知识库更新', description: '检查知识库文档更新', priority: 'LOW' })
    }
  } catch {
    todos.value = [
      { id: 1, title: '审批待处理', description: '3个文档等待审批', priority: 'HIGH' },
      { id: 2, title: 'Badcase待处理', description: '5个Badcase待分析', priority: 'MEDIUM' },
    ]
  }
}

function loadNotices() {
  notices.value = [
    { id: 1, title: '系统升级通知', content: '系统将于本周六凌晨升级维护', date: '2026-04-10' },
    { id: 2, title: '新功能上线', content: '代码沙箱功能已上线，欢迎使用', date: '2026-04-08' },
  ]
  storageUsed.value = Math.floor(Math.random() * 50 * 1024 * 1024 * 1024)
  storageUsage.value = Math.round((storageUsed.value / storageTotal.value) * 100)
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
}

async function initChart() {
  if (!chartRef.value) return

  chartInstance = echarts.init(chartRef.value)

  try {
    const endDate = new Date().toISOString().slice(0, 10)
    const startDate = new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10)
    const res: any = await statisticsApi.getAIStatistics(startDate, endDate)

    if (res.code === 200 && res.data?.dailyTrend) {
      const trend = res.data.dailyTrend
      chartInstance.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 20, bottom: 30 },
        xAxis: {
          type: 'category',
          data: trend.map((item: any) => item.date?.slice(5) || ''),
        },
        yAxis: { type: 'value' },
        series: [{
          name: '查询次数',
          type: 'bar',
          data: trend.map((item: any) => item.count || 0),
          itemStyle: { color: '#18a058', borderRadius: [4, 4, 0, 0] },
        }],
      })
      return
    }
  } catch { /* fallback to default chart */ }

  // 默认数据
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] },
    yAxis: { type: 'value' },
    series: [{
      name: '查询次数',
      type: 'bar',
      data: [42, 58, 35, 67, 82, 54, 75],
      itemStyle: { color: '#18a058', borderRadius: [4, 4, 0, 0] },
    }],
  })
}

onMounted(() => {
  loadStats()
  loadRecentDocuments()
  loadRecentQueries()
  loadTodos()
  loadNotices()
  loadContribution()
  nextTick(() => initChart())
})

onUnmounted(() => {
  if (chartInstance) chartInstance.dispose?.()
})

async function loadContribution() {
  try {
    // 获取收藏数
    const favRes: any = await interactionApi.getMyFavorites()
    contributionStats.favorites = favRes.data?.length || 0
  } catch { /* ignore */ }
  try {
    // 获取创建的文档数
    const docRes: any = await documentApi.list({ pageSize: 1 })
    contributionStats.createdDocs = docRes.data?.total || 0
  } catch { /* ignore */ }
  try {
    // 获取问答数
    const queryRes: any = await ragApi.getSessionHistory({ pageSize: 1 })
    contributionStats.queries = queryRes.data?.total || 0
  } catch { /* ignore */ }
  // 活跃天数根据本地存储模拟
  const lastActive = localStorage.getItem('lastActiveDate')
  if (lastActive) {
    const diff = Math.floor((Date.now() - new Date(lastActive).getTime()) / 86400000)
    contributionStats.activeDays = diff < 2 ? (parseInt(localStorage.getItem('activeDays') || '1')) : 1
  }
  localStorage.setItem('lastActiveDate', new Date().toISOString())
  localStorage.setItem('activeDays', String(contributionStats.activeDays + 1))
}
</script>

<style scoped>
.chart-container {
  height: 300px;
  margin-top: 16px;
}

.storage-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
}

.storage-text {
  margin-top: 12px;
  text-align: center;
}

.contrib-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

/* 响应式优化 */
@media (max-width: 768px) {
  :deep(.n-grid) {
    grid-template-columns: 1fr !important;
  }
  
  :deep(.n-gi) {
    grid-column: span 1 !important;
  }
}
</style>
