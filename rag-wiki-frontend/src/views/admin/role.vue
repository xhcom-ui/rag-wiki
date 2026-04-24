<template>
  <n-card title="角色管理" class="role-page">
    <!-- 搜索栏 -->
    <n-space justify="space-between" align="center" class="toolbar">
      <n-space>
        <n-input
          v-model:value="searchForm.keyword"
          placeholder="搜索角色名称/编码"
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
        新增角色
      </n-button>
    </n-space>

    <!-- 角色列表 -->
    <n-data-table
      :columns="columns"
      :data="tableData"
      :loading="loading"
      row-key="roleId"
    />

    <!-- 新增/编辑弹窗 -->
    <n-modal
      v-model:show="modalVisible"
      :title="modalTitle"
      preset="card"
      style="width: 500px"
      :mask-closable="false"
    >
      <n-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-placement="left"
        label-width="80"
      >
        <n-form-item label="角色名称" path="roleName">
          <n-input v-model:value="formData.roleName" placeholder="输入角色名称" />
        </n-form-item>
        <n-form-item label="角色编码" path="roleCode">
          <n-input v-model:value="formData.roleCode" placeholder="如：admin, user" :disabled="isEdit" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input
            v-model:value="formData.description"
            type="textarea"
            :rows="3"
            placeholder="角色描述"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="modalVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 权限配置弹窗 -->
    <n-modal
      v-model:show="permissionModalVisible"
      title="配置权限"
      preset="card"
      style="width: 500px; max-height: 600px"
    >
      <n-tree
        :data="menuTreeData"
        checkable
        cascade
        :checked-keys="selectedPermissions"
        @update:checked-keys="handlePermissionChange"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="permissionModalVisible = false">取消</n-button>
          <n-button type="primary" @click="handleSavePermissions">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { NButton, NPopconfirm, NSpace, useMessage, type DataTableColumns, type FormInst } from 'naive-ui'
import { AddOutline, ShieldCheckmarkOutline, TrashOutline } from '@vicons/ionicons5'
import { roleApi, rolePermissionApi } from '@/api'

interface RoleRow {
  id?: number
  roleId: string
  roleName: string
  roleCode: string
  description?: string
  createdAt?: string
  permissions?: string[]
}

interface RoleForm {
  roleName: string
  roleCode: string
  description: string
}

interface PermissionNode {
  key: string
  label: string
  children?: PermissionNode[]
}

const message = useMessage()

const searchForm = reactive({
  keyword: '',
})

const tableData = ref<RoleRow[]>([])
const loading = ref(false)

const modalVisible = ref(false)
const modalTitle = ref('新增角色')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInst | null>(null)
const currentRoleId = ref('')

const formData = reactive<RoleForm>({
  roleName: '',
  roleCode: '',
  description: '',
})

const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}

const permissionModalVisible = ref(false)
const menuTreeData = ref<PermissionNode[]>([])
const selectedPermissions = ref<string[]>([])

const columns: DataTableColumns<RoleRow> = [
  { title: '角色名称', key: 'roleName', width: 150 },
  { title: '角色编码', key: 'roleCode', width: 150 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '创建时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render: (row) =>
      h(NSpace, null, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => handleEdit(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', type: 'info', onClick: () => handleConfigPermission(row) }, { default: () => '配置权限', icon: () => h(ShieldCheckmarkOutline) }),
          h(
            NPopconfirm,
            { onPositiveClick: () => handleDelete(row) },
            {
              trigger: () =>
                h(NButton, { size: 'small', type: 'error' }, {
                  default: () => '删除',
                  icon: () => h(TrashOutline),
                }),
              default: () => '确定删除该角色吗？',
            }
          ),
        ],
      }),
  },
]

async function loadData() {
  loading.value = true
  try {
    const res = await roleApi.list()
    if (res.code === 200) {
      tableData.value = Array.isArray(res.data) ? (res.data as RoleRow[]) : []
    }
  } catch {
    message.error('加载角色列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  if (!searchForm.keyword) {
    loadData()
    return
  }

  tableData.value = tableData.value.filter(
    (item) =>
      item.roleName.includes(searchForm.keyword) ||
      item.roleCode.includes(searchForm.keyword)
  )
}

function handleReset() {
  searchForm.keyword = ''
  loadData()
}

function handleAdd() {
  isEdit.value = false
  modalTitle.value = '新增角色'
  resetForm()
  modalVisible.value = true
}

function handleEdit(row: RoleRow) {
  isEdit.value = true
  modalTitle.value = '编辑角色'
  currentRoleId.value = row.roleId
  Object.assign(formData, {
    roleName: row.roleName,
    roleCode: row.roleCode,
    description: row.description || '',
  })
  modalVisible.value = true
}

async function handleDelete(row: RoleRow) {
  try {
    const deleteId = row.id ?? Number(row.roleId)
    if (!Number.isFinite(deleteId)) {
      message.error('缺少角色主键，无法删除')
      return
    }
    const res = await roleApi.delete(deleteId)
    if (res.code === 200) {
      message.success('删除成功')
      loadData()
    }
  } catch {
    message.error('删除失败')
  }
}

async function handleConfigPermission(row: RoleRow) {
  currentRoleId.value = row.roleId
  menuTreeData.value = [
    { key: 'dashboard', label: '仪表盘', children: [] },
    {
      key: 'knowledge',
      label: '知识库管理',
      children: [
        { key: 'knowledge:view', label: '查看' },
        { key: 'knowledge:create', label: '创建' },
        { key: 'knowledge:edit', label: '编辑' },
        { key: 'knowledge:delete', label: '删除' },
      ],
    },
    {
      key: 'ai',
      label: 'AI功能',
      children: [
        { key: 'ai:chat', label: '智能问答' },
        { key: 'ai:agent', label: '深度研究' },
        { key: 'ai:sandbox', label: '代码沙箱' },
      ],
    },
    {
      key: 'admin',
      label: '系统管理',
      children: [
        { key: 'admin:user', label: '用户管理' },
        { key: 'admin:role', label: '角色管理' },
        { key: 'admin:dept', label: '部门管理' },
        { key: 'admin:audit', label: '审计日志' },
        { key: 'admin:model', label: '模型配置' },
      ],
    },
  ]

  try {
    const res = await rolePermissionApi.getRolePermissions(row.roleId)
    selectedPermissions.value = Array.isArray(res.data) ? (res.data as string[]) : []
  } catch {
    selectedPermissions.value = row.permissions || []
  }

  permissionModalVisible.value = true
}

function handlePermissionChange(keys: string[]) {
  selectedPermissions.value = keys
}

async function handleSavePermissions() {
  try {
    const res = await rolePermissionApi.saveRolePermissions(currentRoleId.value, selectedPermissions.value)
    if (res.code === 200) {
      message.success('权限配置保存成功')
      permissionModalVisible.value = false
      loadData()
    }
  } catch {
    message.error('保存失败')
  }
}

async function handleSubmit() {
  await formRef.value?.validate()

  submitLoading.value = true
  try {
    const payload = {
      ...formData,
      status: 1,
      roleId: currentRoleId.value || undefined,
    }
    const res = isEdit.value ? await roleApi.update(payload) : await roleApi.create(payload)
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
  formData.roleName = ''
  formData.roleCode = ''
  formData.description = ''
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.role-page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
</style>
