<template>
  <n-card title="AI深度研究" class="agent-page">
    <n-grid :cols="3" :x-gap="16" class="main-grid">
      <!-- 左侧：任务列表 -->
      <n-gi :span="1">
        <n-card title="研究任务" size="small" class="task-list">
          <template #header-extra>
            <n-button type="primary" size="small" @click="handleNewTask">
              <template #icon><n-icon><AddOutline /></n-icon></template>
              新建
            </n-button>
          </template>
          <n-list hoverable>
            <n-list-item
              v-for="task in taskList"
              :key="task.taskId"
              :class="{ active: currentTask?.taskId === task.taskId }"
              @click="handleSelectTask(task)"
            >
              <n-thing>
                <template #header>
                  <n-space align="center">
                    <n-text strong>{{ task.title || '未命名任务' }}</n-text>
                    <n-tag :type="getStatusType(task.status)" size="small">
                      {{ getStatusLabel(task.status) }}
                    </n-tag>
                  </n-space>
                </template>
                <template #description>
                  <n-text depth="3" class="task-query">{{ task.query?.slice(0, 50) }}...</n-text>
                  <n-space justify="space-between" style="margin-top: 8px">
                    <n-text depth="3" style="font-size: 12px">{{ formatTime(task.createdAt) }}</n-text>
                    <n-button
                      v-if="task.status === 'RUNNING'"
                      text
                      type="error"
                      size="tiny"
                      @click.stop="handleCancel(task)"
                    >
                      取消
                    </n-button>
                  </n-space>
                </template>
              </n-thing>
            </n-list-item>
          </n-list>
          <n-empty v-if="!taskList.length" description="暂无任务" />
        </n-card>
      </n-gi>

      <!-- 中间/右侧：任务详情 -->
      <n-gi :span="2">
        <n-card v-if="currentTask" title="任务详情" size="small" class="task-detail">
          <template #header-extra>
            <n-space>
              <n-tag :type="getStatusType(currentTask.status)">
                {{ getStatusLabel(currentTask.status) }}
              </n-tag>
              <n-button v-if="currentTask.status === 'RUNNING'" type="error" size="small" @click="handleCancel(currentTask)">
                取消任务
              </n-button>
            </n-space>
          </template>

          <!-- 研究主题 -->
          <n-descriptions :column="2" bordered size="small" style="margin-bottom: 16px">
            <n-descriptions-item label="研究主题" :span="2">
              <n-text strong style="font-size: 16px">{{ currentTask.query }}</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="创建时间">{{ currentTask.createdAt }}</n-descriptions-item>
            <n-descriptions-item label="完成时间">{{ currentTask.completedAt || '-' }}</n-descriptions-item>
          </n-descriptions>

          <!-- 执行进度 -->
          <div v-if="currentTask.status === 'RUNNING'" class="progress-section">
            <n-text>执行进度</n-text>
            <n-progress
              type="line"
              :percentage="currentTask.progress || 0"
              :indicator-placement="'inside'"
              processing
            />
          </div>

          <!-- 执行步骤 -->
          <n-timeline v-if="currentTask.steps?.length" style="margin-top: 16px">
            <n-timeline-item
              v-for="(step, index) in currentTask.steps"
              :key="index"
              :type="getStepType(step.status)"
              :title="step.name"
              :content="step.description"
              :time="step.timestamp"
            />
          </n-timeline>

          <!-- 研究结果 -->
          <div v-if="currentTask.result" class="result-section">
            <n-divider title-placement="left">研究结果</n-divider>
            <n-card embedded class="result-card">
              <div class="markdown-content" v-html="sanitizeHTML(renderMarkdown(currentTask.result))"></div>
            </n-card>
          </div>

          <!-- 引用来源 -->
          <div v-if="currentTask.sources?.length" class="sources-section">
            <n-divider title-placement="left">引用来源</n-divider>
            <n-list>
              <n-list-item v-for="(source, index) in currentTask.sources" :key="index">
                <n-thing>
                  <template #header>
                    <n-space>
                      <n-tag size="small">{{ source.type }}</n-tag>
                      <n-text strong>{{ source.title }}</n-text>
                    </n-space>
                  </template>
                  <template #description>
                    <n-text depth="3">{{ source.content?.slice(0, 100) }}...</n-text>
                  </template>
                </n-thing>
              </n-list-item>
            </n-list>
          </div>
        </n-card>

        <n-empty v-else description="选择或创建一个研究任务" style="padding: 100px 0" />
      </n-gi>
    </n-grid>

    <!-- 新建任务弹窗 -->
    <n-modal
      v-model:show="newTaskVisible"
      title="新建深度研究任务"
      preset="card"
      style="width: 600px"
    >
      <n-form :model="newTaskForm" label-placement="top">
        <n-form-item label="研究主题">
          <n-input
            v-model:value="newTaskForm.query"
            type="textarea"
            :rows="3"
            placeholder="输入您想要深入研究的主题或问题..."
          />
        </n-form-item>
        <n-form-item label="研究深度">
          <n-radio-group v-model:value="newTaskForm.depth">
            <n-radio-button value="quick">快速</n-radio-button>
            <n-radio-button value="standard">标准</n-radio-button>
            <n-radio-button value="deep">深度</n-radio-button>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="知识库范围">
          <n-select
            v-model:value="newTaskForm.spaces"
            :options="spaceOptions"
            multiple
            placeholder="选择要检索的知识库（不选则检索全部）"
          />
        </n-form-item>
        <n-form-item label="工具选择">
          <n-checkbox-group v-model:value="newTaskForm.tools">
            <n-space>
              <n-checkbox value="search">知识库检索</n-checkbox>
              <n-checkbox value="web">网络搜索</n-checkbox>
              <n-checkbox value="code">代码执行</n-checkbox>
              <n-checkbox value="calc">计算工具</n-checkbox>
            </n-space>
          </n-checkbox-group>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="newTaskVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitLoading" @click="handleSubmitTask">
            开始研究
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useMessage } from 'naive-ui'
import { AddOutline } from '@vicons/ionicons5'
import { agentApi, spaceApi } from '@/api'
import { sanitizeHTML } from '@/utils/sanitize'

