<template>
  <div class="space-detail">
    <!-- 返回按钮 -->
    <n-page-header title="知识库空间" @back="router.back()">
      <template #subtitle>
        <n-tag :type="spaceInfo.visibility === 'PUBLIC' ? 'success' : 'warning'" size="small">
          {{ spaceInfo.visibility === 'PUBLIC' ? '公开' : '私有' }}
        </n-tag>
      </template>
      <template #extra>
        <n-space>
          <n-button @click="handleEditSpace">编辑空间</n-button>
          <n-button type="primary" @click="showUploadModal = true">上传文档</n-button>
        </n-space>
      </template>
    </n-page-header>

    <!-- 空间信息卡片 -->
    <n-card class="info-card" size="small">
      <n-descriptions :column="4" label-placement="left">
        <n-descriptions-item label="空间名称">{{ spaceInfo.spaceName }}</n-descriptions-item>
        <n-descriptions-item label="空间编码">{{ spaceInfo.spaceCode }}</n-descriptions-item>
        <n-descriptions-item label="所属部门">{{ spaceInfo.ownerDeptName || '-' }}</n-descriptions-item>
        <n-descriptions-item label="文档数量">{{ spaceInfo.documentCount || 0 }}</n-descriptions-item>
        <n-descriptions-item label="存储大小">{{ formatSize(spaceInfo.storageSize || 0) }}</n-descriptions-item>
        <n-descriptions-item label="创建人">{{ spaceInfo.creatorName || '-' }}</n-descriptions-item>
        <n-descriptions-item label="创建时间">{{ spaceInfo.createdAt }}</n-descriptions-item>
        <n-descriptions-item label="描述" :span="2">{{ spaceInfo.description || '-' }}</n-descriptions-item>
      </n-descriptions>
    </n-card>

    <!-- 文档列表 -->
    <n-card title="文档列表" class="document-card">
      <template #header-extra>
        <n-space>
          <n-input v-model:value="searchKeyword" placeholder="搜索文档名称" clearable style="width: 200px" />
          <n-select v-model:value="filterStatus" :options="statusOptions" placeholder="状态筛选" clearable style="width: 120px" />
        </n-space>
      </template>

      <n-data-table
        :columns="documentColumns"
        :data="documents"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: any) => row.documentId"
      />
    </n-card>

    <!-- 上传文档弹窗 -->
    <n-modal v-model:show="showUploadModal" preset="dialog" title="上传文档" style="width: 520px">
      <n-upload
        multiple
        directory-dnd
        :file-list="fileList"
        :max="20"
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md"
        @change="handleUploadChange"
      >
        <n-upload-dragger>
          <div style="margin-bottom: 12px">
            <n-icon size="48" :depth="3"><archive-outline /></n-icon>
          </div>
          <n-text style="font-size: 16px">点击或拖拽文件到此区域上传</n-text>
          <n-p depth="3" style="margin: 8px 0 0 0">支持 PDF、Word、Excel、PPT、TXT、MD 格式，单个文件不超过200MB</n-p>
        </n-upload-dragger>
      </n-upload>
      <n-divider style="margin: 12px 0" />
      <n-form :model="uploadForm" label-placement="left" label-width="80px">
        <n-form-item label="安全密级">
          <n-select v-model:value="uploadForm.securityLevel" :options="securityLevelOptions" style="width: 200px" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showUploadModal = false">取消</n-button>
        <n-button type="primary" @click="handleUpload" :loading="uploading" :disabled="!fileList.length">
          开始上传
        </n-button>
      </template>
    </n-modal>

    <!-- 编辑空间弹窗 -->
    <n-modal v-model:show="showEditModal" preset="dialog" title="编辑空间" style="width: 480px">
      <n-form :model="editForm" label-placement="left" label-width="80px" class="mt-4">
        <n-form-item label="空间名称">
          <n-input v-model:value="editForm.spaceName" />
        </n-form-item>
        <n-form-item label="可见性">
          <n-radio-group v-model:value="editForm.visibility">
            <n-radio value="PUBLIC">公开</n-radio>
            <n-radio value="PRIVATE">私有</n-radio>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="editForm.description" type="textarea" :rows="3" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showEditModal = false">取消</n-button>
        <n-button type="primary" @click="handleSaveSpace" :loading="saving">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NSpace, NTag, useMessage, type DataTableColumns, type UploadFileInfo } from 'naive-ui'
