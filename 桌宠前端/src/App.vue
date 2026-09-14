<template>
  <audio ref="audioPlayer" @ended="onAudioEnd"></audio>
  <!-- ★ 完整的设置窗口 -->
  <div v-if="isSettings" class="settings-window">
    <div class="settings-titlebar">
      <span>设置</span>
      <button class="titlebar-close" onclick="window.close()">X</button>
    </div>
    <div class="settings-tabs">
      <button :class="{ active: settingsTab === 'chat' }" @click="settingsTab = 'chat'">聊天记录</button>
      <button :class="{ active: settingsTab === 'shortcut' }" @click="settingsTab = 'shortcut'">快捷键</button>
    </div>

    <!-- 聊天 Tab -->
    <div v-if="settingsTab === 'chat'" class="settings-chat" ref="chatArea">
      <div v-for="(msg, i) in displayConversation" :key="i" :class="['bubble', msg.role]">
        {{ msg.text }}
      </div>
      <div v-if="loading" class="bubble pet loading-bubble">
        <span class="dots"><span>.</span><span>.</span><span>.</span></span>
      </div>
    </div>

    <div v-if="settingsTab === 'chat'" class="chat-input-bar">
      <input v-model="textInput" class="chat-text-input" placeholder="输入消息..."
        @keydown.enter="sendTextMessage" />
      <button class="chat-send-btn" @click="sendTextMessage"
        :disabled="!textInput.trim() || loading">发送</button>
    </div>

    <div v-if="settingsTab === 'shortcut'" class="shortcut-settings">
  <div class="shortcut-current">
    当前快捷键 <span class="key-badge">{{ currentShortcut }}</span>
  </div>
  <div v-if="capturing" class="shortcut-capturing">正在监听按键...</div>
  <div v-if="capturedKey" class="shortcut-new">
    新快捷键: <span class="key-badge">{{ capturedKey }}</span>
  </div>
  <div class="shortcut-actions">
    <button v-if="!capturing" class="shortcut-btn" @click="startCapture">设置快捷键</button>
    <button v-if="capturedKey" class="shortcut-btn primary" @click="saveShortcut">保存</button>
  </div>
</div>
  </div>

  <!-- 宠物窗口 -->
  <div v-else class="pet-window">
      <div class="pet-body" :style="{ width: modelConfig.canvas.width + 'px', height: modelConfig.canvas.height + 'px' }" @mousedown="startPetDrag">
      <div class="pet-drag-region"></div>
      <Live2DCanvas
        ref="live2dRef"
        :width="modelConfig.canvas.width"
        :height="modelConfig.canvas.height"
        :model-name="modelConfig.modelName"
        :model-file="modelConfig.modelFile"
        :scale="modelConfig.scale"
        :x-multiplier="modelConfig.position.xMultiplier"
        :y-multiplier="modelConfig.position.yMultiplier"
      />
    </div>

    <!-- ★ 对话气泡 -->
    <div v-if="hintText" class="speech-hint">{{ hintText }}</div>
  </div>
</template>



<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from "vue"
import Live2DCanvas from "./components/Live2DCanvas.vue"
import { modelConfig } from "./live2d/config.js"
import toWav from "audiobuffer-to-wav"
import hark from "hark"

const isSettings = window.location.hash === "#settings"

// Electron IPC（仅 Electron 环境可用）
const { ipcRenderer } = (window as any).require
  ? (window as any).require("electron")
  : ({} as any)

const API = "http://localhost:8080/api/text-chat"
const VOICE_API = "http://localhost:8080/api/voice-chat"

// ---- 状态 ----
const settingsTab = ref("chat")
const loading = ref(false)
const textInput = ref("")
const chatArea = ref<HTMLDivElement>()
const live2dRef = ref<any>()
const hintText = ref("")

interface Message { role: "user" | "pet"; text: string }
const conversation = ref<Message[]>([])

// ★ 设置窗口用：合并连续的 pet 消息，避免满屏碎片气泡
const displayConversation = computed(() => {
    const merged: Message[] = []
    for (const msg of conversation.value) {
        const last = merged[merged.length - 1]
        if (last && last.role === "pet" && msg.role === "pet") {
            last.text += msg.text
        } else {
            merged.push({ ...msg })
        }
    }
    return merged
})


