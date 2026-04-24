<template>
  <n-card title="知识库管理" class="knowledge-page">
    <!-- 搜索和操作栏 -->
    <n-space justify="space-between" align="center" class="toolbar">
      <n-space>
        <n-input
          v-model:value="searchForm.keyword"
          placeholder="搜索知识库名称"
          clearable
          style="width: 220px"
        />
        <n-select
          v-model:value="searchForm.securityLevel"
          :options="securityLevelOptions"
          placeholder="安全等级"
          clearable
          style="width: 140px"
        />
        <n-button type="primary" @click="handleSearch">
          <template #icon><n-icon><SearchOutline /></n-icon></template>
          查询
        </n-button>
        <n-button @click="handleReset">重置</n-button>
      </n-space>
      <n-button type="primary" @click="handleCreate">
        <template #icon><n-icon><AddOutline /></n-icon></template>
        创建知识库
      </n-button>
    </n-space>

    <!-- 知识库卡片列表 -->
    <n-grid :cols="4" :x-gap="16" :y-gap="16" class="space-grid">
      <n-gi v-for="space in spaceList" :key="space.spaceId">
        <n-card
          hoverable
          class="space-card"
          @click="handleViewSpace(space)"
        >
          <template #header>
            <n-space align="center">
              <n-icon size="24" :component="BookOutline" />
              <n-text strong class="space-name">{{ space.spaceName }}</n-text>
            </n-space>
          </template>
          <template #header-extra>
            <n-tag :type="getSecurityLevelType(space.securityLevel)" size="small">
              {{ getSecurityLevelLabel(space.securityLevel) }}
            </n-tag>
          </template>

          <n-text depth="3" class="space-desc" :line-clamp="2">
            {{ space.description || '暂无描述' }}
          </n-text>

          <n-divider style="margin: 12px 0" />

          <n-space justify="space-between" align="center">
            <n-space>
              <n-statistic label="文档" :value="space.documentCount || 0" />
              <n-statistic label="向量" :value="space.vectorCount || 0" />
            </n-space>
            <n-dropdown :options="getActionOptions()" @select="(key) => handleAction(key, space)">
              <n-button text @click.stop>
                <template #icon><n-icon><EllipsisHorizontalOutline /></n-icon></template>
              </n-button>
            </n-dropdown>
          </n-space>

          <template #footer>
            <n-space justify="space-between" align="center">
              <n-text depth="3" style="font-size: 12px">
                更新于 {{ formatTime(space.updatedAt || '') }}
              </n-text>
              <n-avatar-group :options="space.members?.slice(0, 3).map((m: any) => ({
                src: m.avatar,
                fallbackSrc: 'https://07akioni.oss-cn-beijing.aliyuncs.com/07akioni.jpeg'
              }))" :size="24" />
            </n-space>
          </template>
        </n-card>
      </n-gi>

      <!-- 创建卡片 -->
      <n-gi>
        <n-card
          hoverable
          class="space-card create-card"
          @click="handleCreate"
        >
          <div class="create-content">
            <n-icon size="48" :component="AddCircleOutline" color="#18a058" />
            <n-text strong style="margin-top: 12px">创建新知识库</n-text>
          </div>
        </n-card>
      </n-gi>
    </n-grid>

    <!-- 分页 -->
    <n-pagination
      v-model:page="pagination.page"
      v-model:page-size="pagination.pageSize"
      :item-count="pagination.total"
      :page-sizes="[12, 24, 48]"
      show-size-picker
      style="margin-top: 24px; justify-content: flex-end"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />

    <!-- 创建/编辑知识库弹窗 -->
    <n-modal
      v-model:show="modalVisible"
      :title="isEdit ? '编辑知识库' : '创建知识库'"
      preset="card"
      style="width: 500px"
      :mask-closable="false"
    >
      <n-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-placement="left"
        label-width="90"
      >
        <n-form-item label="名称" path="spaceName">
          <n-input v-model:value="formData.spaceName" placeholder="输入知识库名称" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input
            v-model:value="formData.description"
            type="textarea"
            :rows="3"
            placeholder="描述知识库的用途和内容..."
          />
        </n-form-item>
        <n-form-item label="安全等级" path="securityLevel">
          <n-radio-group v-model:value="formData.securityLevel">
            <n-radio-button :value="1">公开</n-radio-button>
            <n-radio-button :value="2">内部</n-radio-button>
            <n-radio-button :value="3">敏感</n-radio-button>
            <n-radio-button :value="4">机密</n-radio-button>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="可见范围">
          <n-select
            v-model:value="formData.visibility"
            :options="visibilityOptions"
            placeholder="选择可见范围"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="modalVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitLoading" @click="handleSubmit">
            {{ isEdit ? '保存' : '创建' }}
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage, type DropdownOption, type FormInst } from 'naive-ui'
import {
  BookOutline, AddOutline, AddCircleOutline, SearchOutline,
  EllipsisHorizontalOutline, PencilOutline, TrashOutline, PeopleOutline,
} from '@vicons/ionicons5'
import { spaceApi } from '@/api'
import type { SpaceVO } from '@/types/api'

const router = useRouter()
const message = useMessage()

type SecurityTagType = 'success' | 'info' | 'warning' | 'error' | 'default'

interface SpaceMember {
  avatar?: string
}

interface SpaceRow extends Partial<SpaceVO> {
  id?: number
  spaceId: string
  spaceName: string
  description?: string
  securityLevel: number
  visibility?: string
  documentCount?: number
  vectorCount?: number
  updatedAt?: string
  members?: SpaceMember[]
}

interface SpaceForm {
  spaceName: string
  description: string
  securityLevel: number
  visibility: string
}

