<template>
  <div class="document-editor">
    <n-card :title="isEdit ? '编辑文档' : '创建文档'">
      <template #header-extra>
        <n-space>
          <n-button @click="handleSave" type="primary" :loading="saving">保存</n-button>
          <n-button @click="handleCancel">取消</n-button>
        </n-space>
      </template>

      <n-form ref="formRef" :model="formData" label-placement="left" label-width="80">
        <n-form-item label="文档标题">
          <n-input v-model:value="formData.title" placeholder="请输入文档标题" />
        </n-form-item>
        <n-form-item label="所属空间">
          <n-select v-model:value="formData.spaceId" :options="spaceOptions" placeholder="选择知识库空间" />
        </n-form-item>
        <n-form-item label="文档内容">
          <div id="vditor" ref="vditorRef" style="width: 100%; min-height: 500px;"></div>
        </n-form-item>
      </n-form>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { documentApi, spaceApi } from '@/api'
import type { DocumentVO, SpaceVO } from '@/types/api'

interface VditorInstance {
  getValue: () => string
  destroy: () => void
}

const router = useRouter()
const route = useRoute()
const message = useMessage()

const saving = ref(false)
const isEdit = ref(false)
const vditorRef = ref<HTMLDivElement | null>(null)
let vditorInstance: VditorInstance | null = null

const formData = reactive({
  title: '',
  spaceId: '',
  content: '',
  documentId: '',
})

const spaceOptions = ref<{ label: string; value: string }[]>([])

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}

onMounted(async () => {
  // 加载空间列表
  try {
    const res = await spaceApi.list({})
    const spaces = res.data?.records || []
    spaceOptions.value = spaces.map((space: SpaceVO) => ({
      label: space.spaceName,
      value: space.spaceId,
    }))
  } catch {
    // ignore
  }

  // 如果是编辑模式，加载文档内容
  const docId = route.params.id as string
  if (docId) {
    isEdit.value = true
    formData.documentId = docId
    try {
      const res = await documentApi.get(docId)
      const document = res.data as DocumentVO
      formData.title = document.documentName || ''
      formData.spaceId = document.spaceId || ''
      formData.content = document.content || document.documentContent || ''
    } catch {
      // ignore
    }
  }

  // 初始化Vditor
  initVditor()
})

onBeforeUnmount(() => {
  if (vditorInstance) {
    vditorInstance.destroy()
    vditorInstance = null
  }
})

function initVditor() {
  import('vditor').then((VditorModule) => {
    const Vditor = VditorModule.default
    vditorInstance = new Vditor('vditor', {
      height: 500,
      mode: 'wysiwyg',
      placeholder: '请输入文档内容，支持Markdown语法...',
      theme: 'classic',
      value: formData.content,
      cache: { enable: false },
      toolbar: [
        'headings', 'bold', 'italic', 'strike', '|',
        'line', 'quote', 'list', 'ordered-list', 'check', '|',
        'code', 'inline-code', 'table', 'link', 'upload', '|',
        'undo', 'redo', '|',
        'fullscreen', 'preview',
      ],
    })
  })
}

async function handleSave() {
  if (!formData.title) {
    message.warning('请输入文档标题')
    return
  }
  if (!formData.spaceId) {
    message.warning('请选择知识库空间')
    return
  }

  saving.value = true
  try {
    const content = vditorInstance?.getValue() || formData.content
    const docData = {
      documentName: formData.title,
      spaceId: formData.spaceId,
      documentType: 'wiki',
      content,
    }

    if (isEdit.value) {
      await documentApi.update({ ...docData, documentId: formData.documentId })
      message.success('文档更新成功')
    } else {
      await documentApi.create(docData)
      message.success('文档创建成功')
    }
    router.back()
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.back()
}
</script>

<style scoped>
.document-editor {
  padding: 16px;
}
</style>
