<template>
  <div class="document-detail">
    <!-- 返回按钮 -->
    <n-page-header :title="documentInfo.documentName || '文档详情'" @back="router.back()">
      <template #subtitle>
        <n-tag :type="statusMap[documentInfo.status]?.type || 'default'" size="small">
          {{ statusMap[documentInfo.status]?.text || documentInfo.status }}
        </n-tag>
      </template>
      <template #extra>
        <n-space>
          <n-button v-if="documentInfo.status === 'DRAFT'" type="primary" @click="handleParse" :loading="parsing">解析文档</n-button>
          <n-button v-if="documentInfo.status === 'PUBLISHED'" @click="handleVectorize" :loading="vectorizing">重新向量化</n-button>
          <n-button type="error" @click="handleDelete">删除</n-button>
        </n-space>
      </template>
    </n-page-header>

    <!-- 文档信息 -->
    <n-card class="info-card" size="small">
      <n-descriptions :column="4" label-placement="left">
        <n-descriptions-item label="文档ID">{{ documentInfo.documentId }}</n-descriptions-item>
        <n-descriptions-item label="文档类型">{{ documentInfo.documentType?.toUpperCase() }}</n-descriptions-item>
        <n-descriptions-item label="文件大小">{{ formatSize(documentInfo.fileSize || 0) }}</n-descriptions-item>
        <n-descriptions-item label="安全密级">
          <n-tag size="small">L{{ documentInfo.securityLevel }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="所属空间">{{ documentInfo.spaceName || '-' }}</n-descriptions-item>
        <n-descriptions-item label="版本">{{ documentInfo.version || '1.0.0' }}</n-descriptions-item>
        <n-descriptions-item label="创建人">{{ documentInfo.creatorName || '-' }}</n-descriptions-item>
        <n-descriptions-item label="创建时间">{{ documentInfo.createdAt }}</n-descriptions-item>
        <n-descriptions-item label="解析时间">{{ documentInfo.parsedAt || '-' }}</n-descriptions-item>
        <n-descriptions-item label="文件路径" :span="2">{{ documentInfo.filePath || '-' }}</n-descriptions-item>
      </n-descriptions>
    </n-card>

    <!-- 解析进度 -->
    <n-card v-if="documentInfo.status === 'PARSING'" title="解析进度" class="progress-card">
      <n-steps :current="parseStep" status="process">
        <n-step title="文件上传" description="文件已上传到存储服务" />
        <n-step title="文档解析" description="提取文本内容和结构" />
        <n-step title="文本分块" description="按策略切分文本块" />
        <n-step title="向量化" description="生成向量索引" />
      </n-steps>
      <n-progress type="line" :percentage="parseProgress" :status="parseProgress === 100 ? 'success' : 'default'" style="margin-top: 16px" />
    </n-card>

    <!-- 标签页 -->
    <n-tabs v-model:value="activeTab" type="card" class="content-card">
      <n-tab-pane name="chunks" tab="文本分块">
        <n-space class="mb-4" justify="space-between">
          <n-space>
            <n-input v-model:value="chunkSearch" placeholder="搜索分块内容" clearable style="width: 240px" />
            <n-button @click="loadChunks">查询</n-button>
          </n-space>
          <n-space>
            <n-statistic label="分块总数" :value="chunkPagination.itemCount" />
            <n-statistic label="平均长度" :value="avgChunkLength" />
          </n-space>
        </n-space>

        <n-data-table
          :columns="chunkColumns"
          :data="chunks"
          :loading="chunkLoading"
          :pagination="chunkPagination"
          :row-key="(row: any) => row.chunkId"
        />
      </n-tab-pane>

      <n-tab-pane name="preview" tab="内容预览">
        <n-card v-if="documentContent" embedded>
          <n-scrollbar style="max-height: 600px">
            <div class="content-preview" v-html="sanitizeHTML(documentContent)"></div>
          </n-scrollbar>
        </n-card>
        <n-empty v-else description="暂无预览内容" />
      </n-tab-pane>

      <n-tab-pane name="versions" tab="版本历史">
        <n-timeline>
          <n-timeline-item v-for="v in versions" :key="v.id" :title="`版本 ${v.version}`" :time="v.createdAt">
            {{ v.changeLog || '无变更说明' }}
          </n-timeline-item>
        </n-timeline>
        <n-empty v-if="!versions.length" description="暂无版本历史" />
      </n-tab-pane>

      <n-tab-pane name="permissions" tab="权限管理">
        <n-space vertical>
          <n-button type="primary" @click="showPermissionModal = true">添加权限</n-button>
          <n-data-table :columns="permissionColumns" :data="permissions" :row-key="(row: any) => row.id" />
        </n-space>
      </n-tab-pane>

      <n-tab-pane name="interactions" tab="互动">
        <n-space vertical :size="16">
          <!-- 收藏 & 点赞 -->
          <n-space>
            <n-button :type="isFavorited ? 'warning' : 'default'" @click="handleToggleFavorite">
              {{ isFavorited ? '已收藏' : '收藏' }}
            </n-button>
            <n-button :type="isLiked ? 'error' : 'default'" @click="handleToggleLike">
              {{ isLiked ? '已点赞' : '点赞' }} ({{ likeCount }})
            </n-button>
          </n-space>

          <!-- 评论区 -->
          <n-card title="评论" size="small">
            <n-space vertical>
              <n-input v-model:value="commentContent" type="textarea" placeholder="发表评论..." :rows="2" />
              <n-button type="primary" size="small" @click="handleAddComment" :disabled="!commentContent">发表评论</n-button>
            </n-space>
            <n-divider style="margin: 12px 0" />
            <n-list v-if="comments.length > 0">
              <n-list-item v-for="comment in comments" :key="comment.commentId">
                <n-thing :title="comment.username || '用户'" :description="comment.content">
                  <template #header-extra>
                    <n-text depth="3" style="font-size: 12px">{{ comment.createdAt }}</n-text>
                  </template>
                </n-thing>
              </n-list-item>
            </n-list>
            <n-empty v-else description="暂无评论" />
          </n-card>
        </n-space>
      </n-tab-pane>
    </n-tabs>

    <!-- 分块详情弹窗 -->
    <n-modal v-model:show="showChunkModal" preset="card" title="分块详情" style="width: 640px">
      <n-descriptions v-if="currentChunk" :column="1" bordered label-placement="left">
        <n-descriptions-item label="分块ID">{{ currentChunk.chunkId }}</n-descriptions-item>
        <n-descriptions-item label="所属文档">{{ currentChunk.documentName }}</n-descriptions-item>
        <n-descriptions-item label="页码">{{ currentChunk.pageNum || '-' }}</n-descriptions-item>
        <n-descriptions-item label="字符数">{{ currentChunk.charCount }}</n-descriptions-item>
        <n-descriptions-item label="分块策略">{{ currentChunk.chunkStrategy || '-' }}</n-descriptions-item>
        <n-descriptions-item label="内容">
          <n-scrollbar style="max-height: 300px">
            <div style="white-space: pre-wrap;">{{ currentChunk.content }}</div>
          </n-scrollbar>
        </n-descriptions-item>
      </n-descriptions>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { documentApi, parseApi, vectorApi, interactionApi } from '@/api'
import { sanitizeHTML } from '@/utils/sanitize'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const documentId = route.params.id as string

const activeTab = ref('chunks')
const parsing = ref(false)
const vectorizing = ref(false)
const chunkLoading = ref(false)
const showChunkModal = ref(false)
const showPermissionModal = ref(false)
const chunkSearch = ref('')
const parseStep = ref(1)
const parseProgress = ref(0)
const documentContent = ref('')
const avgChunkLength = ref(0)

const documentInfo = ref<any>({
  documentId: '',
  documentName: '',
  documentType: '',
  fileSize: 0,
  securityLevel: 1,
  status: 'DRAFT',
  spaceName: '',
  version: '1.0.0',
  createdAt: '',
})

const chunks = ref<any[]>([])
const versions = ref<any[]>([])
const permissions = ref<any[]>([])
const currentChunk = ref<any>(null)

// ==================== 互动相关 ====================
const isFavorited = ref(false)
const isLiked = ref(false)
const likeCount = ref(0)
const comments = ref<any[]>([])
const commentContent = ref('')

async function loadInteractions() {
  try {
    const [favRes, likeCountRes, likeCheckRes, commentsRes]: any[] = await Promise.allSettled([
      interactionApi.checkFavorite(documentId),
      interactionApi.getLikeCount(documentId),
      // 点赞状态 - 通过toggle接口的响应判断，这里先加载点赞数
      Promise.resolve({ status: 'fulfilled', value: { data: false } }),
      interactionApi.getComments(documentId),
    ])
    if (favRes.status === 'fulfilled' && favRes.value?.data !== undefined) {
      isFavorited.value = !!favRes.value.data
    }
    if (likeCountRes.status === 'fulfilled' && likeCountRes.value?.data !== undefined) {
      likeCount.value = likeCountRes.value.data
    }
    if (commentsRes.status === 'fulfilled' && commentsRes.value?.data) {
      comments.value = commentsRes.value.data
    }
  } catch (e: any) {
    console.warn('加载互动数据失败', e)
  }
}

async function handleToggleFavorite() {
  try {
    const res: any = await interactionApi.toggleFavorite(documentId)
    isFavorited.value = !isFavorited.value
    message.success(isFavorited.value ? '已收藏' : '已取消收藏')
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

async function handleToggleLike() {
  try {
    const res: any = await interactionApi.toggleLike(documentId)
    isLiked.value = !isLiked.value
    likeCount.value += isLiked.value ? 1 : -1
    message.success(isLiked.value ? '已点赞' : '已取消点赞')
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

async function handleAddComment() {
  if (!commentContent.value.trim()) return
  try {
    await interactionApi.addComment(documentId, commentContent.value.trim())
    message.success('评论成功')
    commentContent.value = ''
    // 重新加载评论列表
    const res: any = await interactionApi.getComments(documentId)
    if (res.data) comments.value = res.data
  } catch (e: any) {
    message.error(e.message || '评论失败')
  }
}

const chunkPagination = reactive({ page: 1, pageSize: 20, itemCount: 0 })

const statusMap: Record<string, { type: 'default' | 'info' | 'success' | 'warning' | 'error', text: string }> = {
  DRAFT: { type: 'default', text: '草稿' },
  PARSING: { type: 'info', text: '解析中' },
  PUBLISHED: { type: 'success', text: '已发布' },
  FAILED: { type: 'error', text: '解析失败' },
}

const chunkColumns: DataTableColumns = [
  { title: '分块ID', key: 'chunkId', width: 120, ellipsis: { tooltip: true } },
  { title: '页码', key: 'pageNum', width: 70 },
  { title: '字符数', key: 'charCount', width: 80 },
  { title: '内容预览', key: 'content', ellipsis: { tooltip: true } },
  { title: '向量化', key: 'vectorized', width: 80, render: (row: any) => h(NTag, { size: 'small', type: row.vectorized ? 'success' : 'default' }, () => row.vectorized ? '已生成' : '未生成') },
  { title: '创建时间', key: 'createdAt', width: 170 },
  {
    title: '操作', key: 'actions', width: 80,
    render: (row: any) => h(NButton, { size: 'small', onClick: () => { currentChunk.value = row; showChunkModal.value = true } }, () => '详情'),
  },
]

const permissionColumns: DataTableColumns = [
  { title: '类型', key: 'type', width: 100, render: (row: any) => row.type === 'USER' ? '用户' : '部门' },
  { title: '名称', key: 'name' },
  { title: '权限', key: 'permission', width: 100, render: (row: any) => row.permission === 'READ' ? '只读' : '读写' },
  { title: '授权时间', key: 'createdAt', width: 170 },
]

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

async function loadDocumentInfo() {
  try {
    const res: any = await documentApi.get(documentId)
    documentInfo.value = res.data || {}
  } catch (e: any) {
    message.error(e.message || '加载失败')
  }
}

async function loadChunks() {
  chunkLoading.value = true
  try {
    const res: any = await documentApi.list({ page: chunkPagination.page, pageSize: chunkPagination.pageSize, documentId })
    chunks.value = res.data?.records || []
    chunkPagination.itemCount = res.data?.total || 0
    if (chunks.value.length > 0) {
      const total = chunks.value.reduce((sum, c) => sum + (c.charCount || 0), 0)
      avgChunkLength.value = Math.round(total / chunks.value.length)
    }
  } catch (e: any) {
    message.error(e.message || '加载失败')
  } finally {
    chunkLoading.value = false
  }
}

async function handleParse() {
  parsing.value = true
  parseStep.value = 1
  parseProgress.value = 0
  try {
    await parseApi.submit({ documentId, spaceId: documentInfo.value.spaceId })
    message.success('解析任务已提交')
    // 模拟进度更新
    const timer = setInterval(() => {
      if (parseProgress.value < 90) {
        parseProgress.value += 10
        parseStep.value = Math.ceil(parseProgress.value / 25)
      }
    }, 1000)
    setTimeout(() => clearInterval(timer), 10000)
    loadDocumentInfo()
  } catch (e: any) {
    message.error(e.message || '提交失败')
  } finally {
    parsing.value = false
  }
}

async function handleVectorize() {
  vectorizing.value = true
  try {
    await vectorApi.embed({ document_id: documentId })
    message.success('向量化任务已提交')
  } catch (e: any) {
    message.error(e.message || '提交失败')
  } finally {
    vectorizing.value = false
  }
}

async function handleDelete() {
  try {
    await documentApi.delete(documentInfo.value.id)
    message.success('删除成功')
    router.back()
  } catch (e: any) {
    message.error(e.message || '删除失败')
  }
}

async function loadVersions() {
  try {
    const res: any = await documentApi.getVersions(documentId)
    versions.value = res.data || []
  } catch (e: any) {
    console.warn('加载版本历史失败', e)
  }
}

async function loadPermissions() {
  try {
    const res: any = await documentApi.getPermissions(documentId)
    permissions.value = res.data || []
  } catch (e: any) {
    console.warn('加载权限数据失败', e)
  }
}

onMounted(() => {
  loadDocumentInfo()
  loadChunks()
  loadInteractions()
  loadVersions()
  loadPermissions()
  // 记录浏览历史（静默失败）
  interactionApi.recordBrowse(documentId).catch((e) => console.warn('[Document] 记录浏览失败', e))
})
</script>

<style scoped>
.document-detail { padding: 16px; }
.info-card { margin: 16px 0; }
.progress-card { margin: 16px 0; }
.content-card { margin-top: 16px; }
.content-preview { line-height: 1.8; white-space: pre-wrap; }
</style>
