# LiveCapture AI - 实时智能转录与 AI 分析工具

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/qasax/asrAgent/blob/master/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue.js-3.x-4fc08d.svg)](https://vuejs.org/)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)](https://github.com/qasax/asrAgent)

> **LiveCapture AI** 是一款基于 AI 驱动的实时语音转录与智能分析系统。它能够捕获系统音频或麦克风语音，提供精准的实时双语字幕，并利用强大的 AI Agent 协作流进行要点总结、待办提取及实时智能问答，让会议与学习效率倍增。
前端：https://github.com/qasax/asrAgent-desktop
---

## 🌟 核心亮点

- **实时语义流**: 整合千问实时语音识别与快速翻译模型，极低延迟的字幕推送。
- **智能 Agent 工作流**: 基于 Spring AI Graph 构建，实现从转录、翻译到总结、可视化的全自动化工作流。
- **增强型问答 (RAG)**: 集成检索增强生成技术，使 AI 能基于会议背景实时精准回答。
- **可视化总结**: 自动生成思维导图、流程图及关键词词云，一眼洞察核心内容。

---

## ✨ 主要特性

| 特性 | 描述 | 状态 |
| :--- | :--- | :--- |
| **实时转录** | 支持系统音频/麦克风语音实时转录为文字 | ✅ 已完成 |
| **智能翻译** | 实时多语言翻译，双语字幕对照展示 | ✅ 已完成 |
| **AI 总结** | 自动提取会议关键要点、Action Items 与待办事项 | ✅ 已完成 |
| **可视化输出** | 支持生成思维导图、流程图、词云等可视化报表 | ✅ 已完成 |
| **RAG 问答** | 基于历史与当前转录内容的智能语义检索与问答 | ✅ 已完成 |
| **安全审计** | 拦截非法输入，确保 Prompt 安全与系统稳定 | ✅ 已完成 |

---

## 🛠️ 技术架构

- **后端**: 
  - [Spring Boot 3.x](https://spring.io/) + [Spring AI](https://spring.io/projects/spring-ai)
  - [WebSocket](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API) / [SSE](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events) 实现双向流式通信
  - 集成**通义千问 (DashScope)** 语音与大模型
- **前端**: 
  - [Vue 3](https://vuejs.org/) (Composition API) + [Vite](https://vitejs.dev/)
  - [Ant Design Vue 4.x](https://www.antdv.com/)
  - [Axios](https://axios-http.com/)
- **存储与检索**: 
  - RAG 检索增强生成
  - 会话级状态管理 (Long/Short-term Memory Hooks)

---

## 🚀 快速开始

### 环境依赖

- Java 17+
- Node.js 18+
- Maven 3.6+
- 通义千问 API Key (DashScope)

### 后端配置

1. 克隆项目:
   ```bash
   git clone https://github.com/qasax/asrAgent.git
   cd asrAgent
   ```
2. 修改 `src/main/resources/application.yml` 配置您的 API Key:
   ```yaml
   spring:
     ai:
       dashscope:
         api-key: YOUR_API_KEY
   ```
3. 编译运行:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

### 前端配置

1. 进入前端目录 (假设位于 `frontend`):
   ```bash
   cd src/main/resources/frontend # 或您的实际前端目录
   npm install
   ```
2. 启动开发服务器:
   ```bash
   npm run dev
   ```

---

## 💡 使用示例

### 实时转录与问答演示

![界面示例](docs/screenshot.png)

1. **开启转录**: 点击界面上的“开始捕获”按钮，AI 将立即开始监听并展示实时字幕。
2. **AI 互动**: 在侧边栏提问，例如：“请根据刚才的谈话总结一下接下来的待办事项”，AI 将结合上下文给出回答。
3. **可视化输出**: 会议结束后，点击“生成总结”，系统会自动渲染思维导图或流程图。

---

## ⚙️ 核心配置说明

| 参数名称 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `dashscope.api-key` | String | - | 必备，通义千问 API 密钥 |
| `asr.realtime.enabled` | Boolean | `true` | 是否启用实时语音识别 |
| `agent.memory.history-size` | Integer | `20` | 会话长期记忆保留的消息数量 |

---

## 🤝 参与贡献

我们非常欢迎社区的贡献！

1. **Fork** 本仓库
2. **Clone** 到本地环境
3. 创建新的功能分支 (`git checkout -b feature/AmazingFeature`)
4. **Commit** 您的修改 (`git commit -m 'Add some AmazingFeature'`)
5. **Push** 分支 (`git push origin feature/AmazingFeature`)
6. 开启一个 **Pull Request**

---

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE) 发布。

Copyright © 2026 LiveCapture AI Team.