const message = useMessage()

// 任务列表
const taskList = ref<any[]>([])
const currentTask = ref<any>(null)
const loading = ref(false)
let refreshTimer: number | null = null

// 新建任务弹窗
const newTaskVisible = ref(false)
const submitLoading = ref(false)
const spaceOptions = ref<{ label: string; value: string }[]>([])

const newTaskForm = reactive({
  query: '',
  depth: 'standard',
  spaces: [] as string[],
  tools: ['search', 'web'],
})

// 状态映射
function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待执行',
    RUNNING: '执行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[status] || status
}

function getStatusType(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'default',
    RUNNING: 'warning',
    COMPLETED: 'success',
    FAILED: 'error',
    CANCELLED: 'default',
  }
  return map[status] || 'default'
}

function getStepType(status: string): string {
  const map: Record<string, string> = {
    pending: 'default',
    running: 'warning',
    completed: 'success',
    failed: 'error',
  }
  return map[status] || 'default'
}

// 格式化时间
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

// 渲染Markdown（简单实现）
function renderMarkdown(content: string): string {
  if (!content) return ''
  return content
    .replace(/### (.*)/g, '<h3>$1</h3>')
    .replace(/## (.*)/g, '<h2>$1</h2>')
    .replace(/# (.*)/g, '<h1>$1</h1>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
}

// 加载任务列表
async function loadTasks() {
  try {
    // 模拟数据，实际应调用API
    if (taskList.value.length === 0) {
      taskList.value = [
        {
          taskId: 'task_001',
          title: '深度学习在NLP中的应用',
          query: '请深入研究深度学习在自然语言处理领域的最新应用和发展趋势',
          status: 'COMPLETED',
          progress: 100,
          createdAt: new Date(Date.now() - 86400000).toISOString(),
          completedAt: new Date(Date.now() - 86000000).toISOString(),
          steps: [
            { name: '任务初始化', description: '准备研究环境', status: 'completed', timestamp: '2026-04-10 10:00:00' },
            { name: '知识库检索', description: '检索相关文档15篇', status: 'completed', timestamp: '2026-04-10 10:05:00' },
            { name: '网络搜索', description: '获取最新论文10篇', status: 'completed', timestamp: '2026-04-10 10:10:00' },
            { name: '分析总结', description: '生成研究报告', status: 'completed', timestamp: '2026-04-10 10:20:00' },
          ],
          result: '## 研究总结\n\n深度学习在NLP领域取得了显著进展...\n\n### 主要应用\n1. **大语言模型**：GPT、BERT等\n2. **机器翻译**：Transformer架构\n3. **文本生成**：对话系统、内容创作',
          sources: [
            { type: '文档', title: '深度学习综述', content: '深度学习是机器学习的一个分支...' },
            { type: '论文', title: 'Attention is All You Need', content: '我们提出了一种新的网络架构...' },
          ],
        },
        {
          taskId: 'task_002',
          title: '企业知识管理最佳实践',
          query: '研究企业知识管理的最佳实践和方法论',
          status: 'RUNNING',
          progress: 65,
          createdAt: new Date(Date.now() - 3600000).toISOString(),
          steps: [
            { name: '任务初始化', description: '准备研究环境', status: 'completed', timestamp: '2026-04-11 09:00:00' },
            { name: '知识库检索', description: '检索相关文档8篇', status: 'completed', timestamp: '2026-04-11 09:10:00' },
            { name: '网络搜索', description: '搜索进行中...', status: 'running', timestamp: '2026-04-11 09:30:00' },
            { name: '分析总结', description: '等待中', status: 'pending', timestamp: '-' },
          ],
        },
      ]
    }
  } catch (error) {
    console.error('加载任务失败', error)
  }
}

// 加载知识库选项
async function loadSpaces() {
  try {
    const res: any = await spaceApi.list({ pageSize: 100 })
    if (res.code === 200) {
      spaceOptions.value = (res.data?.records || []).map((s: any) => ({
        label: s.spaceName,
        value: s.spaceId,
      }))
    }
  } catch (error) {
    console.error('加载知识库失败', error)
  }
}

// 选择任务
function handleSelectTask(task: any) {
  currentTask.value = task
}

// 新建任务
function handleNewTask() {
  newTaskForm.query = ''
  newTaskForm.depth = 'standard'
  newTaskForm.spaces = []
  newTaskForm.tools = ['search', 'web']
  newTaskVisible.value = true
}

// 提交任务
async function handleSubmitTask() {
  if (!newTaskForm.query.trim()) {
    message.warning('请输入研究主题')
    return
  }

  submitLoading.value = true
  try {
    const res: any = await agentApi.submit({
      query: newTaskForm.query,
      depth: newTaskForm.depth,
      spaces: newTaskForm.spaces,
      tools: newTaskForm.tools,
    })

    if (res.code === 200) {
      message.success('任务已提交')
      newTaskVisible.value = false
      // 添加到列表
      taskList.value.unshift({
        taskId: res.data.taskId,
        title: newTaskForm.query.slice(0, 20) + '...',
        query: newTaskForm.query,
        status: 'PENDING',
        progress: 0,
        createdAt: new Date().toISOString(),
        steps: [],
      })
      // 开始轮询状态
      startRefreshTimer()
    }
  } catch (error) {
    message.error('提交失败')
  } finally {
    submitLoading.value = false
  }
}

// 取消任务
async function handleCancel(task: any) {
  try {
    const res: any = await agentApi.cancel(task.taskId)
    if (res.code === 200) {
      message.success('任务已取消')
      task.status = 'CANCELLED'
    }
  } catch (error) {
    message.error('取消失败')
  }
}

// 刷新任务状态
async function refreshTaskStatus() {
  const runningTasks = taskList.value.filter(t => t.status === 'RUNNING' || t.status === 'PENDING')
  for (const task of runningTasks) {
    try {
      const res: any = await agentApi.getTask(task.taskId)
      if (res.code === 200) {
        Object.assign(task, res.data)
      }
    } catch (error) {
      console.error('刷新任务状态失败', error)
    }
  }
}

// 启动刷新定时器
function startRefreshTimer() {
  if (refreshTimer) return
  refreshTimer = window.setInterval(refreshTaskStatus, 5000)
}

// 停止刷新定时器
function stopRefreshTimer() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  loadTasks()
  loadSpaces()
  startRefreshTimer()
})

onUnmounted(() => {
  stopRefreshTimer()
})
</script>

<style scoped>
.agent-page {
  padding: 16px;
}
.main-grid {
  height: calc(100vh - 140px);
}
.task-list {
  height: 100%;
  overflow-y: auto;
}
.task-list :deep(.n-list-item) {
  cursor: pointer;
}
.task-list :deep(.n-list-item.active) {
  background-color: #f0f9ff;
}
.task-query {
  font-size: 12px;
}
.task-detail {
  height: 100%;
  overflow-y: auto;
}
.progress-section {
  margin: 16px 0;
}
.result-section {
  margin-top: 16px;
}
.result-card {
  max-height: 400px;
  overflow-y: auto;
}
.markdown-content {
  line-height: 1.8;
}
.markdown-content :deep(h1) {
  font-size: 20px;
  margin: 16px 0 12px;
}
.markdown-content :deep(h2) {
  font-size: 18px;
  margin: 14px 0 10px;
}
.markdown-content :deep(h3) {
  font-size: 16px;
  margin: 12px 0 8px;
}
.markdown-content :deep(pre) {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
}
.markdown-content :deep(code) {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
}
.sources-section {
  margin-top: 16px;
}
</style>