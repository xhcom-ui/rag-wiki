<template>
  <n-card title="代码沙箱" class="sandbox-page">
    <n-grid :cols="2" :x-gap="16" class="main-grid">
      <!-- 左侧：代码编辑 -->
      <n-gi>
        <n-card title="代码编辑器" size="small" class="code-editor">
          <template #header-extra>
            <n-space>
              <n-select
                v-model:value="language"
                :options="languageOptions"
                size="small"
                style="width: 120px"
              />
              <n-button type="primary" size="small" :loading="running" @click="handleRun">
                <template #icon><n-icon><PlayOutline /></n-icon></template>
                运行
              </n-button>
              <n-button size="small" @click="handleClear">
                <template #icon><n-icon><TrashOutline /></n-icon></template>
                清空
              </n-button>
            </n-space>
          </template>
          <n-input
            v-model:value="code"
            type="textarea"
            class="code-input"
            placeholder="在此输入代码..."
            :style="{ fontFamily: 'monospace' }"
          />
        </n-card>

        <!-- 代码模板 -->
        <n-card title="代码模板" size="small" style="margin-top: 16px">
          <n-space>
            <n-button
              v-for="template in codeTemplates"
              :key="template.name"
              size="small"
              @click="loadTemplate(template)"
            >
              {{ template.name }}
            </n-button>
          </n-space>
        </n-card>
      </n-gi>

      <!-- 右侧：输出结果 -->
      <n-gi>
        <n-card title="执行结果" size="small" class="result-panel">
          <template #header-extra>
            <n-space>
              <n-tag v-if="executionTime" size="small">耗时: {{ executionTime }}ms</n-tag>
              <n-button size="small" @click="handleCopyResult">
                <template #icon><n-icon><CopyOutline /></n-icon></template>
                复制
              </n-button>
            </n-space>
          </template>

          <!-- 输出内容 -->
          <div class="output-content" :class="{ error: hasError }">
            <pre v-if="output">{{ output }}</pre>
            <n-empty v-else description="点击运行按钮执行代码" />
          </div>
        </n-card>

        <!-- 执行历史 -->
        <n-card title="执行历史" size="small" style="margin-top: 16px" class="history-panel">
          <n-list>
            <n-list-item v-for="(item, index) in history" :key="index">
              <n-thing>
                <template #header>
                  <n-space>
                    <n-tag size="small" :type="item.success ? 'success' : 'error'">
                      {{ item.success ? '成功' : '失败' }}
                    </n-tag>
                    <n-text depth="3">{{ formatTime(item.time) }}</n-text>
                  </n-space>
                </template>
                <template #description>
                  <n-text code class="code-preview">{{ item.code?.slice(0, 50) }}...</n-text>
                </template>
              </n-thing>
              <template #suffix>
                <n-button text size="small" @click="loadHistory(item)">加载</n-button>
              </template>
            </n-list-item>
          </n-list>
          <n-empty v-if="!history.length" description="暂无执行记录" />
        </n-card>
      </n-gi>
    </n-grid>

    <!-- 安全检查提示 -->
    <n-alert type="warning" style="margin-top: 16px" :show-icon="true">
      <template #header>安全提示</template>
      代码将在隔离环境中执行，禁止访问网络、文件系统等敏感资源。执行时间限制为30秒。
    </n-alert>
  </n-card>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useMessage } from 'naive-ui'
import { PlayOutline, TrashOutline, CopyOutline } from '@vicons/ionicons5'
import { sandboxApi } from '@/api'

const message = useMessage()

// 代码编辑器
const code = ref(`# Python 示例代码
# 计算斐波那契数列

def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

# 计算前10个斐波那契数
result = [fibonacci(i) for i in range(10)]
print("斐波那契数列:", result)

# 计算总和
print("总和:", sum(result))
`)

const language = ref('python')
const running = ref(false)
const output = ref('')
const executionTime = ref(0)
const hasError = ref(false)

// 语言选项
const languageOptions = [
  { label: 'Python', value: 'python' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'Bash', value: 'bash' },
]