// ===== 语音相关状态 =====
const isRecording = ref(false)
const audioPlayer = ref<HTMLAudioElement>()
let mediaRecorder: MediaRecorder | null = null
let audioChunks: Blob[] = []
let audioQueue: any[] = []
let lipSyncContext: AudioContext | null = null
let lipSyncAnalyser: AnalyserNode | null = null
let lipSyncSource: MediaElementAudioSourceNode | null = null
let lipSyncData: Uint8Array<ArrayBuffer> | null = null

// 下一节实现真实的语音播放队列。
function startLipSync() {
  const player = audioPlayer.value
  if (!player || lipSyncSource) return
  lipSyncContext = new AudioContext()
  lipSyncAnalyser = lipSyncContext.createAnalyser()
  lipSyncAnalyser.fftSize = 256
  lipSyncData = new Uint8Array(new ArrayBuffer(lipSyncAnalyser.fftSize))
  lipSyncSource = lipSyncContext.createMediaElementSource(player)
  lipSyncSource.connect(lipSyncAnalyser)
  lipSyncAnalyser.connect(lipSyncContext.destination)

  const updateMouth = () => {
    if (!lipSyncAnalyser || !lipSyncData || !audioPlayer.value || audioPlayer.value.ended) {
      live2dRef.value?.live2d?.setMouthOpen(0)
      lipSyncTimer = null
      return
    }
    lipSyncAnalyser.getByteTimeDomainData(lipSyncData)
    let amplitude = 0
    for (const value of lipSyncData) amplitude += Math.abs(value - 128) / 128
    const average = amplitude / lipSyncData.length
    const mouthOpen = Math.min(1, Math.max(0, (average - 0.02) * 5))
    live2dRef.value?.live2d?.setMouthOpen(mouthOpen)
    lipSyncTimer = window.requestAnimationFrame(updateMouth)
  }
  if (lipSyncContext.state === "suspended") void lipSyncContext.resume()
  updateMouth()
}

function stopLipSync() {
  if (lipSyncTimer !== null) {
    window.cancelAnimationFrame(lipSyncTimer)
    lipSyncTimer = null
  }
  live2dRef.value?.live2d?.setMouthOpen(0)
  lipSyncSource?.disconnect()
  lipSyncAnalyser?.disconnect()
  void lipSyncContext?.close()
  lipSyncSource = null
  lipSyncAnalyser = null
  lipSyncData = null
  lipSyncContext = null
}

function playNextInQueue() {
  if (audioQueue.length === 0) {
    hintText.value = ""
    stopLipSync()
    return
  }

  const item = audioQueue[0]
  hintText.value = item.text
  if (item.text) {
    conversation.value.push({ role: "pet", text: item.text })
    notifyConvUpdate()
  }
  playBase64Audio(item.data)
}

function onAudioEnd() {
  stopLipSync()
  audioQueue.shift()
  playNextInQueue()
}

function playBase64Audio(b64: string) {
  const bytes = atob(b64)
  const buffer = new Uint8Array(bytes.length)
  for (let i = 0; i < bytes.length; i++) buffer[i] = bytes.charCodeAt(i)

  const blob = new Blob([buffer], { type: "audio/mp3" })
  audioPlayer.value = new Audio(URL.createObjectURL(blob))
  audioPlayer.value.onended = onAudioEnd
  audioPlayer.value.onerror = () => {
    stopLipSync()
    onAudioEnd()
  }
  startLipSync()
  audioPlayer.value.play().catch(() => {})
}
let maxRecordTimer = 0
let harkInstance: any = null
let lipSyncTimer: number | null = null
let silenceTimer: number | null = null
let sseAbort: AbortController | null = null
  


async function parseSSEStream(res: Response, onEvent: (event: string, data: any) => void) {
  const reader = res.body?.getReader()
  if (!reader) return

  const decoder = new TextDecoder()
  let buffer = ""

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split("\n\n")
    buffer = blocks.pop() || ""

    for (const block of blocks) {
      let event = "message"
      const dataLines: string[] = []

      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) event = line.substring(6).trim()
        if (line.startsWith("data:")) dataLines.push(line.substring(5).trim())
      }

      const data = dataLines.join("\n")
      if (!data) continue

      try {
        onEvent(event, JSON.parse(data))
      } catch {
        // 忽略格式不完整或不是 JSON 的事件。
      }
    }
  }
}

