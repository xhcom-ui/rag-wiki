<template>
  <n-card title="部门管理" class="dept-page">
    <!-- 操作栏 -->
    <n-space justify="space-between" align="center" class="toolbar">
      <n-space>
        <n-input
          v-model:value="searchForm.keyword"
          placeholder="搜索部门名称"
          clearable
          style="width: 220px"
        />
        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button @click="handleReset">重置</n-button>
      </n-space>
      <n-button type="primary" @click="handleAdd">
        <template #icon>
          <n-icon><AddOutline /></n-icon>
        </template>
        新增部门
      </n-button>
    </n-space>

    <!-- 部门树形表格 -->
    <n-data-table
      :columns="columns"
      :data="tableData"
      :loading="loading"
      row-key="deptId"
      default-expand-all
    />

    <!-- 新增/编辑弹窗 -->
    <n-modal
      v-model:show="modalVisible"
      :title="modalTitle"
      preset="card"
      style="width: 450px"
      :mask-closable="false"
    >
      <n-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-placement="left"
        label-width="80"
      >
        <n-form-item label="上级部门">
          <n-tree-select
            v-model:value="formData.parentId"
            :options="deptTreeOptions"
            placeholder="选择上级部门（不选则为根部门）"
            clearable
          />
        </n-form-item>
        <n-form-item label="部门名称" path="deptName">
          <n-input v-model:value="formData.deptName" placeholder="输入部门名称" />
        </n-form-item>
        <n-form-item label="部门编码" path="deptCode">
          <n-input v-model:value="formData.deptCode" placeholder="输入部门编码" />
        </n-form-item>
        <n-form-item label="排序">
          <n-input-number v-model:value="formData.sort" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态">
          <n-switch v-model:value="formData.status" :checked-value="1" :unchecked-value="0">
            <template #checked>启用</template>
            <template #unchecked>禁用</template>
          </n-switch>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="modalVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { NButton, NPopconfirm, NSpace, NTag, useMessage, type DataTableColumns, type FormInst, type TreeSelectOption } from 'naive-ui'
import { AddOutline, AddCircleOutline, TrashOutline } from '@vicons/ionicons5'
import { deptApi } from '@/api'
import type { DeptVO } from '@/types/api'

interface DeptRow {
  id?: number
  deptId: string
  deptName: string
  deptCode?: string
  parentId?: string | null
  sort?: number
  status: number
  createdAt?: string
  children?: DeptRow[]
}

interface DeptForm {
  parentId: string | null
  deptName: string
  deptCode: string
  sort: number
  status: number
}

const message = useMessage()

const searchForm = reactive({
  keyword: '',
})

const tableData = ref<DeptRow[]>([])
const loading = ref(false)

const modalVisible = ref(false)
const modalTitle = ref('新增部门')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInst | null>(null)
const currentDeptId = ref('')

const formData = reactive<DeptForm>({
  parentId: null,
  deptName: '',
  deptCode: '',
  sort: 0,
  status: 1,
})

const formRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
}

const deptTreeOptions = ref<TreeSelectOption[]>([])

const columns: DataTableColumns<DeptRow> = [
  { title: '部门名称', key: 'deptName', width: 200 },
  { title: '部门编码', key: 'deptCode', width: 150 },
  { title: '排序', key: 'sort', width: 80, align: 'center' },
  {
    title: '状态',
    key: 'status',
    width: 100,
    align: 'center',
    render: (row) =>
      h(NTag, { type: row.status === 1 ? 'success' : 'default', size: 'small' }, { default: () => (row.status === 1 ? '启用' : '禁用') }),
  },
  { title: '创建时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render: (row) =>
      h(NSpace, null, {
        default: () => [
          h(NButton, { size: 'small', type: 'primary', onClick: () => handleAddSub(row) }, { default: () => '添加下级', icon: () => h(AddCircleOutline) }),
          h(NButton, { size: 'small', onClick: () => handleEdit(row) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => handleDelete(row) },
            {
              trigger: () =>
                h(NButton, { size: 'small', type: 'error' }, {
                  default: () => '删除',
                  icon: () => h(TrashOutline),
                }),
              default: () => '确定删除该部门吗？',
            }
          ),
        ],
      }),
  },
]

