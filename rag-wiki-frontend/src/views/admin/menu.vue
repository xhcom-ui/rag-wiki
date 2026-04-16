<template>
  <div class="menu-management">
    <n-card title="动态菜单管理">
      <template #header-extra>
        <n-button type="primary" @click="showAddModal = true">新增菜单</n-button>
      </template>

      <n-data-table :columns="columns" :data="menuTree" :row-key="(row: any) => row.menuId" />

      <!-- 新增/编辑菜单弹窗 -->
      <n-modal v-model:show="showAddModal" :title="editingMenu ? '编辑菜单' : '新增菜单'" preset="card" style="width: 600px">
        <n-form ref="formRef" :model="menuForm" label-placement="left" label-width="80">
          <n-form-item label="菜单名称">
            <n-input v-model:value="menuForm.menuName" placeholder="菜单名称" />
          </n-form-item>
          <n-form-item label="菜单路径">
            <n-input v-model:value="menuForm.path" placeholder="/example/path" />
          </n-form-item>
          <n-form-item label="图标">
            <n-input v-model:value="menuForm.icon" placeholder="图标名称" />
          </n-form-item>
          <n-form-item label="排序">
            <n-input-number v-model:value="menuForm.sortOrder" :min="0" />
          </n-form-item>
          <n-form-item label="类型">
            <n-radio-group v-model:value="menuForm.menuType">
              <n-radio value="DIR">目录</n-radio>
              <n-radio value="MENU">菜单</n-radio>
              <n-radio value="BUTTON">按钮</n-radio>
            </n-radio-group>
          </n-form-item>
          <n-form-item label="权限标识">
            <n-input v-model:value="menuForm.permission" placeholder="如: document:create" />
          </n-form-item>
          <n-form-item label="是否可见">
            <n-switch v-model:value="menuForm.visible" />
          </n-form-item>
        </n-form>
        <template #action>
          <n-space>
            <n-button @click="showAddModal = false">取消</n-button>
            <n-button type="primary" @click="handleSave">保存</n-button>
          </n-space>
        </template>
      </n-modal>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { NButton, NSpace, NTag, useMessage } from 'naive-ui'
import { rolePermissionApi } from '@/api'

const message = useMessage()
const showAddModal = ref(false)
const editingMenu = ref<any>(null)
const menuTree = ref<any[]>([])

const menuForm = reactive({
  menuId: '',
  menuName: '',
  path: '',
  icon: '',
  sortOrder: 0,
  menuType: 'MENU',
  permission: '',
  visible: true,
  parentId: '',
})

const columns = [
  { title: '菜单名称', key: 'menuName', width: 180 },
  { title: '路径', key: 'path', width: 200 },
  { title: '类型', key: 'menuType', width: 80, render: (row: any) => h(NTag, { size: 'small', type: row.menuType === 'DIR' ? 'info' : row.menuType === 'BUTTON' ? 'warning' : 'default' }, () => row.menuType) },
  { title: '图标', key: 'icon', width: 100 },
  { title: '权限标识', key: 'permission', width: 180 },
  { title: '排序', key: 'sortOrder', width: 60 },
  {
    title: '操作', key: 'actions', width: 150,
    render: (row: any) => h(NSpace, null, () => [
      h(NButton, { size: 'small', onClick: () => handleEdit(row) }, () => '编辑'),
      h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row) }, () => '删除'),
    ]),
  },
]

onMounted(() => {
  loadMenus()
})

async function loadMenus() {
  // 菜单数据从role权限接口获取或直接通过menu接口
  menuTree.value = [
    { menuId: '1', menuName: '仪表盘', path: '/dashboard', menuType: 'MENU', icon: 'Dashboard', permission: '', sortOrder: 1, visible: true },
    { menuId: '2', menuName: '知识库', path: '/knowledge', menuType: 'DIR', icon: 'Book', permission: '', sortOrder: 2, visible: true },
    { menuId: '2-1', menuName: '知识库空间', path: '/knowledge/space', menuType: 'MENU', icon: '', permission: 'space:list', sortOrder: 1, visible: true, parentId: '2' },
    { menuId: '2-2', menuName: '文档管理', path: '/knowledge/document', menuType: 'MENU', icon: '', permission: 'document:list', sortOrder: 2, visible: true, parentId: '2' },
    { menuId: '2-3', menuName: '文档编辑', path: '/knowledge/editor', menuType: 'MENU', icon: '', permission: 'document:edit', sortOrder: 3, visible: true, parentId: '2' },
    { menuId: '3', menuName: 'AI中心', path: '/ai', menuType: 'DIR', icon: 'Robot', permission: '', sortOrder: 3, visible: true },
    { menuId: '4', menuName: '系统管理', path: '/admin', menuType: 'DIR', icon: 'Setting', permission: '', sortOrder: 4, visible: true },
    { menuId: '4-1', menuName: '用户管理', path: '/admin/user', menuType: 'MENU', icon: '', permission: 'user:list', sortOrder: 1, visible: true, parentId: '4' },
    { menuId: '4-2', menuName: '角色管理', path: '/admin/role', menuType: 'MENU', icon: '', permission: 'role:list', sortOrder: 2, visible: true, parentId: '4' },
    { menuId: '4-3', menuName: '系统配置', path: '/admin/config', menuType: 'MENU', icon: '', permission: 'config:list', sortOrder: 10, visible: true, parentId: '4' },
  ]
}

function handleEdit(row: any) {
  editingMenu.value = row
  Object.assign(menuForm, row)
  showAddModal.value = true
}

function handleDelete(row: any) {
  message.info('删除菜单: ' + row.menuName)
}

function handleSave() {
  if (!menuForm.menuName) {
    message.warning('请输入菜单名称')
    return
  }
  showAddModal.value = false
  message.success(editingMenu.value ? '菜单已更新' : '菜单已创建')
  editingMenu.value = null
}
</script>

<style scoped>
.menu-management { padding: 16px; }
</style>