async function sendToBackend() {
  const webmBlob = new Blob(audioChunks, { type: "audio/webm" })
  audioChunks = []

  const userIndex = conversation.value.length
  conversation.value.push({ role: "user", text: "语音识别中..." })
  conversation.value.push({ role: "pet", text: "思考中..." })
  hintText.value = "..."
  loading.value = true
  notifyConvUpdate()

  let thinkingRemoved = false
  const removeThinking = () => {
    if (thinkingRemoved) return
    const index = conversation.value.findIndex((message) =>
      message.role === "pet" && message.text === "思考中..."
    )
    if (index !== -1) conversation.value.splice(index, 1)
    thinkingRemoved = true
  }

  try {
    const wavBlob = await webmToWav(webmBlob)
    const formData = new FormData()
    formData.append("audio", wavBlob, "recording.wav")

    sseAbort = new AbortController()
    const res = await fetch(VOICE_API, {
      method: "POST",
      body: formData,
      signal: sseAbort.signal
    })

    if (!res.ok) throw new Error("语音接口返回 " + res.status)

    await parseSSEStream(res, (event, payload) => {
      if (event === "userText") {
        if (payload.text) {
          if (conversation.value[userIndex]) conversation.value[userIndex].text = payload.text
        } else {
          conversation.value.splice(userIndex, 1)
          removeThinking()
          hintText.value = ""
        }
        notifyConvUpdate()
        return
      }

      if (event === "audio") {
        removeThinking()
        if (payload.data) {
          const wasEmpty = audioQueue.length === 0
          audioQueue.push(payload)
          if (wasEmpty) playNextInQueue()
        } else if (payload.text) {
          conversation.value.push({ role: "pet", text: payload.text })
          hintText.value = payload.text
          notifyConvUpdate()
        }
        return
      }

      if (event === "done") {
        loading.value = false
        return
      }

      if (event === "error") {
        conversation.value.push({ role: "pet", text: "语音出错了：" + payload.message })
        notifyConvUpdate()
        loading.value = false
      }
    })
  } catch (error: any) {
    if (error?.name !== "AbortError") {
      conversation.value.push({ role: "pet", text: "网络好像不太好，请稍后再试" })
      notifyConvUpdate()
    }
    loading.value = false
  }
}


// ===== 录音 =====
async function startRecording() {
    if (isRecording.value) return
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        mediaRecorder = new MediaRecorder(stream, { mimeType: "audio/webm" })
        audioChunks = []
        mediaRecorder.ondataavailable = e => audioChunks.push(e.data)
        mediaRecorder.onstop = () => {
            stream.getTracks().forEach(t => t.stop())
            clearSilenceDetection()
            hintText.value = ""
            sendToBackend()
        }
        mediaRecorder.start()
        isRecording.value = true
        hintText.value = "聆听中..."
        startSilenceDetection(stream)
        maxRecordTimer = window.setTimeout(() => { if (isRecording.value) stopRecording() }, 15000)
    } catch (e) {
        alert("无法访问麦克风，请检查权限设置")
    }
}

function stopRecording() {
    if (maxRecordTimer) { clearTimeout(maxRecordTimer); maxRecordTimer = 0 }
    if (mediaRecorder && mediaRecorder.state === "recording") {
        mediaRecorder.stop()
        isRecording.value = false
    }
}

// ===== 静音检测：hark 库，2 秒无声自动停止 =====
function startSilenceDetection(stream: MediaStream) {
    clearSilenceDetection()
    harkInstance = hark(stream, { threshold: -40, interval: 100 })
    harkInstance.on('stopped_speaking', () => {
        if (silenceTimer !== null) window.clearTimeout(silenceTimer)
        silenceTimer = window.setTimeout(() => {
            silenceTimer = null
            if (isRecording.value) stopRecording()
        }, 5000)
    })
    harkInstance.on('speaking', () => {
        if (silenceTimer !== null) {
            window.clearTimeout(silenceTimer)
            silenceTimer = null
        }
    })
    silenceTimer = window.setTimeout(() => {
        silenceTimer = null
        if (isRecording.value) stopRecording()
    }, 5000)
}

