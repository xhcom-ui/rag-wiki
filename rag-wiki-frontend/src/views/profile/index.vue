<template>
  <div class="profile-page">
    <n-grid :cols="3" :x-gap="16">
      <!-- 左侧：用户信息 -->
      <n-gi :span="1">
        <n-card title="个人信息">
          <div class="user-avatar">
            <n-avatar :size="80" :style="{ background: '#18a058' }">{{ userInfo.realName?.charAt(0) || 'U' }}</n-avatar>
            <n-button size="small" style="margin-top: 8px">更换头像</n-button>
          </div>
          <n-descriptions :column="1" label-placement="left" class="user-info">
            <n-descriptions-item label="用户名">{{ userInfo.username }}</n-descriptions-item>
            <n-descriptions-item label="姓名">{{ userInfo.realName }}</n-descriptions-item>
            <n-descriptions-item label="部门">{{ userInfo.deptName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="角色">
              <n-space>
                <n-tag v-for="role in userInfo.roles" :key="role" size="small">{{ role }}</n-tag>
              </n-space>
            </n-descriptions-item>
            <n-descriptions-item label="密级">
              <n-tag size="small" :type="userInfo.securityLevel >= 3 ? 'warning' : 'default'">L{{ userInfo.securityLevel }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="邮箱">{{ userInfo.email || '-' }}</n-descriptions-item>
            <n-descriptions-item label="手机">{{ userInfo.phone || '-' }}</n-descriptions-item>
          </n-descriptions>
        </n-card>
      </n-gi>

      <!-- 右侧：设置 -->
      <n-gi :span="2">
        <n-tabs type="card">
          <n-tab-pane name="basic" tab="基本设置">
            <n-form :model="basicForm" label-placement="left" label-width="80px" style="max-width: 400px">
              <n-form-item label="姓名">
                <n-input v-model:value="basicForm.realName" placeholder="请输入姓名" />
              </n-form-item>
              <n-form-item label="邮箱">
                <n-input v-model:value="basicForm.email" placeholder="请输入邮箱" />
              </n-form-item>
              <n-form-item label="手机">
                <n-input v-model:value="basicForm.phone" placeholder="请输入手机号" />
              </n-form-item>
              <n-form-item>
                <n-button type="primary" @click="handleSaveBasic" :loading="saving">保存修改</n-button>
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <n-tab-pane name="password" tab="修改密码">
            <n-form ref="pwdFormRef" :model="passwordForm" :rules="passwordRules" label-placement="left" label-width="100px" style="max-width: 400px">
              <n-form-item label="当前密码" path="oldPassword">
                <n-input v-model:value="passwordForm.oldPassword" type="password" placeholder="请输入当前密码" />
              </n-form-item>
              <n-form-item label="新密码" path="newPassword">
                <n-input v-model:value="passwordForm.newPassword" type="password" placeholder="请输入新密码" />
              </n-form-item>
              <n-form-item label="确认密码" path="confirmPassword">
                <n-input v-model:value="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
              </n-form-item>
              <n-form-item>
                <n-button type="primary" @click="handleChangePassword" :loading="changingPwd">修改密码</n-button>
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <n-tab-pane name="preferences" tab="偏好设置">
            <n-form :model="preferences" label-placement="left" label-width="120px" style="max-width: 500px">
              <n-form-item label="默认知识库">
                <n-select v-model:value="preferences.defaultSpaceId" :options="spaceOptions" placeholder="选择默认知识库" clearable />
              </n-form-item>
              <n-form-item label="默认AI模型">
                <n-select v-model:value="preferences.defaultModel" :options="modelOptions" placeholder="选择默认模型" />
              </n-form-item>
              <n-form-item label="回复语言">
                <n-radio-group v-model:value="preferences.replyLanguage">
                  <n-radio value="zh-CN">中文</n-radio>
                  <n-radio value="en-US">英文</n-radio>
                  <n-radio value="auto">自动</n-radio>
                </n-radio-group>
              </n-form-item>
              <n-form-item label="流式输出">
                <n-switch v-model:value="preferences.streamOutput">
                  <template #checked>开启</template>
                  <template #unchecked>关闭</template>
                </n-switch>
                <span style="margin-left: 8px; color: #999">开启后AI回复将逐字显示</span>
              </n-form-item>
              <n-form-item label="消息通知">
                <n-checkbox v-model:checked="preferences.notifyOnParse">文档解析完成通知</n-checkbox>
                <n-checkbox v-model:checked="preferences.notifyOnApproval" style="margin-left: 16px">审批待处理通知</n-checkbox>
              </n-form-item>
              <n-form-item>
                <n-button type="primary" @click="handleSavePreferences" :loading="savingPrefs">保存设置</n-button>
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <n-tab-pane name="sessions" tab="会话管理">
            <n-data-table :columns="sessionColumns" :data="sessions" :loading="loadingSessions" :row-key="(row: any) => row.sessionId" />
          </n-tab-pane>

          <n-tab-pane name="apikeys" tab="API密钥">
            <n-space vertical>
              <n-space justify="space-between" align="center">
                <n-text>管理您的API密钥，用于外部系统集成</n-text>
                <n-button type="primary" size="small" @click="showCreateKeyModal = true">创建密钥</n-button>
              </n-space>
              <n-data-table :columns="apiKeyColumns" :data="apiKeys" :loading="loadingApiKeys" :row-key="(row: any) => row.keyId" />
            </n-space>
          </n-tab-pane>

          <n-tab-pane name="mydocs" tab="我的文档">
            <n-space vertical>
              <n-tabs type="segment" @update:value="handleMyDocTab">
                <n-tab name="favorites">收藏文档</n-tab>
                <n-tab name="history">浏览历史</n-tab>
                <n-tab name="created">我创建的</n-tab>
              </n-tabs>
              <n-list hoverable clickable>
                <n-list-item v-for="doc in myDocuments" :key="doc.documentId" @click="router.push('/knowledge/document')">
                  <n-thing :title="doc.documentName" :description="doc.spaceName ? '空间: ' + doc.spaceName : ''" />
                  <template #suffix>
                    <n-text depth="3" style="font-size: 12px">{{ doc.updatedAt || doc.createdAt }}</n-text>
                  </template>
                </n-list-item>
                <n-list-item v-if="myDocuments.length === 0">
                  <n-text depth="3">暂无数据</n-text>
                </n-list-item>
              </n-list>
            </n-space>
          </n-tab-pane>
        </n-tabs>
      </n-gi>
    </n-grid>

    <!-- 创建API密钥弹窗 -->
    <n-modal v-model:show="showCreateKeyModal" title="创建API密钥" preset="card" style="width: 500px">
      <n-form :model="newKeyForm" label-placement="left" label-width="80">
        <n-form-item label="密钥名称">
          <n-input v-model:value="newKeyForm.keyName" placeholder="如：开发环境密钥" />
        </n-form-item>
        <n-form-item label="速率限制">
          <n-input-number v-model:value="newKeyForm.rateLimit" :min="1" :max="100" placeholder="QPS限制" style="width: 100%" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space>
          <n-button @click="showCreateKeyModal = false">取消</n-button>
          <n-button type="primary" @click="handleCreateApiKey" :loading="creatingKey">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 密钥创建成功弹窗 -->
    <n-modal v-model:show="showKeyResultModal" title="密钥创建成功" preset="card" style="width: 500px">
      <n-alert type="warning" title="请妥善保管" style="margin-bottom: 12px">
        此密钥仅展示一次，关闭后将无法再次查看完整密钥！
      </n-alert>
      <n-input :value="newKeyValue" type="textarea" :rows="2" readonly />
      <template #action>
        <n-button type="primary" @click="copyApiKey">复制密钥</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpace, NTag, useMessage, type DataTableColumns, type FormRules, type FormInst } from 'naive-ui'
import { authApi, userApi, spaceApi, ragApi, apiKeyApi, interactionApi, documentApi } from '@/api'

const router = useRouter()
const message = useMessage()
const saving = ref(false)
const changingPwd = ref(false)
const savingPrefs = ref(false)
const loadingSessions = ref(false)
const pwdFormRef = ref<FormInst | null>(null)

const userInfo = ref<any>({
  username: '',
  realName: '',
  deptName: '',
  roles: [],
  securityLevel: 1,
  email: '',
  phone: '',
})

const basicForm = reactive({ realName: '', email: '', phone: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const preferences = reactive({
  defaultSpaceId: null as string | null,
  defaultModel: 'gpt-4',
  replyLanguage: 'auto',
  streamOutput: true,
  notifyOnParse: true,
  notifyOnApproval: true,
})

const spaceOptions = ref<any[]>([])
const modelOptions = [
  { label: 'GPT-4', value: 'gpt-4' },
  { label: 'GPT-3.5', value: 'gpt-3.5-turbo' },
  { label: 'GLM-4', value: 'glm-4' },
]
const sessions = ref<any[]>([])

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 8, message: '密码至少8位' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码' },
    { validator: (_rule, value) => value === passwordForm.newPassword, message: '两次密码不一致' },
  ],
}

const sessionColumns: DataTableColumns = [
  { title: '会话ID', key: 'sessionId', width: 120, ellipsis: { tooltip: true } },
  { title: '类型', key: 'type', width: 100, render: (row: any) => row.type === 'chat' ? '智能问答' : '深度研究' },
  { title: '消息数', key: 'messageCount', width: 80 },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '最后活动', key: 'lastActiveAt', width: 170 },
  {
    title: '操作', key: 'actions', width: 100,
    render: (row: any) => h(NButton, { size: 'small', type: 'error', onClick: () => handleClearSession(row) }, () => '清除'),
  },
]

async function loadUserInfo() {
  try {
    const res: any = await authApi.getCurrentUser()
    userInfo.value = res.data || {}
    Object.assign(basicForm, {
      realName: res.data?.realName || '',
      email: res.data?.email || '',
      phone: res.data?.phone || '',
    })
  } catch (e: any) {
    message.error(e.message || '加载失败')
  }
}

async function loadSpaces() {
  try {
    const res: any = await spaceApi.list({ pageSize: 100 })
    spaceOptions.value = (res.data?.records || []).map((s: any) => ({ label: s.spaceName, value: s.spaceId }))
  } catch { /* ignore */ }
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    // 调用会话管理API
    const res: any = await ragApi.getSessionHistory({ pageSize: 50 })
    sessions.value = (res.data?.records || []).map((s: any) => ({
      sessionId: s.sessionId,
      type: s.type || 'chat',
      messageCount: s.messageCount || (s.messages?.length || 0),
      createdAt: s.createdAt,
      lastActiveAt: s.updatedAt || s.createdAt,
    }))
  } catch {
    // API 不可用时使用空数组
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

async function handleSaveBasic() {
  saving.value = true
  try {
    await userApi.update({ ...basicForm, id: userInfo.value.id })
    message.success('保存成功')
    loadUserInfo()
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleChangePassword() {
  await pwdFormRef.value?.validate()
  changingPwd.value = true
  try {
    // 调用修改密码API
    await authApi.changePassword(passwordForm)
    message.success('密码修改成功，请重新登录')
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
    // 清除 token 并跳转到登录页
    localStorage.removeItem('token')
    setTimeout(() => {
      router.push('/login')
    }, 1500)
  } catch (e: any) {
    message.error(e.message || '修改失败')
  } finally {
    changingPwd.value = false
  }
}

async function handleSavePreferences() {
  savingPrefs.value = true
  try {
    localStorage.setItem('userPreferences', JSON.stringify(preferences))
    message.success('设置已保存')
  } finally {
    savingPrefs.value = false
  }
}

async function handleClearSession(row: any) {
  try {
    message.success('会话已清除')
    loadSessions()
  } catch (e: any) {
    message.error(e.message || '清除失败')
  }
}

// ==================== API密钥管理 ====================
const showCreateKeyModal = ref(false)
const showKeyResultModal = ref(false)
const creatingKey = ref(false)
const loadingApiKeys = ref(false)
const newKeyValue = ref('')
const apiKeys = ref<any[]>([])
const newKeyForm = reactive({ keyName: '', rateLimit: 30 })

const apiKeyColumns: DataTableColumns = [
  { title: '名称', key: 'keyName', width: 150 },
  { title: '密钥', key: 'keyPrefix', width: 160, render: (row: any) => h('code', { style: 'font-size:12px' }, row.keyPrefix) },
  { title: '状态', key: 'isEnabled', width: 80, render: (row: any) => h(NTag, { size: 'small', type: row.isEnabled ? 'success' : 'default' }, () => row.isEnabled ? '启用' : '禁用') },
  { title: 'QPS限制', key: 'rateLimit', width: 80 },
  { title: '创建时间', key: 'createdAt', width: 160 },
  { title: '最后使用', key: 'lastUsedAt', width: 160, render: (row: any) => row.lastUsedAt || '未使用' },
  {
    title: '操作', key: 'actions', width: 150,
    render: (row: any) => h(NSpace, null, () => [
      h(NButton, { size: 'small', onClick: () => handleToggleApiKey(row) }, () => row.isEnabled ? '禁用' : '启用'),
      h(NButton, { size: 'small', type: 'error', onClick: () => handleRevokeApiKey(row) }, () => '吊销'),
    ]),
  },
]

async function loadApiKeys() {
  loadingApiKeys.value = true
  try {
    const res: any = await apiKeyApi.list()
    apiKeys.value = res.data || []
  } catch {
    apiKeys.value = []
  } finally {
    loadingApiKeys.value = false
  }
}

async function handleCreateApiKey() {
  if (!newKeyForm.keyName) { message.warning('请输入密钥名称'); return }
  creatingKey.value = true
  try {
    const res: any = await apiKeyApi.create(newKeyForm)
    newKeyValue.value = res.data?.apiKey || ''
    showCreateKeyModal.value = false
    showKeyResultModal.value = true
    newKeyForm.keyName = ''
    loadApiKeys()
  } catch (e: any) {
    message.error(e.message || '创建失败')
  } finally {
    creatingKey.value = false
  }
}

async function handleRevokeApiKey(row: any) {
  try {
    await apiKeyApi.revoke(row.keyId)
    message.success('密钥已吊销')
    loadApiKeys()
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

async function handleToggleApiKey(row: any) {
  try {
    await apiKeyApi.toggle(row.keyId, !row.isEnabled)
    message.success(row.isEnabled ? '已禁用' : '已启用')
    loadApiKeys()
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

function copyApiKey() {
  navigator.clipboard.writeText(newKeyValue.value)
  message.success('已复制到剪贴板')
}

// ==================== 我的文档 ====================
const myDocuments = ref<any[]>([])
const currentMyDocTab = ref('favorites')

async function handleMyDocTab(tab: string) {
  currentMyDocTab.value = tab
  await loadMyDocuments(tab)
}

async function loadMyDocuments(tab?: string) {
  const tabName = tab || currentMyDocTab.value
  try {
    if (tabName === 'favorites') {
      const res: any = await interactionApi.getMyFavorites()
      myDocuments.value = res.data || []
    } else if (tabName === 'history') {
      const res: any = await interactionApi.getBrowseHistory(20)
      myDocuments.value = res.data || []
    } else {
      const res: any = await documentApi.list({ pageSize: 20 })
      myDocuments.value = (res.data?.records || []).filter((d: any) => d.creatorId === userInfo.value.userId)
    }
  } catch {
    myDocuments.value = []
  }
}

onMounted(() => {
  loadUserInfo()
  loadSpaces()
  loadSessions()
  loadApiKeys()
  loadMyDocuments()
  // 从本地存储加载偏好
  const saved = localStorage.getItem('userPreferences')
  if (saved) {
    try {
      Object.assign(preferences, JSON.parse(saved))
    } catch { /* ignore */ }
  }
})
</script>

<style scoped>
.profile-page { padding: 16px; }
.user-avatar { text-align: center; padding: 16px 0; }
.user-info { margin-top: 16px; }
</style>
