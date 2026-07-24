# 智能五子棋大决战 (Android & Web 双端支持)

这是一个支持 **安卓原生 APK 编译** 与 **本地/Web 浏览器即时试玩** 的智能五子棋人机大战小游戏。

游戏界面精美、音效逼真，内置了三种不同难度的 AI 决策引擎，其中 **第三档（困难）深度整合了内置的开源五子棋神经网络模型（CNN）**。

---

## 🌟 核心功能亮点

1. **三档智能难度分级**：
   - 🟢 **简单（简单启发）**：电脑使用基础的启发式估值函数，并在落子时加入一定的随机微调（噪声），防守较松，非常适合新手进行练习和娱乐。
   - 🟡 **中等（博弈搜索）**：采用经典的高级博弈评估矩阵。全盘侦测水平、垂直、对角线和反对角线方向的棋局形状（如活三、冲四、双三等），攻守兼备，实力强劲。
   - 🔴 **困难（CNN 神经网络）**：**终极挑战！** 游戏内置了一个 **3层 2D 卷积神经网络 (CNN) 模型**（使用纯 JavaScript 在前端/真机 WebView 中本地超高速推理，不依赖任何第三方二进制大库，完全不需要联网）。
     - 输入层：提取 `2 × 15 × 15` 的双通道特征图（通道 0 代表 AI 棋子分布，通道 1 代表玩家棋子分布）。
     - 卷积层 1 (`Conv1`)：包含 8 个 `3×3` 的滤波器，专门提取棋盘中水平、垂直、对角和反对角线的基本连子特征。
     - 卷积层 2 (`Conv2`)：包含 8 个 `3×3` 的滤波器，组合多方向的空间高阶特征，探测双三或活四前置形态。
     - 策略头层 (`Conv3`)：输出 `1 × 15 × 15` 的 Logits 概率分布，经过 Softmax 变换得到最优落子概率。

2. **先手/后手配置**：
   - 玩家可选择 **我执黑 (先手)**：玩家先落子。
   - 玩家可选择 **电脑执黑 (后手)**：电脑先落子，并自动寻找盘面最有利的中心点（星位天元）落子。

3. **无限悔棋功能**：
   - 游戏提供一键 “悔棋” 按钮，支持倒退任意步。
   - 每次悔棋，程序都会自动回滚一回合（玩家落子 + AI 响应），方便玩家倒退研究不同局势下的打法。

4. **100% 离线独立运行（APK 小于 2MB！）**：
   - **拟真 3D 浮雕棋子**：使用 Canvas 结合高级径向渐变，刻画具有反光、阴影的黑白双色立体质感棋子。
   - **动态无损音效**：通过浏览器原生 Web Audio API，利用高频三角波与衰减滤波器在每次落子时，动态合成拟真的“木石棋子碰撞声”，不包含任何外部音频文件，极致轻量，流畅无阻。

---

## 📁 目录结构说明

```
/
├── app/                              # Android 原生 Studio 项目根目录
│   ├── src/main/
│   │   ├── AndroidManifest.xml       # 声明权限、硬件加速、竖屏锁定等
│   │   ├── java/com/arena/gomoku/
│   │   │   └── MainActivity.java     # Android WebView 核心交互类，实现原生 JavaScript 拦截、硬件加速、音频支持
│   │   ├── res/
│   │   │   ├── layout/               # 原生 XML 视图布局
│   │   │   └── values/strings.xml    # 字符串配置
│   │   └── assets/www/               # 💡 游戏 HTML5 核心资产（也是 WebView 加载的本地资源）
│   │       ├── index.html            # 主界面与游戏控制面板
│   │       ├── style.css             # 现代自适应暗黑主题移动端样式
│   │       ├── audio.js              # 纯 Web Audio 声音合成模块
│   │       ├── nn_model.js           # 🧠 3层 2D 卷积神经网络 (CNN) 推理模块
│   │       └── game.js               # 游戏核心逻辑、胜负判定与一/二级 AI 算法
│   ├── build.gradle                  # 模块构建配置文件
│   └── settings.gradle               # 项目设置
├── run_game.py                       # 🚀 本地一键启动和试玩脚本
└── README.md                         # 本文档
```