import { ArchiveOutline } from '@vicons/ionicons5'
import { spaceApi, documentApi, parseApi } from '@/api'
import type { DocumentVO, SpaceVO } from '@/types/api'

type Visibility = 'PUBLIC' | 'PRIVATE'
type DocumentStatusKey = 'DRAFT' | 'PARSING' | 'PUBLISHED' | 'FAILED'
type StatusTagType = 'default' | 'info' | 'success' | 'warning' | 'error'

interface SpaceEditForm {
  spaceName: string
  visibility: Visibility
  description: string
}

interface SpaceDetail extends SpaceVO {
  visibility: Visibility
}

interface TablePagination {
  page: number
  pageSize: number
  itemCount: number
  showSizePicker: boolean
  pageSizes: number[]
  onChange: (page: number) => void
  onUpdatePageSize: (pageSize: number) => void
}

const router = useRouter()
const route = useRoute()
const message = useMessage()
const spaceId = route.params.id as string

const loading = ref(false)
const uploading = ref(false)
const saving = ref(false)
const showUploadModal = ref(false)
const showEditModal = ref(false)
const searchKeyword = ref('')
const filterStatus = ref<string | null>(null)
const fileList = ref<UploadFileInfo[]>([])
const documents = ref<DocumentVO[]>([])

const spaceInfo = ref<SpaceDetail>({
  spaceId,
  spaceName: '',
  spaceCode: '',
  description: '',
  securityLevel: 1,
  tenantId: '',
  visibility: 'PRIVATE',
  documentCount: 0,
  storageSize: 0,
  createdAt: '',
  updatedAt: '',
})

const pagination = reactive<TablePagination>({
  page: 1,
  pageSize: 15,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [15, 30, 50],
  onChange: (page: number) => {
    pagination.page = page
    void loadDocuments()
  },
  onUpdatePageSize: (pageSize: number) => {
    pagination.pageSize = pageSize
    pagination.page = 1
    void loadDocuments()
  },
})

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '解析中', value: 'PARSING' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '失败', value: 'FAILED' },
]

const securityLevelOptions = [
  { label: '公开(1)', value: 1 },
  { label: '内部(2)', value: 2 },
  { label: '秘密(3)', value: 3 },
  { label: '机密(4)', value: 4 },
]

const uploadForm = reactive({ securityLevel: 1 })
const editForm = reactive<SpaceEditForm>({ spaceName: '', visibility: 'PRIVATE', description: '' })

const statusMap: Record<DocumentStatusKey, { type: StatusTagType; text: string }> = {
  DRAFT: { type: 'default', text: '草稿' },
  PARSING: { type: 'info', text: '解析中' },
  PUBLISHED: { type: 'success', text: '已发布' },
  FAILED: { type: 'error', text: '失败' },
}

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}

function normalizeDocumentStatus(status?: DocumentVO['status']): DocumentStatusKey | string {
  if (status === 0 || status === '0') return 'DRAFT'
  if (status === 1 || status === '1') return 'PUBLISHED'
  if (status === 2 || status === '2') return 'FAILED'
  if (typeof status === 'string') return status
  return 'DRAFT'
}

