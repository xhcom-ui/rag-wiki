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
import { useMessage } from 'naive-ui'
import { AddOutline, AddCircleOutline, TrashOutline } from '@vicons/ionicons5'
import { deptApi } from '@/api'

const message = useMessage()

// 搜索表单
const searchForm = reactive({
  keyword: '',
})

// 表格数据
const tableData = ref<any[]>([])
const loading = ref(false)

// 弹窗相关
const modalVisible = ref(false)
const modalTitle = ref('新增部门')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<any>(null)
const currentDeptId = ref('')

const formData = reactive({
  parentId: null as string | null,
  deptName: '',
  deptCode: '',
  sort: 0,
  status: 1,
})

const formRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
}

// 部门树选项
const deptTreeOptions = ref<any[]>([])

// 表格列定义
const columns = [
  { title: '部门名称', key: 'deptName', width: 200 },
  { title: '部门编码', key: 'deptCode', width: 150 },
  { title: '排序', key: 'sort', width: 80, align: 'center' },
  {
    title: '状态',
    key: 'status',
    width: 100,
    align: 'center',
    render(row: any) {
      return h(
        'n-tag',
        { type: row.status === 1 ? 'success' : 'default', size: 'small' },
        { default: () => (row.status === 1 ? '启用' : '禁用') }
      )
    },
  },
  { title: '创建时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render(row: any) {
      return h('n-space', null, {
        default: () => [
          h(
            'n-button',
            { size: 'small', type: 'primary', onClick: () => handleAddSub(row) },
            { default: () => '添加下级', icon: () => h(AddCircleOutline) }
          ),
          h(
            'n-button',
            { size: 'small', onClick: () => handleEdit(row) },
            { default: () => '编辑' }
          ),
          h(
            'n-popconfirm',
            { onPositiveClick: () => handleDelete(row) },
            {
              trigger: () =>
                h('n-button', { size: 'small', type: 'error' }, {
                  default: () => '删除',
                  icon: () => h(TrashOutline),
                }),
              default: () => '确定删除该部门吗？',
            }
          ),
        ],
      })
    },
  },
]

// 加载部门数据
async function loadData() {
  loading.value = true
  try {
    const res: any = await deptApi.tree()
    if (res.code === 200) {
      tableData.value = res.data || []
      deptTreeOptions.value = convertToTreeSelect(res.data || [])
    }
  } catch (error) {
    message.error('加载部门列表失败')
  } finally {
    loading.value = false
  }
}

// 转换为树选择选项
function convertToTreeSelect(nodes: any[]): any[] {
  return nodes.map((node) => ({
    key: node.deptId,
    label: node.deptName,
    value: node.deptId,
    children: node.children ? convertToTreeSelect(node.children) : undefined,
  }))
}

// 搜索
function handleSearch() {
  if (searchForm.keyword) {
    // 递归过滤
    const filterTree = (nodes: any[]): any[] => {
      return nodes
        .filter((node) => {
          const match = node.deptName.includes(searchForm.keyword)
          const childrenMatch = node.children && filterTree(node.children).length > 0
          return match || childrenMatch
        })
        .map((node) => ({
          ...node,
          children: node.children ? filterTree(node.children) : undefined,
        }))
    }
    // 先加载完整数据再过滤
    deptApi.tree().then((res: any) => {
      if (res.code === 200) {
        tableData.value = filterTree(res.data || [])
      }
    })
  } else {
    loadData()
  }
}

// 重置
function handleReset() {
  searchForm.keyword = ''
  loadData()
}

// 新增
function handleAdd() {
  isEdit.value = false
  modalTitle.value = '新增部门'
  resetForm()
  modalVisible.value = true
}

// 添加下级
function handleAddSub(row: any) {
  isEdit.value = false
  modalTitle.value = '新增下级部门'
  resetForm()
  formData.parentId = row.deptId
  modalVisible.value = true
}

// 编辑
function handleEdit(row: any) {
  isEdit.value = true
  modalTitle.value = '编辑部门'
  currentDeptId.value = row.deptId
  Object.assign(formData, {
    parentId: row.parentId,
    deptName: row.deptName,
    deptCode: row.deptCode,
    sort: row.sort,
    status: row.status,
  })
  modalVisible.value = true
}

// 删除
async function handleDelete(row: any) {
  if (row.children && row.children.length > 0) {
    message.error('请先删除下级部门')
    return
  }
  try {
    const res: any = await deptApi.delete(row.id)
    if (res.code === 200) {
      message.success('删除成功')
      loadData()
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// 提交表单
async function handleSubmit() {
  formRef.value?.validate(async (errors: any) => {
    if (errors) return

    submitLoading.value = true
    try {
      let res: any
      if (isEdit.value) {
        res = await deptApi.update({ ...formData, deptId: currentDeptId.value })
      } else {
        res = await deptApi.create(formData)
      }

      if (res.code === 200) {
        message.success(isEdit.value ? '更新成功' : '创建成功')
        modalVisible.value = false
        loadData()
      }
    } catch (error) {
      message.error(isEdit.value ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 重置表单
function resetForm() {
  formData.parentId = null
  formData.deptName = ''
  formData.deptCode = ''
  formData.sort = 0
  formData.status = 1
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