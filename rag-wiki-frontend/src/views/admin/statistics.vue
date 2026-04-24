<template>
  <div class="statistics-container">
    <n-h2>统计报表</n-h2>
    
    <!-- 概览卡片 -->
    <n-grid :cols="4" :x-gap="16" :y-gap="16" class="overview-cards">
      <n-grid-item>
        <n-card>
          <n-statistic label="总用户数" :value="overview.totalUsers || 0">
            <template #prefix>
              <n-icon :component="UserOutlined" />
            </template>
          </n-statistic>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card>
          <n-statistic label="总文档数" :value="overview.totalDocuments || 0">
            <template #prefix>
              <n-icon :component="FileTextOutlined" />
            </template>
          </n-statistic>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card>
          <n-statistic label="知识库数" :value="overview.totalSpaces || 0">
            <template #prefix>
              <n-icon :component="DatabaseOutlined" />
            </template>
          </n-statistic>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card>
          <n-statistic label="今日新增文档" :value="overview.todayNewDocuments || 0">
            <template #prefix>
              <n-icon :component="PlusOutlined" />
            </template>
          </n-statistic>
        </n-card>
      </n-grid-item>
    </n-grid>

    <!-- AI调用统计 -->
    <n-card title="AI调用统计" class="stat-card">
      <n-grid :cols="3" :x-gap="16">
        <n-grid-item>
          <n-statistic label="总查询次数" :value="aiStats.totalQueries || 0" />
        </n-grid-item>
        <n-grid-item>
          <n-statistic label="成功次数" :value="aiStats.successQueries || 0" />
        </n-grid-item>
        <n-grid-item>
          <n-statistic label="平均响应时间" :value="aiStats.avgResponseTime || 0" suffix="ms" />
        </n-grid-item>
      </n-grid>
      
      <!-- 趋势图表 -->
      <div ref="aiTrendChart" class="chart-container"></div>
    </n-card>

    <!-- 文档统计 -->
    <n-grid :cols="2" :x-gap="16" class="stat-row">
      <n-grid-item>
        <n-card title="文档类型分布">
          <div ref="docTypeChart" class="chart-container"></div>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card title="知识库文档数">
          <n-list>
            <n-list-item v-for="item in documentStats.bySpace" :key="item.spaceName">
              <n-thing :title="item.spaceName">
                <template #description>
                  <n-tag>{{ item.count }} 个文档</n-tag>
                </template>
              </n-thing>
            </n-list-item>
          </n-list>
        </n-card>
      </n-grid-item>
    </n-grid>

    <!-- 热门查询 -->
    <n-card title="热门查询 TOP10" class="stat-card">
      <n-list>
        <n-list-item v-for="(item, index) in hotQueries" :key="index">
          <n-thing>
            <template #header>
              <n-tag :type="index < 3 ? 'error' : 'default'">{{ index + 1 }}</n-tag>
              <span class="query-text">{{ item.query }}</span>
            </template>
            <template #description>
              <n-tag size="small">{{ item.count }} 次</n-tag>
            </template>
          </n-thing>
        </n-list-item>
      </n-list>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useMessage } from 'naive-ui'
import * as echarts from 'echarts'
import {
  UserOutlined,
  FileTextOutlined,
  DatabaseOutlined,
  PlusOutlined
} from '@vicons/antd'
import { statisticsApi } from '@/api'

interface OverviewStats {
  totalUsers?: number
  totalDocuments?: number
  totalSpaces?: number
  todayNewDocuments?: number
}

interface TrendPoint {
  date: string
  count: number
}

interface AIStats {
  totalQueries?: number
  successQueries?: number
  avgResponseTime?: number
  dailyTrend?: TrendPoint[]
}

interface SpaceCount {
  spaceName: string
  count: number
}

interface TypeCount {
  type?: string
  count: number
}

interface DocumentStats {
  bySpace: SpaceCount[]
  byType: TypeCount[]
}

interface HotQuery {
  query: string
  count: number
}

interface DashboardPayload {
  overview?: OverviewStats
  aiStats?: AIStats
  documentStats?: Partial<DocumentStats>
  hotQueries?: HotQuery[]
}

const message = useMessage()

// 数据
const overview = ref<OverviewStats>({})
const aiStats = ref<AIStats>({})
const documentStats = ref<DocumentStats>({ bySpace: [], byType: [] })
const hotQueries = ref<HotQuery[]>([])

// 图表引用
const aiTrendChart = ref<HTMLElement>()
const docTypeChart = ref<HTMLElement>()

// 加载数据
const loadData = async () => {
  try {
    const response = await statisticsApi.getDashboardData()
    const data = (response.data || {}) as DashboardPayload
    overview.value = data.overview || {}
    aiStats.value = data.aiStats || {}
    documentStats.value = {
      bySpace: data.documentStats?.bySpace || [],
      byType: data.documentStats?.byType || [],
    }
    hotQueries.value = data.hotQueries || []
    
    // 渲染图表
    nextTick(() => {
      renderAITrendChart()
      renderDocTypeChart()
    })
  } catch {
    message.error('加载统计数据失败')
  }
}

// 渲染AI趋势图
const renderAITrendChart = () => {
  if (!aiTrendChart.value || !aiStats.value.dailyTrend) return
  
  const chart = echarts.init(aiTrendChart.value)
  const trend = aiStats.value.dailyTrend
  
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: trend.map((item) => item.date)
    },
    yAxis: { type: 'value' },
    series: [{
      name: '查询次数',
      type: 'line',
      data: trend.map((item) => item.count),
      smooth: true,
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(64, 128, 128, 0.3)' },
            { offset: 1, color: 'rgba(64, 128, 128, 0.05)' }
          ]
        }
      }
    }]
  })
}

// 渲染文档类型饼图
const renderDocTypeChart = () => {
  if (!docTypeChart.value || !documentStats.value.byType) return
  
  const chart = echarts.init(docTypeChart.value)
  const typeData = documentStats.value.byType
  
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie',
      radius: '50%',
      data: typeData.map((item) => ({
        name: item.type || '未知',
        value: item.count
      })),
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }]
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.statistics-container {
  padding: 20px;
}

.overview-cards {
  margin-bottom: 20px;
}

.stat-card {
  margin-bottom: 20px;
}

.stat-row {
  margin-bottom: 20px;
}

.chart-container {
  height: 300px;
  margin-top: 20px;
}

.query-text {
  margin-left: 8px;
  font-size: 14px;
}
</style>
