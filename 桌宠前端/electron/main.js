const { app, BrowserWindow, ipcMain, globalShortcut, Tray, Menu, nativeImage, screen } = require("electron")
const fs = require("fs")
const path = require("path")

// 从已关闭的开发终端启动时，忽略日志管道关闭造成的 EPIPE，避免主进程退出。
function ignoreBrokenConsolePipe(stream) {
  if (!stream) return
  stream.on("error", (error) => {
    if (error?.code !== "EPIPE") throw error
  })
}
ignoreBrokenConsolePipe(process.stdout)
ignoreBrokenConsolePipe(process.stderr)

// Config file for user preferences
const configPath = path.join(app.getPath("userData"), "cyberpet-config.json")
function loadConfig() {
  try { return JSON.parse(fs.readFileSync(configPath, "utf8")) } catch { return {} }
}
function saveConfig(cfg) { fs.writeFileSync(configPath, JSON.stringify(cfg, null, 2)) }

// Shared conversation store (pet window writes, settings window reads)
let conversation = []

let mainWin = null, settingsWin = null, tray = null, currentShortcut = null
let cursorTimer = null
let petMouseCaptured = false

const isDev = process.env.NODE_ENV === "dev" || process.argv.includes("--dev")

function registerShortcut(shortcut) {
  if (currentShortcut) globalShortcut.unregister(currentShortcut)
  try {
    globalShortcut.register(shortcut, () => {
      if (mainWin && !mainWin.isDestroyed()) {
        mainWin.show()
        mainWin.webContents.send("voice-toggle")
      }
    })
    currentShortcut = shortcut
    console.log("Shortcut registered:", shortcut)
  } catch (e) {
    console.error("Failed to register shortcut:", shortcut, e.message)
  }
}

function loadPage(win, hash) {
  const url = isDev ? "http://localhost:5173" : `file://${path.join(__dirname, "..", "dist", "index.html")}`
  win.loadURL(url + (hash || ""))
}

function openSettings() {
  if (settingsWin && !settingsWin.isDestroyed()) {
    settingsWin.show()
    settingsWin.focus()
    return
  }
  settingsWin = new BrowserWindow({
    width: 420, height: 580,
    frame: false,
    autoHideMenuBar: true,
    webPreferences: { nodeIntegration: true, contextIsolation: false }
  })
  settingsWin.setMenu(null)
  settingsWin.setBackgroundColor("#1a1a2e")
  loadPage(settingsWin, "#settings")
  settingsWin.on("close", (e) => {
    e.preventDefault()
    settingsWin.hide()
  })
  settingsWin.on("closed", () => {
    settingsWin = null
  })
}

function createTray() {
  const iconPath = path.join(__dirname, "tray-icon.png")
  const icon = nativeImage.createFromPath(iconPath)
  tray = new Tray(icon.resize({ width: 16, height: 16 }))
  const menu = Menu.buildFromTemplate([
    { label: "打开设置", click: openSettings },
    { type: "separator" },
    { label: "退出", click: () => { app.quit() } }
  ])
  tray.setContextMenu(menu)
  tray.setToolTip("CyberPet")
  tray.on("double-click", openSettings)
}

function createWindow() {
  mainWin = new BrowserWindow({
    width: 300, height: 320,
    transparent: true, frame: true, thickFrame: false,
    alwaysOnTop: true, resizable: false,
    skipTaskbar: true, title: "",
    type: "toolbar",
    focusable: false,
    titleBarStyle: "hidden",
    acceptFirstMouse: true,
    hasShadow: false,
    backgroundColor: "#00000000",
    webPreferences: { nodeIntegration: true, contextIsolation: false }
  })

  loadPage(mainWin, "")

  // 全局读取鼠标位置，不受宠物窗口拖拽层影响
  cursorTimer = setInterval(() => {
    if (!mainWin || mainWin.isDestroyed()) return
    const point = screen.getCursorScreenPoint()
    const bounds = mainWin.getBounds()
    const localX = point.x - bounds.x
    const localY = point.y - bounds.y
    mainWin.webContents.send("global-mousemove", localX, localY)

    // 仅在角色附近接收鼠标，透明空白区域穿透到底层窗口。
    const hitboxLeft = Math.round(bounds.width * 0.22)
    const hitboxRight = Math.round(bounds.width * 0.78)
    const hitboxTop = Math.round(bounds.height * 0.22)
    const overPet = localX >= hitboxLeft && localX <= hitboxRight
      && localY >= hitboxTop && localY <= bounds.height - 5
    if (overPet !== petMouseCaptured) {
      petMouseCaptured = overPet
      mainWin.setIgnoreMouseEvents(!overPet, { forward: true })
    }
  }, 16)



  mainWin.setAlwaysOnTop(true, "screen-saver")
  mainWin.setVisibleOnAllWorkspaces(true)
  mainWin.setBackgroundColor("#00000000")
  mainWin.setMenu(null)

  mainWin.on("focus", () => {
    if (process.platform === "win32") {
      mainWin.setBackgroundColor("#00000000")
    }
  })
  mainWin.on("blur", () => {
    if (process.platform === "win32") {
      mainWin.setBackgroundColor("#00000000")
        }
  })
  mainWin.on("show", () => {
    if (process.platform === "win32") {
      mainWin.setBackgroundColor("#00000000")
    }
  })
  mainWin.on("resize", () => {
    if (process.platform === "win32") {
      mainWin.setBackgroundColor("#00000000")
    }
  })
  mainWin.on("page-title-updated", (e) => e.preventDefault())

  // Load saved shortcut or use default
  const cfg = loadConfig()
  const sc = cfg.shortcut || "Ctrl+Alt+L"
  registerShortcut(sc)

  createTray()
}

app.whenReady().then(createWindow)

ipcMain.on("close-window", () => { globalShortcut.unregisterAll(); app.quit() })

ipcMain.on("resize-pet-window", (event, w, h) => {
  const win = BrowserWindow.fromWebContents(event.sender)
  if (win) win.setBounds({ width: w, height: h })
})

ipcMain.on("move-window", (event, x, y) => {
  const win = BrowserWindow.fromWebContents(event.sender)
  if (win) {
    const [wx, wy] = win.getPosition()
    const [width, height] = win.getSize()
    win.setBounds({ x: Math.round(wx + x), y: Math.round(wy + y), width, height })
  }
})

// Shortcut IPC
ipcMain.on("get-shortcut", (event) => {
  const cfg = loadConfig()
  event.returnValue = cfg.shortcut || "Ctrl+Alt+L"
})

ipcMain.on("set-shortcut", (event, newShortcut) => {
  const cfg = loadConfig()
  cfg.shortcut = newShortcut
  saveConfig(cfg)
  registerShortcut(newShortcut)
})

// Conversation IPC - broadcast to other window
ipcMain.on("conv-update", (_event, msgs) => {
  conversation = msgs
  // Forward to settings window if pet sent it
  if (settingsWin && !settingsWin.isDestroyed()) {
    settingsWin.webContents.send("conv-update", conversation)
  }
  // Forward to pet window if settings sent it
  if (mainWin && !mainWin.isDestroyed()) {
    mainWin.webContents.send("conv-update", conversation)
  }
})

ipcMain.on("get-conversation", (event) => {
  event.returnValue = conversation
})

app.on("will-quit", () => {
  globalShortcut.unregisterAll()
  if (cursorTimer) clearInterval(cursorTimer)
})
app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit() })
