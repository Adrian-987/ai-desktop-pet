# Live2D 模型资产说明

本目录下的 Live2D 模型**未包含在 Git 仓库中**，原因是模型作者有明确的授权限制，不能二次分发。

## 为什么不在仓库里

`Thelema2Yuzuha0802/注意事项.txt` 原文规定：

> 1. 本模型的版权归属于 miHoYo；
> 2. 本模型可以用于制作绝区零相关的视频，不可用于政治宣传等东西，使用请符合官方二创规定；
> 3. **禁止用于直播盈利，禁止二次配布，禁止在非 Bilibili 平台使用该模型**；
> 4. 模型使用者有任何违反法律法规的行为、违反相关平台规则等，模型制作者概不负责，一切后果由使用者自行承担。

把模型提交到 GitHub 属于「二次配布」，且 GitHub 属于「非 Bilibili 平台」，因此本项目不随仓库分发这些文件。

`live2dcubismcore.min.js` 是 Live2D Inc. 的官方 Cubism Core SDK，同样有再分发限制，一并排除。

## 本地运行需要放哪些文件

从模型作者处合法获取后，按下述结构放入本目录（文件夹名必须一致）：

```
public/
├── live2d/
│   ├── live2dcubismcore.min.js      # Live2D Cubism Core（需自行从 Live2D 官网获取）
│   ├── LSS/                          # 模型 A
│   │   ├── LSS.moc3
│   │   ├── LSS.model3.json
│   │   ├── LSS.physics3.json
│   │   ├── LSS.cdi3.json
│   │   └── LSS.4096/texture_*.png
│   ├── MousseJiu1.0/                 # 模型 B
│   └── Thelema2Yuzuha0802/           # 模型 C
└── Thelema2Yuzuha0802/               # 默认加载的模型
```

模型路径由 `src/live2d/config.ts` 指定，换成自己的模型时改这里即可。

## 想换成可自由分发的模型

建议使用 Live2D 官方的示例模型（如 Hiyori、Haru 等），它们允许在遵守 Live2D 软件授权条款的前提下使用与分发，这样整个项目就能完整克隆即用。