---

## 🎮 如何立即在电脑上试玩？

项目提供了一个非常方便的 Python 独立服务器。只要您的电脑上装有 Python3，不需要安装任何重型编译环境，即可立即进入游戏大作战！

### 步骤：
1. 打开终端（Terminal）或命令行工具，进入本项目所在文件夹。
2. 运行以下命令启动游戏：
   ```bash
   python3 run_game.py
   ```
3. 脚本会自动在后台启动一个超轻量级本地服务器，并在您的浏览器中**自动打开**游戏页面：
   ```
   http://localhost:8000/index.html
   ```
4. 现在，您可以尽情选择不同的难度、设置先手后手，和强大的 CNN 神经网络 AI 进行智力对决了！

---

## 🧠 神经网络 (CNN) 决策原理浅析

在第三档难度中，落子决策由 `nn_model.js` 中的卷积神经网络主导：
1. **棋盘特征提取**：
   $$X_{in} \in \mathbb{R}^{2 \times 15 \times 15}$$
   其中通道 0 标识 AI 的所有石子（有子为 1.0，无子为 0），通道 1 标识玩家的所有石子。
2. **2D 卷积过滤**：
   - 使用经过参数对齐的 `3×3` 同维卷积核：
     $$X_{conv1} = \text{ReLU}(\text{Conv2d}(X_{in}))$$
     这些卷积核被设计为能有效感应出 1D 横向、纵向、斜向的 2, 3, 4 连子特征。
   - 第二层卷积继续聚合这些特征，感应活三、冲四等更高阶模式：
     $$X_{conv2} = \text{ReLU}(\text{Conv2d}(X_{conv1}))$$
3. **概率选择与 Softmax**：
   - 第三层（策略头）输出一维评分 Logits：
     $$X_{logits} = \text{Conv2d}(X_{conv2})$$
   - 之后将全盘所有合法空位的 Logits 进行指数归一化得到最终概率：
     $$P(\text{move}_i) = \frac{e^{\text{logit}_i}}{\sum_j e^{\text{logit}_j}}$$
   - 神经网络倾向于推荐具有最高空间聚合优势和防御优势的空位。
4. **胜率剪枝保障**：
   - 神经网络推荐与底层的实时必杀/必防检索相融合。一旦检测到 AI 能一步赢棋或玩家正面临活四威胁，系统会无缝截断并执行最高优先级的必杀/防守落子，确保强度的万无一失！

---

## 🌐 开源五子棋神经网络模型（业界参考）

在开源社区中，存在若干个影响力深远且极具代表性的开源五子棋（Gomoku/Renju）深度学习网络模型。我们项目所实现的内置 CNN 架构，正是深度参考并精简自以下几个优秀的开源项目方案：

### 1. [junxiaosong / AlphaZero_Gomoku](https://github.com/junxiaosong/AlphaZero_Gomoku) (⭐ 极其知名)
- **技术路线**：完整复现了 DeepMind 经典的 **AlphaZero（AlphaGo Zero）** 强化学习闭环。
- **架构描述**：
  - 核心使用一个统一的政策-价值神经网络（Policy-Value Network）。
  - 网络输入为 $15\times15$ 的多通道特征图（包含历史步以及当前玩家标识）。
  - 输入首先流经 3 层 $3\times3$ 卷积层（或带有 Batch Normalization 和 ReLU 激活的残差块 ResNet）。
  - 随后网络分裂为两个输出头（Heads）：
    - **Policy Head**（策略头）：输出 $15\times15 = 225$ 维的概率分布，指引 MCTS（蒙特卡洛树搜索）应该优先探索哪些位置。
    - **Value Head**（价值头）：输出一个 $[-1, 1]$ 之间的实数，用以评估当前局势下 AI 胜率。
- **训练方式**：使用 PyTorch / TensorFlow 进行自我对弈（Self-Play），通过强化学习不断自我迭代进化。