async function loadData() {
  loading.value = true
  try {
    const res = await deptApi.tree()
    if (res.code === 200) {
      const items = Array.isArray(res.data) ? normalizeDeptRows(res.data as DeptVO[]) : []
      tableData.value = items
      deptTreeOptions.value = convertToTreeSelect(items)
    }
  } catch {
    message.error('加载部门列表失败')
  } finally {
    loading.value = false
  }
}

function convertToTreeSelect(nodes: DeptRow[]): TreeSelectOption[] {
  return nodes.map((node) => ({
    key: node.deptId,
    label: node.deptName,
    value: node.deptId,
    children: node.children ? convertToTreeSelect(node.children) : undefined,
  }))
}

function filterTree(nodes: DeptRow[], keyword: string): DeptRow[] {
  return nodes
    .filter((node) => {
      const match = node.deptName.includes(keyword)
      const childrenMatch = !!node.children?.length && filterTree(node.children, keyword).length > 0
      return match || childrenMatch
    })
    .map((node) => ({
      ...node,
      children: node.children ? filterTree(node.children, keyword) : undefined,
    }))
}

function handleSearch() {
  if (!searchForm.keyword) {
    loadData()
    return
  }

  deptApi.tree().then((res) => {
    if (res.code === 200) {
      const items = Array.isArray(res.data) ? normalizeDeptRows(res.data as DeptVO[]) : []
      tableData.value = filterTree(items, searchForm.keyword)
    }
  })
}

function handleReset() {
  searchForm.keyword = ''
  loadData()
}

function handleAdd() {
  isEdit.value = false
  modalTitle.value = '新增部门'
  resetForm()
  modalVisible.value = true
}

function handleAddSub(row: DeptRow) {
  isEdit.value = false
  modalTitle.value = '新增下级部门'
  resetForm()
  formData.parentId = row.deptId
  modalVisible.value = true
}

function handleEdit(row: DeptRow) {
  isEdit.value = true
  modalTitle.value = '编辑部门'
  currentDeptId.value = row.deptId
  Object.assign(formData, {
    parentId: row.parentId ?? null,
    deptName: row.deptName,
    deptCode: row.deptCode || '',
    sort: row.sort ?? 0,
    status: row.status,
  })
  modalVisible.value = true
}

async function handleDelete(row: DeptRow) {
  if (row.children && row.children.length > 0) {
    message.error('请先删除下级部门')
    return
  }

  try {
    const deleteId = row.id ?? Number(row.deptId)
    if (!Number.isFinite(deleteId)) {
      message.error('缺少部门主键，无法删除')
      return
    }
    const res = await deptApi.delete(deleteId)
    if (res.code === 200) {
      message.success('删除成功')
      loadData()
    }
  } catch {
    message.error('删除失败')
  }
}

async function handleSubmit() {
  await formRef.value?.validate()

  submitLoading.value = true
  try {
    const payload = {
      ...formData,
      deptId: currentDeptId.value || undefined,
    }
    const res = isEdit.value ? await deptApi.update(payload) : await deptApi.create(formData)
    if (res.code === 200) {
      message.success(isEdit.value ? '更新成功' : '创建成功')
      modalVisible.value = false
      loadData()
    }
  } catch {
    message.error(isEdit.value ? '更新失败' : '创建失败')
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  formData.parentId = null
  formData.deptName = ''
  formData.deptCode = ''
  formData.sort = 0
  formData.status = 1
}

function normalizeDeptRows(rows: DeptVO[]): DeptRow[] {
  return rows.map((row) => ({
    id: undefined,
    deptId: String(row.deptId),
    deptName: row.deptName,
    deptCode: row.deptCode,
    parentId: row.parentId != null ? String(row.parentId) : null,
    sort: row.sort ?? row.sortOrder,
    status: row.status,
    createdAt: row.createdAt,
    children: row.children ? normalizeDeptRows(row.children) : undefined,
  }))
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.dept-page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
</style>