function clearSilenceDetection() {
    if (silenceTimer !== null) {
        window.clearTimeout(silenceTimer)
        silenceTimer = null
    }
    if (harkInstance) { harkInstance.stop(); harkInstance = null }
}

// ===== WebM → WAV 转换（audiobuffer-to-wav 库） =====
async function webmToWav(blob: Blob): Promise<Blob> {
    const decodeContext = new AudioContext()
    const sourceBuffer = await decodeContext.decodeAudioData(await blob.arrayBuffer())
    const targetSampleRate = 16000
    const targetFrames = Math.ceil(sourceBuffer.duration * targetSampleRate)
    const offlineContext = new OfflineAudioContext(1, targetFrames, targetSampleRate)
    const source = offlineContext.createBufferSource()
    const monoBuffer = offlineContext.createBuffer(1, sourceBuffer.length, sourceBuffer.sampleRate)
    const monoChannel = monoBuffer.getChannelData(0)

    for (let channel = 0; channel < sourceBuffer.numberOfChannels; channel++) {
        const channelData = sourceBuffer.getChannelData(channel)
        for (let i = 0; i < channelData.length; i++) {
            monoChannel[i] += channelData[i] / sourceBuffer.numberOfChannels
        }
    }

    source.buffer = monoBuffer
    source.connect(offlineContext.destination)
    source.start()
    const resampledBuffer = await offlineContext.startRendering()
    await decodeContext.close()
    return new Blob([toWav(resampledBuffer)], { type: "audio/wav" })
}


function notifyConvUpdate() {
  if (ipcRenderer?.send)
    ipcRenderer.send("conv-update", JSON.parse(JSON.stringify(conversation.value)))
}


function onMouseMove(e: MouseEvent) {
  live2dRef.value?.live2d?.lookAt(e.clientX, e.clientY)
}

let petDragging = false
let petDidDrag = false
let petLastScreenX = 0
let petLastScreenY = 0

function startPetDrag(event: MouseEvent) {
  if (event.button !== 0) return
  petDragging = true
  petDidDrag = false
  petLastScreenX = event.screenX
  petLastScreenY = event.screenY
  window.addEventListener("mousemove", onPetDrag)
  window.addEventListener("mouseup", stopPetDrag, { once: true })
}

function onPetDrag(event: MouseEvent) {
  if (!petDragging) return
  const dx = event.screenX - petLastScreenX
  const dy = event.screenY - petLastScreenY
  if (Math.abs(event.screenX - petLastScreenX) > 3 || Math.abs(event.screenY - petLastScreenY) > 3) {
    petDidDrag = true
  }
  if (dx !== 0 || dy !== 0) {
    ipcRenderer?.send?.("move-window", dx, dy)
    petLastScreenX = event.screenX
    petLastScreenY = event.screenY
  }
}

function stopPetDrag() {
  const wasClick = !petDidDrag
  petDragging = false
  window.removeEventListener("mousemove", onPetDrag)
  if (wasClick) live2dRef.value?.live2d?.playTapReaction()
  window.setTimeout(() => { petDidDrag = false }, 0)
}

// Electron 主进程传来的全局鼠标坐标（已经转换为当前窗口内坐标）
function onGlobalMouseMove(_event: unknown, x: number, y: number) {
  live2dRef.value?.live2d?.lookAt(x, y)
}
    
function scrollDown() { nextTick(() => { if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight }) }