const documentColumns: DataTableColumns<DocumentVO> = [
  { title: '文档名称', key: 'documentName', ellipsis: { tooltip: true }, minWidth: 200 },
  { title: '类型', key: 'documentType', width: 80 },
  { title: '大小', key: 'fileSize', width: 100, render: (row: DocumentVO) => formatSize(row.fileSize || 0) },
  { title: '密级', key: 'securityLevel', width: 70, render: (row: DocumentVO) => h(NTag, { size: 'small' }, () => `L${row.securityLevel ?? 1}`) },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row: DocumentVO) => {
      const normalizedStatus = normalizeDocumentStatus(row.status)
      const mappedStatus = statusMap[normalizedStatus as DocumentStatusKey]
      return h(
        NTag,
        { size: 'small', type: mappedStatus?.type || 'default' },
        () => mappedStatus?.text || String(normalizedStatus),
      )
    },
  },
  { title: '创建时间', key: 'createdAt', width: 170 },
  {
    title: '操作', key: 'actions', width: 180, fixed: 'right',
    render: (row: DocumentVO) =>
      h(NSpace, null, () => [
        h(NButton, { size: 'small', onClick: () => router.push(`/knowledge/document/${row.documentId}`) }, () => '详情'),
        normalizeDocumentStatus(row.status) === 'DRAFT' &&
          h(NButton, { size: 'small', type: 'primary', onClick: () => handleParse(row) }, () => '解析'),
        h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row) }, () => '删除'),
      ]),
  },
]

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

async function loadSpaceInfo() {
  try {
    const res = await spaceApi.get(spaceId)
    spaceInfo.value = {
      ...spaceInfo.value,
      ...res.data,
      visibility: (res.data?.visibility as Visibility) || 'PRIVATE',
    }
    Object.assign(editForm, {
      spaceName: res.data?.spaceName || '',
      visibility: (res.data?.visibility as Visibility) || 'PRIVATE',
      description: res.data?.description || '',
    })
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '加载失败'))
  }
}

async function loadDocuments() {
  loading.value = true
  try {
    const res = await documentApi.list({
      page: pagination.page,
      pageSize: pagination.pageSize,
      spaceId,
      keyword: searchKeyword.value,
      status: filterStatus.value,
    })
    documents.value = res.data?.records || []
    pagination.itemCount = res.data?.total || 0
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '加载失败'))
  } finally {
    loading.value = false
  }
}

function handleUploadChange(options: { fileList: UploadFileInfo[] }) {
  fileList.value = options.fileList
}

async function handleUpload() {
  if (!fileList.value.length) return
  uploading.value = true
  try {
    for (const file of fileList.value) {
      if (file.file) {
        await parseApi.upload(file.file, spaceId, uploadForm.securityLevel)
      }
    }
    message.success('上传成功，文档正在解析中...')
    showUploadModal.value = false
    fileList.value = []
    void loadDocuments()
    void loadSpaceInfo()
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '上传失败'))
  } finally {
    uploading.value = false
  }
}

function handleEditSpace() {
  showEditModal.value = true
}

async function handleSaveSpace() {
  saving.value = true
  try {
    await spaceApi.update({ ...editForm, spaceId })
    message.success('保存成功')
    showEditModal.value = false
    void loadSpaceInfo()
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function handleParse(row: DocumentVO) {
  try {
    await parseApi.submit({ documentId: row.documentId, spaceId })
    message.success('已提交解析任务')
    void loadDocuments()
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '提交失败'))
  }
}

async function handleDelete(row: DocumentVO) {
  if (row.id === undefined) {
    message.error('缺少文档主键，无法删除')
    return
  }

  try {
    await documentApi.delete(row.id)
    message.success('删除成功')
    void loadDocuments()
    void loadSpaceInfo()
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '删除失败'))
  }
}

watch([searchKeyword, filterStatus], () => {
  pagination.page = 1
  void loadDocuments()
})

onMounted(() => {
  void loadSpaceInfo()
  void loadDocuments()
})
</script>

<style scoped>
.space-detail { padding: 16px; }
.info-card { margin: 16px 0; }
.document-card { margin-top: 16px; }
</style>
