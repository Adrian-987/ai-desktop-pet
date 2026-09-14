# Live2D 模型资产

本目录包含桌宠使用的 Live2D 模型。当前只保留**伊蕾娜（LSS）**一个模型。

```
public/
└── live2d/
    ├── live2dcubismcore.min.js    # Live2D Cubism Core 运行时（必需）
    └── LSS/                        # 伊蕾娜
        ├── LSS.moc3
        ├── LSS.model3.json
        ├── LSS.physics3.json
        ├── LSS.cdi3.json
        └── LSS.4096/texture_00.png
```

模型路径由 `src/live2d/config.ts` 指定：

```ts
export const modelConfig = {
  modelName: "LSS",
  modelFile: "LSS.model3.json",
  // ...
}
```

## 已移除的模型

以下模型因**授权明确禁止二次分发**，已从项目中删除，不随仓库分发：

| 模型 | 来源 | 移除原因 |
|------|------|----------|
| 柚叶（Thelema2Yuzuha0802） | 《绝区零》同人模型 | 授权文件注明「禁止二次配布、禁止在非 Bilibili 平台使用」，版权归 miHoYo |
| MousseJiu1.0 | 紫发角色同人模型 | 非本项目使用角色，且无授权说明 |

## 注意事项

- **本仓库为私有仓库**。伊蕾娜模型含角色版权（《魔女之旅》），且为同人创作，
  请勿公开分发或用于商业用途。
- `live2dcubismcore.min.js` 是 Live2D Inc. 的官方 Cubism Core SDK，
  使用需遵守 [Live2D 软件使用许可](https://www.live2d.com/download/cubism-sdk/)。
- 想换成可自由分发的模型时，可使用 Live2D 官方示例模型（如 Hiyori、Haru），
  修改 `src/live2d/config.ts` 即可切换。