// ---- 文字对话（复用第1课的 SSE 流式消费） ----
async function sendTextMessage() {
  let currentChunk = '';
  const text = textInput.value.trim()
  if (!text || loading.value) return
  textInput.value = ""
  loading.value = true

  conversation.value.push({ role: "user", text })
  notifyConvUpdate()
  scrollDown()
  hintText.value = "..."

  try {
    sseAbort = new AbortController()
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text }),
      signal: sseAbort.signal
    })

    if (!res.ok) {
      throw new Error(`AI 接口返回 ${res.status}`)
    }

    // 后端当前返回普通文本；保留对 SSE 的兼容，避免丢弃 AI 回复
    const contentType = res.headers.get("content-type") || ""
    if (!contentType.includes("text/event-stream")) {
      const answer = (await res.text()).trim()
      if (answer) {
        conversation.value.push({ role: "pet", text: answer })
        notifyConvUpdate()
        hintText.value = answer
        scrollDown()
      }
      return
    }

    const reader = res.body!.getReader()
    const decoder = new TextDecoder()
    let buf = ""
    let aiText = ""
    const aiMsg: Message = { role: "pet", text: "" }
    conversation.value.push(aiMsg)

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const parts = buf.split("\n\n"); buf = parts.pop() || ""
      for (const part of parts) {
        if (!part.startsWith("data:")) continue
        const data = part.substring(5).trim()
        if (!data || data === "[DONE]") continue
        currentChunk += data

        // ★ 多气泡切分：遇到标点或满 20 字就截断
        while (currentChunk.length > 0) {
          const punctMatch = currentChunk.match(/[。！？\n]/)
          const punctIdx = punctMatch ? punctMatch.index! + 1 : -1
          let splitAt: number
          if (punctIdx > 0 && punctIdx <= 20) {
            splitAt = punctIdx
          } else if (currentChunk.length >= 20) {
            splitAt = 20
          } else {
            break
          }
          const segment = currentChunk.substring(0, splitAt).trim()
          currentChunk = currentChunk.substring(splitAt)
          if (segment) {
            conversation.value.push({ role: "pet", text: segment })
            notifyConvUpdate()
            hintText.value = segment
            scrollDown()
          }
        }
      }
    }
    // 吐出不满足 20 字的残句
    if (currentChunk.trim()) {
      conversation.value.push({ role: "pet", text: currentChunk.trim() })
      notifyConvUpdate()
      hintText.value = currentChunk.trim()
      scrollDown()
    }
  } catch (e: any) {
    if (e?.name !== "AbortError") {
      conversation.value.push({ role: "pet", text: "网络好像不太好，请稍后再试" })
      notifyConvUpdate()
    }
  } finally {
    loading.value = false
  }
}
let bubbleQueue: string[] = []
let bubbleTimer: number | null = null
let bubbleEmptyCount = 0

// ★ 气泡播放队列：按文字类型估算停留时长（中文~170ms/字，英文/数字~50ms/字，标点不计），依次播放
function showNextBubble() {
  if (bubbleQueue.length === 0) {
    bubbleEmptyCount++
    if (bubbleEmptyCount > 10) {
      hintText.value = ""
      bubbleTimer = null
      return
    }
    bubbleTimer = window.setTimeout(showNextBubble, 500)
    return
  }

  bubbleEmptyCount = 0
  const text = bubbleQueue.shift()!
  hintText.value = text
  const cjk = (text.match(/[\u4e00-\u9fff]/g) || []).length
  const eng = (text.match(/[a-zA-Z0-9]/g) || []).length
  const ms = Math.max(3000, Math.min(8000, cjk * 170 + eng * 50))
  bubbleTimer = window.setTimeout(showNextBubble, ms)
}
//初始化时候从主进程拿到数据
onMounted(() => {
  // Electron 中使用全局鼠标追踪；浏览器开发模式保留窗口内追踪作为回退
  if (!isSettings && ipcRenderer?.on) {
    ipcRenderer.on("global-mousemove", onGlobalMouseMove)
  } else if (!isSettings) {
    document.addEventListener("mousemove", onMouseMove)
  }

  if (!isSettings) {
    ipcRenderer?.on("voice-toggle", () => {
      isRecording.value ? stopRecording() : startRecording()
    })
  }
  if (ipcRenderer?.sendSync) {
  currentShortcut.value = ipcRenderer.sendSync("get-shortcut")
}
  // ★ 从主进程拿历史对话 + 监听实时同步
  if (ipcRenderer?.sendSync) {
    conversation.value = ipcRenderer.sendSync("get-conversation")
  }
  //接受广播
  ipcRenderer?.on("conv-update", (_e: any, msgs: Message[]) => {
    const prevLen = conversation.value.length
    conversation.value = [...msgs]
    // 取新增的 pet 消息，塞进气泡队列
    for (let i = prevLen; i < msgs.length; i++) {
      if (msgs[i].role === "pet") bubbleQueue.push(msgs[i].text)
    }
    if (bubbleQueue.length > 0) {
      if (bubbleTimer) { clearTimeout(bubbleTimer); bubbleTimer = null }
      showNextBubble()
    }
  })
})

