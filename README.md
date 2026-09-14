# AI 桌宠（CyberPet）

一个 Live2D 桌面宠物应用：透明置顶的虚拟角色常驻桌面，支持**文字聊天**和**语音对话**，会看鼠标、会说话、嘴型跟着语音动。

## 项目结构

```
.
├── 后端/          Spring Boot 服务：接大模型、语音识别/合成
└── 桌宠前端/      Electron 桌面客户端：Live2D 渲染 + 录音 + 口型同步
```

## 技术栈

| 模块 | 技术 |
|------|------|
| 后端 | Spring Boot 3.4.7 · Java 21 · Spring AI 1.1.4 · OkHttp · SSE |
| 大模型 | 阿里云百炼（通义千问，OpenAI 兼容接口） |
| 语音 | 百度智能云 ASR（识别）+ TTS（合成） |
| 桌宠前端 | Electron 38 · Vue 3 · Vite 6 · TypeScript |
| Live2D | pixi.js 7 · pixi-live2d-display |
| 音频 | MediaRecorder · hark（静音检测）· audiobuffer-to-wav · Web Audio API |

## 工作流程

```
麦克风录音 → 静音检测自动停止 → 转 16kHz 单声道 WAV
   → 后端百度 ASR 识别 → 通义千问流式回复
   → 按标点切段 → 逐段百度 TTS 合成
   → 前端按序播放 + Web Audio 驱动口型
```

## 快速开始

### 1. 后端

```bash
cd 后端

# 配置密钥：复制模板并填入自己的密钥
cp src/main/resources/application-local.example.properties \
   src/main/resources/application-local.properties
# 然后编辑 application-local.properties，填入：
#   AI_API_KEY / BAIDU_API_KEY / BAIDU_SECRET_KEY / FEISHU_WEBHOOK_URL

./mvnw spring-boot:run
```

后端默认跑在 `http://localhost:8080`。

> `application-local.properties` 已被 `.gitignore` 忽略，**不会提交到仓库**。
> `application.properties` 里只有 `${...}` 占位符，可以安全提交。

### 2. 桌宠前端

```bash
cd 桌宠前端
npm install
npm run dev:electron     # 开发模式（Vite + Electron）
```

### 3. Live2D 模型

**仓库里不包含模型文件**，因为模型授权明确禁止二次配布。本地运行前请按
[`桌宠前端/public/README.md`](桌宠前端/public/README.md) 的说明放置模型。

## 说明

- 语音接口 `/api/voice-chat` 用 SSE 推送：`event: userText` 回传识别文本，
  `event: audio` 推送分段音频（base64 MP3），`event: done` 表示结束。
- 文字接口 `/api/text-chat` 返回流式文本。
- 内置两个 AI 工具：`FileTools`（文件操作）、`FeishuTool`（飞书群机器人推送）。