// 搜索表单
const searchForm = reactive({
  keyword: '',
  securityLevel: null as number | null,
})

// 安全等级选项
const securityLevelOptions = [
  { label: '公开', value: 1 },
  { label: '内部', value: 2 },
  { label: '敏感', value: 3 },
  { label: '机密', value: 4 },
]

// 可见范围选项
const visibilityOptions = [
  { label: '全员可见', value: 'PUBLIC' },
  { label: '部门可见', value: 'DEPT' },
  { label: '仅成员可见', value: 'PRIVATE' },
]

// 知识库列表
const spaceList = ref<SpaceRow[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 12,
  total: 0,
})

// 弹窗相关
const modalVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInst | null>(null)
const currentSpaceId = ref('')

const formData = reactive<SpaceForm>({
  spaceName: '',
  description: '',
  securityLevel: 1,
  visibility: 'PUBLIC',
})

const formRules = {
  spaceName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  securityLevel: [{ required: true, message: '请选择安全等级', trigger: 'change' }],
}

// 获取安全等级标签
function getSecurityLevelLabel(level: number): string {
  const map: Record<number, string> = { 1: '公开', 2: '内部', 3: '敏感', 4: '机密' }
  return map[level] || '未知'
}

function getSecurityLevelType(level: number): SecurityTagType {
  const map: Record<number, SecurityTagType> = { 1: 'success', 2: 'info', 3: 'warning', 4: 'error' }
  return map[level] || 'default'
}

// 获取操作选项
function getActionOptions(): DropdownOption[] {
  return [
    {
      label: '编辑',
      key: 'edit',
      icon: () => h(PencilOutline),
    },
    {
      label: '成员管理',
      key: 'members',
      icon: () => h(PeopleOutline),
    },
    {
      type: 'divider',
      key: 'divider',
    },
    {
      label: '删除',
      key: 'delete',
      icon: () => h(TrashOutline),
      props: {
        style: { color: '#d03050' },
      },
    },
  ]
}

// 格式化时间
function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / 86400000)

  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  return date.toLocaleDateString()
}

// 加载知识库列表
async function loadData() {
  loading.value = true
  try {
    const params: Record<string, string | number | undefined> = {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      securityLevel: searchForm.securityLevel ?? undefined,
    }
    const res = await spaceApi.list(params)
    if (res.code === 200) {
      spaceList.value = (res.data.records || []).map(normalizeSpace)
      pagination.total = res.data.total || 0
    }
  } catch {
    message.error('加载知识库列表失败')
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
  searchForm.securityLevel = null
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

// 查看知识库
function handleViewSpace(space: SpaceRow) {
  router.push(`/knowledge/${space.spaceId}`)
}

// 创建知识库
function handleCreate() {
  isEdit.value = false
  resetForm()
  modalVisible.value = true
}

// 编辑知识库
function handleEdit(space: SpaceRow) {
  isEdit.value = true
  currentSpaceId.value = space.spaceId
  Object.assign(formData, {
    spaceName: space.spaceName,
    description: space.description,
    securityLevel: space.securityLevel,
    visibility: space.visibility || 'PUBLIC',
  })
  modalVisible.value = true
}

// 删除知识库
async function handleDelete(space: SpaceRow) {
  try {
    const deleteId = space.id ?? Number(space.spaceId)
    if (!Number.isFinite(deleteId)) {
      message.error('缺少知识库主键，无法删除')
      return
    }
    const res = await spaceApi.delete(deleteId)
    if (res.code === 200) {
      message.success('删除成功')
      await loadData()
    }
  } catch {
    message.error('删除失败')
  }
}

// 操作处理
function handleAction(key: string, space: SpaceRow) {
  switch (key) {
    case 'edit':
      handleEdit(space)
      break
    case 'members':
      message.info('成员管理功能开发中...')
      break
    case 'delete':
      handleDelete(space)
      break
  }
}

// 提交表单
async function handleSubmit() {
  await formRef.value?.validate()

  submitLoading.value = true
  try {
    const res = isEdit.value
      ? await spaceApi.update({ ...formData, spaceId: currentSpaceId.value })
      : await spaceApi.create(formData)

    if (res.code === 200) {
      message.success(isEdit.value ? '更新成功' : '创建成功')
      modalVisible.value = false
      await loadData()
    }
  } catch {
    message.error(isEdit.value ? '更新失败' : '创建失败')
  } finally {
    submitLoading.value = false
  }
}

// 重置表单
function resetForm() {
  formData.spaceName = ''
  formData.description = ''
  formData.securityLevel = 1
  formData.visibility = 'PUBLIC'
}

onMounted(() => {
  loadData()
})

function normalizeSpace(space: SpaceVO): SpaceRow {
  return {
    id: undefined,
    spaceId: space.spaceId,
    spaceName: space.spaceName,
    description: space.description,
    securityLevel: space.securityLevel,
    visibility: space.visibility,
    documentCount: Number((space as SpaceVO & { documentCount?: number }).documentCount || 0),
    vectorCount: Number((space as SpaceVO & { vectorCount?: number }).vectorCount || 0),
    updatedAt: space.updatedAt,
    members: space.members?.map((member) => ({ avatar: member.avatar })),
  }
}
</script>

<style scoped>
.knowledge-page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 24px;
}
.space-grid {
  min-height: 400px;
}
.space-card {
  cursor: pointer;
  transition: all 0.3s;
}
.space-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}
.space-name {
  font-size: 16px;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.space-desc {
  height: 44px;
  font-size: 13px;
}
.create-card {
  height: 100%;
  min-height: 220px;
}
.create-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #18a058;
}
</style>