onUnmounted(() => {
  clearSilenceDetection()
  stopLipSync()
  if (!isSettings && ipcRenderer?.removeListener) {
    ipcRenderer.removeListener("global-mousemove", onGlobalMouseMove)
  } else if (!isSettings) {
    document.removeEventListener("mousemove", onMouseMove)
  }
  ipcRenderer?.removeAllListeners?.("voice-toggle")
})
const currentShortcut = ref("Ctrl+Alt+L")
const capturedKey = ref("")
const capturing = ref(false)

function startCapture() {
  capturing.value = true
  capturedKey.value = ""
  const onKeyDown = (e: KeyboardEvent) => {
    e.preventDefault(); e.stopPropagation()
    if (e.key === "Escape") {
      capturing.value = false
      window.removeEventListener("keydown", onKeyDown, true)
      return
    }
    if (["Control", "Alt", "Shift", "Meta"].includes(e.key)) return
    const parts: string[] = []
    if (e.ctrlKey) parts.push("Ctrl")
    if (e.altKey) parts.push("Alt")
    if (e.shiftKey) parts.push("Shift")
    if (e.metaKey) parts.push("Meta")
    parts.push(e.key.length === 1 ? e.key.toUpperCase() : e.key)
    capturedKey.value = parts.join("+")
    capturing.value = false
    window.removeEventListener("keydown", onKeyDown, true)
  }
  window.addEventListener("keydown", onKeyDown, true)
}

