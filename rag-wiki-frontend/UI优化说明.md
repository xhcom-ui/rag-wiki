# UI样式优化说明

## 📋 优化内容

### 1. 全局样式系统

创建了 `src/assets/global.scss`，包含：

#### 设计令牌（Design Tokens）
```scss
:root {
  /* 主题色 - 企业绿 */
  --primary-color: #408080;
  --primary-hover: #5a9a9a;
  --primary-active: #357070;
  
  /* 辅助色 */
  --success-color: #52c41a;
  --warning-color: #faad14;
  --error-color: #ff4d4f;
  --info-color: #1890ff;
  
  /* 背景、文字、边框、阴影、圆角、间距等 */
}
```

#### 通用组件样式
- ✅ **页面容器** - `.page-container`
- ✅ **页面头部** - `.page-header`
- ✅ **内容卡片** - `.content-card`
- ✅ **统计卡片** - `.stat-card`（支持success/warning/error/info变体）
- ✅ **搜索栏** - `.search-bar`
- ✅ **操作栏** - `.action-bar`
- ✅ **数据表格** - `.data-table`
- ✅ **表单卡片** - `.form-card`
- ✅ **状态标签** - `.status-tag`
- ✅ **空状态** - `.empty-state`

#### 工具类
- 间距：`.mt-sm`, `.mt-md`, `.mt-lg`, `.mb-*`, `.p-*`
- Flex布局：`.flex`, `.flex-col`, `.items-center`, `.justify-between`
- 文本对齐：`.text-center`, `.text-right`

#### 动画效果
- ✅ 淡入动画 - `.fade-in`
- ✅ 滑入动画 - `.slide-in`
- ✅ 自定义滚动条样式

### 2. Dashboard页面优化

#### 改进前
- 简单的n-statistic组件
- 无页面标题
- 卡片样式单一

#### 改进后
```vue
<!-- 页面头部 -->
<div class="page-header">
  <h2>欢迎使用智维Wiki</h2>
  <p>企业级智能安全知识库系统</p>
</div>

<!-- 统计卡片 -->
<div class="stat-cards">
  <div class="stat-card success">
    <div class="stat-label">文档总数</div>
    <div class="stat-value">{{ stats.documentCount }}</div>
    <div class="stat-footer">
      <n-icon :component="DocumentTextOutlined" />
      <span>知识文档</span>
    </div>
  </div>
</div>
```

**视觉效果：**
- 🎨 左侧彩色边框区分不同类型
- 📊 大字显示核心数据
- 🏷️ 底部图标+说明文字
- ✨ 悬停动画效果（上浮+阴影增强）

### 3. 响应式设计

```scss
@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: 1fr;  /* 移动端单列 */
  }
  
  .search-bar {
    flex-direction: column;  /* 搜索栏堆叠 */
  }
}
```

## 🎯 使用指南

### 统计卡片
```vue
<div class="stat-card">  <!-- 默认主色 -->
<div class="stat-card success">  <!-- 绿色 -->
<div class="stat-card warning">  <!-- 橙色 -->
<div class="stat-card error">  <!-- 红色 -->
<div class="stat-card info">  <!-- 蓝色 -->
```

### 内容卡片
```vue
<n-card class="content-card">
  <!-- 内容 -->
</n-card>
```

### 页面布局
```vue
<div class="page-container fade-in">
  <div class="page-header">
    <h2>页面标题</h2>
    <p>页面描述</p>
  </div>
  
  <!-- 统计卡片 -->
  <div class="stat-cards">...</div>
  
  <!-- 内容区 -->
  <n-card class="content-card mt-lg">...</n-card>
</div>
```

## 📱 已优化页面

- ✅ Dashboard首页 - 统计卡片、快捷入口、图表容器
- ✅ 全局样式系统 - 可在所有页面复用

## 🚀 后续优化建议

1. **其他管理页面**
   - admin/user.vue - 用户管理
   - admin/role.vue - 角色管理
   - admin/badcase.vue - Badcase管理
   - admin/statistics.vue - 统计报表

2. **AI功能页面**
   - ai/chat.vue - 聊天界面
   - ai/agent.vue - Agent编排
   - ai/sandbox.vue - 代码沙箱

3. **知识库页面**
   - knowledge/index.vue - 知识库列表
   - knowledge/document.vue - 文档管理

## 🎨 设计原则

1. **一致性** - 统一的色彩、间距、圆角
2. **可用性** - 清晰的视觉层次
3. **响应式** - 适配桌面和移动端
4. **可维护** - 基于Design Tokens，易于主题定制
5. **性能** - CSS动画使用transform和opacity

## 📦 技术栈

- Vue 3 + TypeScript
- Naive UI 组件库
- SCSS 预处理器
- CSS Variables（自定义属性）
- CSS Grid & Flexbox
