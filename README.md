# grok-app-googlebookos

> **Grok Workbench for Googlebook OS (Android Laptop)**  
> 专为 Googlebook OS / Android Laptop 打造的原生 Grok Build Agent 桌面工作台（前端 GUI）。

---

## 🌟 项目简介

`grok-app-googlebookos` 是基于 **Jetpack Compose + Material 3** 开发的原生 Android 工作台应用，针对 Android Laptop / 大屏平板设备深度优化。

它作为 **Grok Build CLI** (`grok agent stdio`) 的可视化前端，通过极低延迟的本地 Web 协议（HTTP REST + Server-Sent Events 流式通道）与 Linux VM 中运行的 Agent 后端进行通信交互。

---

## 🏗️ 架构设计

```mermaid
flowchart LR
    subgraph AndroidHost ["Android Host（前端 GUI）"]
        A[Grok Workbench Android App] --> B[会话管理与检索]
        A --> C[流式打字机 & 深度思考折叠]
        A --> D[代码高亮与一键复制]
        A --> E[模型 / 思考预算 / 权限快捷切换]
    end

    subgraph Comm ["通信层 (Localhost 8088)"]
        F["HTTP REST (会话 CRUD / 服务探测)"]
        G["SSE 流式长连接 (思考 / 文本 / 工具执行)"]
    end

    subgraph LinuxVM ["Linux VM（后端 Agent 执行环境）"]
        H[grok-web 服务 (server.py)] <--> I["grok build CLI (grok agent stdio)"]
        I <--> J[本地代码工程 / Git / Shell 执行]
    end

    A <===>|Localhost HTTP / SSE| H
    J <.-.->|共享挂载 /storage/emulated/10| A
```

---

## ✨ 核心特性

- 🖥️ **大屏分栏工作台**：针对笔记本/横屏优化，左侧支持会话快捷检索、归档过滤与状态监控；右侧为主工作区与实时时间线。
- 🧠 **深度思考折叠卡片**：实时展示 Grok 思考过程（Thinking Tokens），支持折叠/展开与思考状态动态反馈。
- 🛠️ **工具执行轨迹**：直观展示 Agent 调用的工具、参数与输出结果。
- ⌨️ **桌面级快捷键**：支持 `Ctrl + Enter` / `Cmd + Enter` 极速发送指令。
- 📝 **Markdown 与代码高亮**：原生解析 Markdown 代码块，提供暗色背景代码容器及一键复制到剪贴板。
- 🎛️ **多模型与权限切换**：
  - **模型**：Grok 4.7 (旗舰), Grok 4.7 Fast (极速), Grok 4.6, Grok 4.5
  - **思考预算**：高 (High), 极高 (Extra High), 中 (Medium), 低 (Low)
  - **权限模式**：全自动 (YOLO), 自动写代码, 计划模式 (Plan), 标准安全模式
- 🟢 **后端健康探测**：实时感知 `http://127.0.0.1:8088` 服务状态与代理连通性。

---

## 🚀 快速开始

### 1. 启动 Linux VM 后端服务
在 Linux VM 终端中启动 `grok-web` 后端服务：
```bash
python3 /storage/emulated/10/Codes/grok-web/server.py
```
默认监听 `http://0.0.0.0:8088`。

### 2. 编译并安装 Android 前端
```bash
# 编译 Debug APK
./gradlew assembleDebug

# 生成的安装包位置：
app/build/outputs/apk/debug/app-debug.apk
```

在系统文件管理器中点击生成的 APK 文件即可安装运行。

---

## 📄 许可证

MIT License