function saveShortcut() {
  if (!capturedKey.value || !ipcRenderer?.send) return
  ipcRenderer.send("set-shortcut", capturedKey.value)
  currentShortcut.value = capturedKey.value
  capturedKey.value = ""
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app {
  width: 100%; height: 100%; overflow: hidden;
  background: transparent;
}
body { font-family: "Microsoft YaHei", sans-serif; }

.settings-window {
  width: 100vw; height: 100vh;
  color: #c8d0d8;
  display: flex; flex-direction: column;
}
.pet-window {
  width: 100vw; height: 100vh;
  display: flex; align-items: center; justify-content: center;
  user-select: none;
}
.pet-body {
  position: relative;
  cursor: grab;
  flex-shrink: 0;
}
.pet-body:active { cursor: grabbing; }
.pet-drag-region {
  position: absolute; top: 0; left: 0; right: 0; height: 28px; z-index: 1;
  -webkit-app-region: drag;   /* ★ 允许拖拽窗口 */
}
.speech-hint {
  position: absolute; top: 10px; left: 50%;
  transform: translateX(-50%);
  min-width: 80px; max-width: 340px;
  background: rgba(20, 25, 50, 0.95);
  border: 1px solid rgba(100, 160, 255, 0.35);
  border-radius: 12px 12px 4px 12px;
  padding: 8px 14px;
  font-size: 12px; line-height: 1.6;
  color: #c8d2e8; z-index: 10;
  pointer-events: none;
  animation: popIn 0.25s ease-out;
  text-align: center;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow-y: auto;
}
@keyframes popIn {
  from { opacity: 0; transform: translateX(-50%) translateY(8px) scale(0.9); }
  to   { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
}
.settings-window{width:100vw;height:100vh;background:transparent;color:#c8d0d8;display:flex;flex-direction:column;overflow:hidden}
.settings-titlebar{display:flex;align-items:center;justify-content:space-between;padding:6px 12px;flex-shrink:0;-webkit-app-region:drag;border-bottom:1px solid rgba(100,150,255,.12)}
.settings-titlebar span{font-size:13px;color:#aaaacc}
.titlebar-close{-webkit-app-region:no-drag;background:none;border:none;color:#7777aa;font-size:14px;cursor:pointer;padding:2px 6px;border-radius:4px;line-height:1}
.titlebar-close:hover{background:rgba(255,80,80,.25);color:#ff6666}
.settings-tabs{display:flex;border-bottom:1px solid rgba(100,150,255,.15);flex-shrink:0}
.settings-tabs button{flex:1;padding:10px 0;background:transparent;border:none;color:#7777aa;font-size:14px;cursor:pointer;border-bottom:2px solid transparent;transition:all .15s}
.settings-tabs button.active{color:#7cc8ff;border-bottom-color:#7cc8ff}
.settings-chat{flex:1;overflow-y:auto;padding:8px 12px;display:flex;flex-direction:column;gap:6px}
.settings-chat::-webkit-scrollbar{width:4px}
.settings-chat::-webkit-scrollbar-thumb{background:#444466;border-radius:2px}

.chat-input-bar{display:flex;gap:6px;padding:8px 12px;border-top:1px solid rgba(100,150,255,.15);flex-shrink:0;background:#16162a}
.chat-text-input{flex:1;padding:8px 12px;border-radius:8px;border:1px solid rgba(100,150,255,.2);background:#0d0d1a;color:#c8d0d8;font-size:13px;outline:none;font-family:inherit}
.chat-text-input:focus{border-color:rgba(100,150,255,.4)}
.chat-send-btn{padding:8px 16px;border-radius:8px;border:none;background:rgba(100,150,255,.2);color:#c0c0ee;cursor:pointer;font-size:13px;white-space:nowrap;transition:all .15s}
.chat-send-btn:hover{background:rgba(100,150,255,.35)}
.chat-send-btn:disabled{opacity:.4;cursor:default}
.chat-stop-btn{padding:8px 16px;border-radius:8px;border:none;background:rgba(255,100,100,.25);color:#ff8888;cursor:pointer;font-size:13px;white-space:nowrap;transition:all .15s}
.chat-stop-btn:hover{background:rgba(255,100,100,.4)}

.bubble{max-width:85%;padding:7px 12px;border-radius:14px;font-size:13px;line-height:1.5;word-break:break-word;flex-shrink:0}
.bubble.user{align-self:flex-end;background:#2979ff;color:white;border-bottom-right-radius:4px}
.bubble.pet{align-self:flex-start;background:#2e3440;color:#c8d0d8;border-bottom-left-radius:4px}
.cursor{animation:blink-cursor .8s infinite;color:#7cc8ff;font-weight:bold}
@keyframes blink-cursor{0%,100%{opacity:1}50%{opacity:0}}
.loading-bubble{padding:12px 16px}
.dots span{display:inline-block;width:6px;height:6px;background:#7cc8ff;border-radius:50%;margin:0 3px;animation:dot-bounce 1.2s infinite}
.dots span:nth-child(2){animation-delay:.2s}.dots span:nth-child(3){animation-delay:.4s}
@keyframes dot-bounce{0%,100%{transform:translateY(0);opacity:.3}50%{transform:translateY(-6px);opacity:1}}
.shortcut-current { font-size: 14px; color: #aaaacc; margin-bottom: 16px; }
.shortcut-capturing {
  font-size: 14px; color: #ffaa66; margin-bottom: 12px;
  animation: pulse-text 1s infinite;
}
.shortcut-new { font-size: 13px; color: #aacccc; margin-bottom: 16px; }
.shortcut-actions { display: flex; gap: 8px; justify-content: center; }
.shortcut-btn {
  padding: 8px 18px; border-radius: 8px;
  border: 1px solid rgba(100, 150, 255, 0.25);
  background: rgba(100, 150, 255, 0.1); color: #c0c0ee;
  cursor: pointer; font-size: 13px;
} 
.shortcut-btn:hover { background: rgba(100, 150, 255, 0.2); }
.shortcut-btn.primary {
  background: rgba(33, 150, 243, 0.3);
  border-color: rgba(33, 150, 243, 0.4); color: white;
}
@keyframes pulse-text {
  0%, 100% { opacity: 0.6; }
  50%      { opacity: 1; }
}
.key-badge {
  display: inline-block; background: rgba(100, 150, 255, 0.2);
  color: #7cc8ff; padding: 2px 8px; border-radius: 6px;
  font-family: monospace; font-size: 13px;
}
</style>