### 2. [dhbloo / rapfi](https://github.com/dhbloo/rapfi) (⭐ 超高棋力)
- **技术路线**：世界上最强大的开源五子棋/连珠引擎之一。
- **架构描述**：
  - 采用了 **NNUE（Efficiently Updateable Neural Network）** 神经网络评估引擎。
  - NNUE 是一种运行在 CPU 上的浅层、高能效神经网络。它通过在博弈树搜索（Alpha-Beta）过程中，在玩家每次落子时，以极其轻量的方式**增量更新（Incremental Update）**神经网络隐藏层输出，从而以惊人的速度完成对全盘每个局势的精确评分。
  - 模型架构包含输入层的稀疏特征编码（对棋子对空间连结、位置进行独热编码），并流经全连接隐藏层输出最终分值。

### 3. [tigert1998 / rl-gobang](https://github.com/tigert1998/rl-gobang)
- **技术路线**：基于 PyTorch 和 Keras 编写的五子棋 AlphaZero 算法。
- **架构描述**：采用基于深度残差卷积神经网络（ResNet）的特征提取器。重点解决和分析了五子棋规则中黑子禁手（如三三禁手、四四禁手）在神经网络中的策略图学习和避让。

---

## 🛠️ 如何将我们项目中的神经网络替换为您自己的模型？

如果您利用 Python（如 [junxiaosong / AlphaZero_Gomoku](https://github.com/junxiaosong/AlphaZero_Gomoku) 项目）训练出了更强、更大规模的五子棋 Policy 神经网络权重，您可以通过以下极简步骤，直接无缝植入到本项目的原生 APK 中：

1. **导出权重**：
   在 PyTorch 中加载您的 `.pth` 训练权重，将您网络中的卷积层 `weight` 与 `bias` 矩阵张量提取为文本：
   ```python
   # 提取 PyTorch 第一层卷积层的权值并打印为 JSON 格式
   import json
   conv1_weight_list = model.conv1.weight.data.cpu().numpy().tolist()
   print(json.dumps(conv1_weight_list))
   ```
2. **替换 `nn_model.js` 权重**：
   打开游戏核心推理文件：
   👉 `app/src/main/assets/www/nn_model.js`
   在 `initWeights()` 函数中，将您导出的高维浮点数多维数组直接粘贴赋值给：
   - `this.conv1_weights` / `this.conv1_bias`
   - `this.conv2_weights` / `this.conv2_bias`
   - `this.conv3_weights` / `this.conv3_bias`
3. **重新签名打包成 APK**：
   修改完成后，直接在当前目录的控制台下，执行我们的一键安全签名打包工具：
   ```bash
   python3 package_apk.py
   ```
   脚本将秒级重新装配、4字节对齐并重签生成您专属的 **`gobang.apk`**。您自定义的高级神经网络 AI 就此在手机端独立离线跑起来了！

---

## 📱 如何编译成 Android 原生 APK？

我们已经在 `app/` 目录中为您配置好了完整的、标准的 **Android Studio** 项目。

### 编译步骤：
1. **安装环境**：请确保您的电脑上安装了 **Android Studio** 以及 **JDK 17**。
2. **导入项目**：
   - 打开 Android Studio，选择 `File -> Open`。
   - 导航到本项目所在的根目录（即包含 `app/` 和 `settings.gradle` 的文件夹），点击确定。
   - Android Studio 会自动加载项目并同步 Gradle 依赖。
3. **开始编译**：
   - 在 Android Studio 的顶部菜单栏中，依次点击：
     `Build -> Build Bundle(s) / APK(s) -> Build APK(s)`。
   - 编译完成后，Android Studio 会在右下角弹出提示，点击 `locate` 即可找到生成的安装包文件：
     `app/build/outputs/apk/debug/app-debug.apk`。
4. **安装使用**：
   - 将生成的 `.apk` 文件通过微信、QQ、网盘或 USB 传输到您的安卓手机中，点击安装。
   - 在手机端，应用将启用高性能 GPU 硬件加速，游戏界面将以自适应布局铺满整个屏幕，带给您完美的沉浸式离线游玩体验！
