<template>
  <n-card title="用户管理" class="user-page">
    <!-- 搜索栏 -->
    <n-space justify="space-between" align="center" class="toolbar">
      <n-space>
        <n-input
          v-model:value="searchForm.keyword"
          placeholder="搜索用户名/姓名/邮箱"
          clearable
          style="width: 220px"
        />
        <n-select
          v-model:value="searchForm.deptId"
          :options="deptOptions"
          placeholder="选择部门"
          clearable
          style="width: 160px"
        />
        <n-select
          v-model:value="searchForm.status"
          :options="statusOptions"
          placeholder="状态"
          clearable
          style="width: 120px"
        />
        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button @click="handleReset">重置</n-button>
      </n-space>
      <n-button type="primary" @click="handleAdd">
        <template #icon>
          <n-icon><AddOutline /></n-icon>
        </template>
        新增用户
      </n-button>
    </n-space>

    <!-- 用户列表 -->
    <n-data-table
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
      row-key="id"
    />

    <!-- 新增/编辑弹窗 -->
    <n-modal
      v-model:show="modalVisible"
      :title="modalTitle"
      preset="card"
      style="width: 550px"
      :mask-closable="false"
    >
      <n-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-placement="left"
        label-width="80"
      >
        <n-form-item label="用户名" path="username">
          <n-input v-model:value="formData.username" placeholder="输入用户名" :disabled="isEdit" />
        </n-form-item>
        <n-form-item label="密码" path="password" v-if="!isEdit">
          <n-input
            v-model:value="formData.password"
            type="password"
            placeholder="输入密码"
          />
        </n-form-item>
        <n-form-item label="真实姓名" path="realName">
          <n-input v-model:value="formData.realName" placeholder="输入真实姓名" />
        </n-form-item>
        <n-form-item label="邮箱" path="email">
          <n-input v-model:value="formData.email" placeholder="输入邮箱" />
        </n-form-item>
        <n-form-item label="手机号" path="phone">
          <n-input v-model:value="formData.phone" placeholder="输入手机号" />
        </n-form-item>
        <n-form-item label="部门" path="deptId">
          <n-tree-select
            v-model:value="formData.deptId"
            :options="deptTreeOptions"
            placeholder="选择部门"
          />
        </n-form-item>
        <n-form-item label="角色" path="roleIds">
          <n-select
            v-model:value="formData.roleIds"
            :options="roleOptions"
            multiple
            placeholder="选择角色"
          />
        </n-form-item>
        <n-form-item label="安全等级" path="securityLevel">
          <n-radio-group v-model:value="formData.securityLevel">
            <n-radio :value="1">公开</n-radio>
            <n-radio :value="2">内部</n-radio>
            <n-radio :value="3">敏感</n-radio>
            <n-radio :value="4">机密</n-radio>
          </n-radio-group>
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

    <!-- 分配角色弹窗 -->
    <n-modal
      v-model:show="roleModalVisible"
      title="分配角色"
      preset="card"
      style="width: 400px"
    >
      <n-checkbox-group v-model:value="selectedRoles">
        <n-space vertical>
          <n-checkbox v-for="role in roleOptions" :key="role.value" :value="role.value">
            {{ role.label }}
          </n-checkbox>
        </n-space>
      </n-checkbox-group>
      <template #footer>
        <n-space justify="end">
          <n-button @click="roleModalVisible = false">取消</n-button>
          <n-button type="primary" @click="handleAssignRoles">确定</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { AddOutline, KeyOutline, PeopleOutline } from '@vicons/ionicons5'
import { userApi, roleApi, deptApi, rolePermissionApi } from '@/api'

const message = useMessage()
const dialog = useDialog()

// 搜索表单
const searchForm = reactive({
  keyword: '',
  deptId: null as string | null,
  status: null as number | null,
})

// 部门选项
const deptOptions = ref<{ label: string; value: string }[]>([])
const deptTreeOptions = ref<any[]>([])

// 角色选项
const roleOptions = ref<{ label: string; value: string }[]>([])

// 状态选项
const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

// 表格数据
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [20, 50, 100],
})

// 弹窗相关
const modalVisible = ref(false)
const modalTitle = ref('新增用户')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<any>(null)
const currentUserId = ref('')

const formData = reactive({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  deptId: null as string | null,
  roleIds: [] as string[],
  securityLevel: 1,
  status: 1,
})

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
}

// 角色分配弹窗
const roleModalVisible = ref(false)
const selectedRoles = ref<string[]>([])

// 安全等级标签
function getSecurityLevelLabel(level: number): string {
  const map: Record<number, string> = { 1: '公开', 2: '内部', 3: '敏感', 4: '机密' }
  return map[level] || '未知'
}

function getSecurityLevelType(level: number): string {
  const map: Record<number, string> = { 1: 'success', 2: 'info', 3: 'warning', 4: 'error' }
  return map[level] || 'default'
}