// 代码模板
const codeTemplates = [
  {
    name: '斐波那契',
    language: 'python',
    code: `def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

result = [fibonacci(i) for i in range(10)]
print(result)`,
  },
  {
    name: '数据处理',
    language: 'python',
    code: `# 数据处理示例
data = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

# 计算平均值
avg = sum(data) / len(data)
print(f"平均值: {avg}")

# 筛选大于5的数
filtered = [x for x in data if x > 5]
print(f"大于5的数: {filtered}")`,
  },
  {
    name: '字符串处理',
    language: 'python',
    code: `text = "Hello, World!"

# 字符串操作
print(f"原始文本: {text}")
print(f"大写: {text.upper()}")
print(f"小写: {text.lower()}")
print(f"长度: {len(text)}")
print(f"分割: {text.split(', ')}")`,
  },
  {
    name: '日期时间',
    language: 'python',
    code: `from datetime import datetime, timedelta

now = datetime.now()
print(f"当前时间: {now}")
print(f"格式化: {now.strftime('%Y-%m-%d %H:%M:%S')}")

# 计算未来日期
future = now + timedelta(days=7)
print(f"一周后: {future.strftime('%Y-%m-%d')}")`,
  },
]

// 执行历史
const history = reactive<any[]>([])

// 运行代码
async function handleRun() {
  if (!code.value.trim()) {
    message.warning('请输入代码')
    return
  }

  running.value = true
  output.value = '执行中...'
  hasError.value = false
  const startTime = Date.now()

  try {
    const res: any = await sandboxApi.execute({
      code: code.value,
      language: language.value,
    })

    executionTime.value = Date.now() - startTime

    if (res.code === 200) {
      output.value = res.data?.output || '执行成功，无输出'
      hasError.value = false

      // 添加到历史
      history.unshift({
        code: code.value,
        output: output.value,
        success: true,
        time: new Date(),
        executionTime: executionTime.value,
      })
    } else {
      output.value = res.message || '执行失败'
      hasError.value = true
    }
  } catch (error: any) {
    executionTime.value = Date.now() - startTime
    output.value = error.message || '执行出错'
    hasError.value = true

    // 添加到历史
    history.unshift({
      code: code.value,
      output: output.value,
      success: false,
      time: new Date(),
      executionTime: executionTime.value,
    })
  } finally {
    running.value = false
    // 限制历史记录数量
    if (history.length > 10) {
      history.pop()
    }
  }
}

// 清空代码
function handleClear() {
  code.value = ''
  output.value = ''
  executionTime.value = 0
}

// 加载模板
function loadTemplate(template: any) {
  language.value = template.language
  code.value = template.code
  message.success(`已加载模板: ${template.name}`)
}

// 加载历史记录
function loadHistory(item: any) {
  code.value = item.code
  message.success('已加载历史代码')
}

// 复制结果
function handleCopyResult() {
  if (!output.value) {
    message.warning('没有可复制的内容')
    return
  }
  navigator.clipboard.writeText(output.value).then(() => {
    message.success('已复制到剪贴板')
  })
}

// 格式化时间
function formatTime(date: Date): string {
  return date.toLocaleTimeString()
}
</script>

<style scoped>
.sandbox-page {
  padding: 16px;
}
.main-grid {
  height: calc(100vh - 200px);
}
.code-editor,
.result-panel {
  height: calc(50% - 8px);
}
.code-input {
  height: calc(100% - 60px);
  min-height: 300px;
}
.code-input :deep(textarea) {
  height: 100% !important;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 14px;
  line-height: 1.6;
}
.output-content {
  height: calc(100% - 40px);
  min-height: 200px;
  background: #f5f5f5;
  border-radius: 4px;
  padding: 12px;
  overflow: auto;
}
.output-content.error {
  background: #fff2f0;
  color: #cf1322;
}
.output-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
}
.history-panel {
  max-height: calc(50% - 8px);
  overflow: auto;
}
.code-preview {
  font-size: 12px;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>