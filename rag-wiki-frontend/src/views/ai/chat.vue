<template>
  <div class="chat-page">
    <!-- 左侧会话列表 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <n-button type="primary" block @click="newSession">新建对话</n-button>
      </div>
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { active: currentSessionId === s.id }]"
          @click="switchSession(s.id)"
        >
          <div class="session-title">{{ s.title }}</div>
          <div class="session-time">{{ formatTime(s.updatedAt) }}</div>
        </div>
      </div>
    </div>

    <!-- 右侧对话区域 -->
    <div class="chat-main">
      <div class="chat-messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="empty-state">
          <h3>智维Wiki 智能问答</h3>
          <p>基于企业知识库的AI助手，支持权限安全隔离</p>
          <div class="quick-questions">
            <n-tag v-for="q in quickQuestions" :key="q" @click="askQuick(q)" style="cursor: pointer; margin: 4px;">
              {{ q }}
            </n-tag>
          </div>
        </div>

        <div v-for="msg in messages" :key="msg.id" :class="['chat-message', msg.role]">
          <div class="message-avatar">
            <n-avatar v-if="msg.role === 'user'" :size="32">我</n-avatar>
            <n-avatar v-else :size="32" style="background: #18a058;">AI</n-avatar>
          </div>
          <div class="message-body">
            <div class="message-content" v-html="sanitizeHTML(msg.content)"></div>
            <div v-if="msg.sources?.length" class="message-sources">
              <div class="sources-label">引用来源:</div>
              <n-tag v-for="s in msg.sources" :key="s.document_name" size="small" type="info" style="margin: 2px;">
                {{ s.document_name }}{{ s.page_num ? ` P${s.page_num}` : '' }}
              </n-tag>
            </div>
            <div v-if="msg.role === 'assistant'" class="message-actions">
              <n-button text size="tiny" @click="copyMessage(msg.content)">复制</n-button>
              <n-button text size="tiny" type="success" @click="feedback(msg.id, 'like')">赞</n-button>
              <n-button text size="tiny" type="error" @click="feedback(msg.id, 'dislike')">踩</n-button>
            </div>
          </div>
        </div>

        <!-- 流式输出中 -->
        <div v-if="streamingContent" class="chat-message assistant">
          <div class="message-avatar">
            <n-avatar :size="32" style="background: #18a058;">AI</n-avatar>
          </div>
          <div class="message-body">
            <div class="message-content" v-html="sanitizeHTML(streamingContent)"></div>
            <n-spin size="small" />
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input-area">
        <div class="input-toolbar">
          <n-select v-model:value="selectedSpaceId" :options="spaceOptions" placeholder="选择知识库(可选)" clearable size="small" style="width: 200px;" />
        </div>
        <div class="input-row">
          <n-input
            v-model:value="question"
            type="textarea"
            placeholder="请输入您的问题... (Ctrl+Enter 发送)"
            :autosize="{ minRows: 2, maxRows: 6 }"
            @keydown.enter.ctrl="handleSend"
          />
          <n-button type="primary" :loading="loading" :disabled="!question.trim()" @click="handleSend" style="margin-left: 12px; height: 40px;">
            发送
          </n-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ragApi, spaceApi } from '@/api'
import type { RAGAnswerVO, SpaceVO } from '@/types/api'
import { sanitizeHTML } from '@/utils/sanitize'
import dayjs from 'dayjs'

interface Source {
  document_name: string
  page_num?: number
  chunk_id?: string
}

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  sources?: Source[]
}

interface Session {
  id: string
  title: string
  updatedAt: number
  messages: Message[]
}

interface StreamPacket {
  type?: 'sources' | 'token' | 'error' | 'done'
  data?: Source[] | string | { session_id?: string }
}

function extractSessionId(payload: StreamPacket['data']) {
  if (payload && !Array.isArray(payload) && typeof payload === 'object') {
    return payload.session_id
  }
  return undefined
}

const SESSION_STORAGE_KEY = 'rag-chat-sessions'

const question = ref('')
const loading = ref(false)
const messages = ref<Message[]>([])
const sessions = ref<Session[]>([])
const currentSessionId = ref('')
const streamingContent = ref('')
const messagesRef = ref<HTMLElement>()
const selectedSpaceId = ref<string | null>(null)
const spaceOptions = ref<{ label: string; value: string }[]>([])

const quickQuestions = [
  '公司信息安全规范有哪些要点？',
  '如何申请敏感文档的访问权限？',
  '新员工入职需要完成哪些安全培训？',
]

onMounted(() => {
  loadSpaces()
  restoreSessions()
})

async function loadSpaces() {
  try {
    const res = await spaceApi.list({ pageNum: 1, pageSize: 100 })
    spaceOptions.value = (res.data?.records || []).map((s: SpaceVO) => ({
      label: s.spaceName,
      value: s.spaceId,
    }))
  } catch (error) {
    console.error('[Chat] 加载知识库失败', error)
  }
}

function newSession() {
  const session: Session = {
    id: Date.now().toString(),
    title: '新对话',
    updatedAt: Date.now(),
    messages: [],
  }
  sessions.value.unshift(session)
  currentSessionId.value = session.id
  messages.value = []
  persistSessions()
}

function switchSession(id: string) {
  const session = sessions.value.find(s => s.id === id)
  if (session) {
    currentSessionId.value = id
    messages.value = session.messages
  }
}

function askQuick(q: string) {
  question.value = q
  handleSend()
}