// 表格列定义
const columns = [
  { title: '用户名', key: 'username', width: 120 },
  { title: '真实姓名', key: 'realName', width: 100 },
  { title: '部门', key: 'deptName', width: 120 },
  { title: '邮箱', key: 'email', width: 180, ellipsis: { tooltip: true } },
  { title: '手机号', key: 'phone', width: 130 },
  {
    title: '安全等级',
    key: 'securityLevel',
    width: 100,
    render(row: any) {
      return h(
        'n-tag',
        { type: getSecurityLevelType(row.securityLevel), size: 'small' },
        { default: () => getSecurityLevelLabel(row.securityLevel) }
      )
    },
  },
  {
    title: '状态',
    key: 'status',
    width: 80,
    align: 'center',
    render(row: any) {
      return h(
        'n-switch',
        {
          value: row.status === 1,
          size: 'small',
          onUpdateValue: (val: boolean) => handleStatusChange(row, val),
        },
        {
          checked: () => '启用',
          unchecked: () => '禁用',
        }
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
            { size: 'small', onClick: () => handleEdit(row) },
            { default: () => '编辑' }
          ),
          h(
            'n-button',
            { size: 'small', onClick: () => handleAssignRole(row) },
            { default: () => '分配角色', icon: () => h(PeopleOutline) }
          ),
          h(
            'n-popconfirm',
            { onPositiveClick: () => handleDelete(row) },
            {
              trigger: () =>
                h('n-button', { size: 'small', type: 'error' }, { default: () => '删除' }),
              default: () => '确定删除该用户吗？',
            }
          ),
        ],
      })
    },
  },
]

// 加载部门数据
async function loadDepts() {
  try {
    const res: any = await deptApi.tree()
    if (res.code === 200) {
      deptTreeOptions.value = res.data || []
      // 扁平化选项
      const flatten = (nodes: any[]): any[] => {
        return nodes.reduce((acc, node) => {
          acc.push({ label: node.deptName, value: node.deptId })
          if (node.children) {
            acc.push(...flatten(node.children))
          }
          return acc
        }, [])
      }
      deptOptions.value = flatten(res.data || [])
    }
  } catch (error) {
    console.error('加载部门失败', error)
  }
}

// 加载角色数据
async function loadRoles() {
  try {
    const res: any = await roleApi.list()
    if (res.code === 200) {
      roleOptions.value = (res.data || []).map((r: any) => ({
        label: r.roleName,
        value: r.roleId,
      }))
    }
  } catch (error) {
    console.error('加载角色失败', error)
  }
}

// 加载用户数据
async function loadData() {
  loading.value = true
  try {
    const params: any = {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      deptId: searchForm.deptId || undefined,
      status: searchForm.status ?? undefined,
    }
    const res: any = await userApi.list(params)
    if (res.code === 200) {
      tableData.value = res.data.records || []
      pagination.itemCount = res.data.total || 0
    }
  } catch (error) {
    message.error('加载用户列表失败')
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
  searchForm.deptId = null
  searchForm.status = null
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

// 新增
function handleAdd() {
  isEdit.value = false
  modalTitle.value = '新增用户'
  resetForm()
  modalVisible.value = true
}

// 编辑
function handleEdit(row: any) {
  isEdit.value = true
  modalTitle.value = '编辑用户'
  currentUserId.value = row.userId
  Object.assign(formData, {
    username: row.username,
    realName: row.realName,
    email: row.email,
    phone: row.phone,
    deptId: row.deptId,
    roleIds: row.roleIds || [],
    securityLevel: row.securityLevel,
    status: row.status,
  })
  modalVisible.value = true
}

// 状态变更
async function handleStatusChange(row: any, enabled: boolean) {
  try {
    await userApi.update({ ...row, status: enabled ? 1 : 0 })
    message.success('状态更新成功')
    loadData()
  } catch (error) {
    message.error('状态更新失败')
  }
}

// 删除
async function handleDelete(row: any) {
  try {
    const res: any = await userApi.delete(row.id)
    if (res.code === 200) {
      message.success('删除成功')
      loadData()
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// 分配角色
async function handleAssignRole(row: any) {
  currentUserId.value = row.userId
  // 加载用户已有角色
  try {
    const res: any = await rolePermissionApi.getUserRoles(row.userId)
    if (res.code === 200) {
      selectedRoles.value = res.data || []
    }
  } catch (error) {
    selectedRoles.value = row.roleIds || []
  }
  roleModalVisible.value = true
}

// 提交角色分配
async function handleAssignRoles() {
  try {
    const res: any = await rolePermissionApi.saveUserRoles(currentUserId.value, selectedRoles.value)
    if (res.code === 200) {
      message.success('角色分配成功')
      roleModalVisible.value = false
      loadData()
    }
  } catch (error) {
    message.error('角色分配失败')
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
        res = await userApi.update({ ...formData, userId: currentUserId.value })
      } else {
        res = await userApi.create(formData)
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
  formData.username = ''
  formData.password = ''
  formData.realName = ''
  formData.email = ''
  formData.phone = ''
  formData.deptId = null
  formData.roleIds = []
  formData.securityLevel = 1
  formData.status = 1
}

onMounted(() => {
  loadDepts()
  loadRoles()
  loadData()
})
</script>

<style scoped>
.user-page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
</style>