async function handleSend() {
  if (!question.value.trim() || loading.value) return
  const q = question.value
  question.value = ''

  messages.value.push({ id: Date.now().toString(), role: 'user', content: q })
  updateSession()
  loading.value = true
  streamingContent.value = ''

  try {
    // 尝试SSE流式
    const response = await ragApi.queryStream({
      question: q,
      session_id: currentSessionId.value,
      space_id: selectedSpaceId.value || undefined,
      stream: true,
    })

    if (response.ok && response.body) {
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let fullContent = ''
      let sources: Source[] = []
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() || ''

        for (const line of lines) {
          const payload = line.trim()
          if (!payload.startsWith('{')) continue
          try {
            const data = JSON.parse(payload) as StreamPacket
            if (data.type === 'sources') {
              sources = Array.isArray(data.data) ? data.data : []
            } else if (data.type === 'token') {
              fullContent += typeof data.data === 'string' ? data.data : ''
              streamingContent.value = fullContent
              scrollToBottom()
            } else if (data.type === 'error') {
              throw new Error(typeof data.data === 'string' ? data.data : '流式回答失败')
            } else if (data.type === 'done') {
              const sessionId = extractSessionId(data.data)
              if (sessionId) {
                syncCurrentSessionId(sessionId)
              }
            }
          } catch (parseError) {
            console.warn('[Chat] SSE数据解析失败', parseError)
          }
        }
      }

      const remaining = buffer.trim()
      if (remaining.startsWith('{')) {
        try {
          const data = JSON.parse(remaining) as StreamPacket
          if (data.type === 'done') {
            const sessionId = extractSessionId(data.data)
            if (sessionId) {
              syncCurrentSessionId(sessionId)
            }
          }
        } catch (parseError) {
          console.warn('[Chat] 尾包解析失败', parseError)
        }
      }

      messages.value.push({
        id: Date.now().toString(),
        role: 'assistant',
        content: fullContent,
        sources,
      })
    } else {
      // 回退到非流式
      const response = await ragApi.query({
        question: q,
        session_id: currentSessionId.value,
        space_id: selectedSpaceId.value || undefined,
      })
      const res: Partial<RAGAnswerVO> = response.data || {}
      messages.value.push({
        id: Date.now().toString(),
        role: 'assistant',
        content: res.answer || '暂无回答',
        sources: res.sources || [],
      })
      if (res.session_id) {
        syncCurrentSessionId(res.session_id)
      }
    }
  } catch (error: unknown) {
    messages.value.push({
      id: Date.now().toString(),
      role: 'assistant',
      content: '抱歉，回答生成失败：' + (error instanceof Error ? error.message : '未知错误'),
    })
  } finally {
    streamingContent.value = ''
    loading.value = false
    updateSession()
    scrollToBottom()
  }
}

function updateSession() {
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (session) {
    session.messages = [...messages.value]
    session.updatedAt = Date.now()
    if (messages.value.length > 0 && session.title === '新对话') {
      session.title = messages.value[0].content.slice(0, 20) + '...'
    }
    persistSessions()
  }
}

function syncCurrentSessionId(nextId: string) {
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (session) {
    session.id = nextId
  }
  currentSessionId.value = nextId
  persistSessions()
}

function persistSessions() {
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(sessions.value))
}

function restoreSessions() {
  const saved = localStorage.getItem(SESSION_STORAGE_KEY)
  if (!saved) {
    newSession()
    return
  }

  try {
    const parsed = JSON.parse(saved) as Session[]
    if (!Array.isArray(parsed) || parsed.length === 0) {
      newSession()
      return
    }
    sessions.value = parsed
    currentSessionId.value = parsed[0].id
    messages.value = [...(parsed[0].messages || [])]
  } catch {
    newSession()
  }
}

function feedback(messageId: string, type: string) {
  ragApi.feedback({
    session_id: currentSessionId.value,
    message_id: messageId,
    feedback: type,
  }).catch((e) => console.warn('[Chat] 反馈提交失败', e))
}

function copyMessage(content: string) {
  navigator.clipboard.writeText(content.replace(/<[^>]+>/g, ''))
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function formatTime(ts: number) {
  return dayjs(ts).format('HH:mm')
}
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 96px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.chat-sidebar {
  width: 240px;
  border-right: 1px solid #efeff5;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #efeff5;
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}

.session-item:hover, .session-item.active {
  background: #f0faf4;
}

.session-title {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #666;
}

.empty-state h3 {
  font-size: 22px;
  margin-bottom: 8px;
  color: #333;
}

.quick-questions {
  margin-top: 20px;
}

.chat-message {
  display: flex;
  margin-bottom: 20px;
}

.chat-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
  margin: 0 12px;
}

.message-body {
  max-width: 75%;
}

.chat-message.user .message-content {
  background: #18a058;
  color: white;
  border-radius: 12px 12px 0 12px;
  padding: 10px 16px;
}

.chat-message.assistant .message-content {
  background: #f4f4f5;
  border-radius: 12px 12px 12px 0;
  padding: 10px 16px;
  line-height: 1.6;
}

.message-content :deep(p) {
  margin: 4px 0;
}

.message-content :deep(code) {
  background: rgba(0,0,0,0.06);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
}

.message-content :deep(pre) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}

.message-content :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}

.message-content :deep(th), .message-content :deep(td) {
  border: 1px solid #ddd;
  padding: 6px 10px;
  font-size: 13px;
}

.message-sources {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e0e0e0;
}

.sources-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.message-actions {
  margin-top: 4px;
  display: flex;
  gap: 8px;
}

.chat-input-area {
  border-top: 1px solid #efeff5;
  padding: 12px 20px;
}

.input-toolbar {
  margin-bottom: 8px;
}

.input-row {
  display: flex;
  align-items: flex-end;
}
</style>
