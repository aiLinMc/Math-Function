# Math Function

**Bring mathematics to life in Minecraft with function-powered projectiles and graphs!**

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[![GitHub Release](https://img.shields.io/github/v/release/aiLinMc/Math-Function)](https://github.com/aiLinMc/Math-Function/releases)
[![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/aiLinMc/Math-Function)](https://github.com/aiLinMc/Math-Function)

---

## 📖 Introduction

Math Function is a **Minecraft NeoForge mod** that transforms mathematical expressions into interactive in-game experiences. Whether you want to visualize functions as block graphs or launch projectiles along custom curves, this mod brings the beauty of math directly into your world.

### Two Core Features in One Mod:

**1. Block Graph Generation** – Visualize any function as a 3D block structure using a simple command.

**2. Function Enchantment** – Apply custom mathematical trajectories to weapons and tools, turning arrows, TNT, fireworks, and even your own flight into controllable function curves!

### Supported Functions
| Category | Functions |
|----------|-----------|
| Basic Arithmetic | `+` `-` `*` `/` |
| Power | `^` (e.g. `x^2`) |
| Unary Operator | `-x` (negative numbers) |
| Trigonometry | `sin(x)` `cos(x)` `tan(x)` |
| Logarithms | `log(x)` `ln(x)` `log(base, value)` `log2` `log10` |
| Roots | `sqrt(x)` `sqrt(root, value)` (e.g. `sqrt(3, 27)` = cube root) |
| Rounding | `floor(x)` (round down), `ceil(x)` (round up), `round(x)` (round to nearest), `trunc(x)` (remove decimal) |
| Other | `abs(x)` `exp(x)` `min(a, b)` `max(a, b)` `mod(a, b)` |
| Random | `Ran#` (0~1) `RanInt(min, max)` |
| Constants | `e` `pi` |
| Nesting | `3*x^2 + sin(2*x)` |

---

## 🧪 Block Graph Command
```
/fx "<expression>" <min_x> <max_x> <block> [scale] [mode] [coordinateSystem]
```

**Parameters**

| Parameter           | Description                                                                 |
|---------------------|-----------------------------------------------------------------------------|
| `expression`        | Math expression in terms of `x` (e.g. `"x^2 + 3*x - 5"`). Supports `+ - * / ^ sin cos tan sqrt log ln`. |
| `min_x`, `max_x`    | Domain of the function (x values).                                          |
| `block`             | Block to build the graph (e.g. `minecraft:red_concrete`).                  |
| `scale` (optional)  | How many blocks represent one unit (default = 1, range 1–10).              |
| `mode`  (optional)  | `stand` – vertical graph (Y = f(x)), `floor` – horizontal graph (Z = f(x)).|
| `coordinateSystem` (optional) | `true` / `false` – whether to generate a quartz coordinate system (origin at (0,0), shifted 1 block left/down). |

**Example**
```
/fx "x^2" -5 5 minecraft:blue_wool 2 stand true
```
> Creates a blue wool parabola from x=-5 to x=5, each unit = 2 blocks, with a coordinate system.

---

## ⚔️ Function Enchantment

Rename any compatible item to `f(x)=expression` or `y=expression` (e.g. `f(x)=x^2`) and the enchantment will activate automatically. No command required – just rename the item in an anvil!

### Compatible Items & Behaviors

| Item | Effect |
|------|--------|
| **Bow / Crossbow** | Arrows follow the function curve y = f(x), where x is horizontal distance and y is height relative to launch point. |
| **Flint and Steel** | Lit TNT flies along the function curve before exploding. |
| **Firework Rocket** | Fireworks glide along the curve and explode upon hitting blocks or entities. |
| **Trident (with Riptide)** | Player flies along the function curve during water/rain dash – press jump key to stop anytime! |

### Example Usage
1. Get a bow and enchant it with the **Math Function** enchantment or obtain it via commands.
2. Rename the bow in an anvil to `f(x)=sin(x)*2`.
3. Shoot an arrow – it will fly in a beautiful sine wave!
4. For TNT: rename flint and steel to `y=cos(x)` and ignite TNT.
5. For tridents: rename a riptide trident to `f(x)=x^2/10` and dash through water!

### Technical Details
- Initial height automatically set to f(0), ensuring constant functions work correctly.
- Real-time numerical differentiation computes tangent velocity for smooth motion.
- Collision detection prevents projectiles from going through blocks.
- Cleanly reverts to vanilla physics when out of range, duration, or upon impact.
- Supported since version 2.0, continuously improved with each release.

---

## ✨ Features

- **Real‑time expression parsing** – Fast and reliable, no external libraries.
- **Continuous graph lines** – Bresenham’s line algorithm fills gaps between sample points.
- **Automatic discontinuity handling** – Breaks at invalid points (e.g. `1/x` near 0, `tan(x)` at asymptotes).
- **Axis‑aligned placement** – Graph extends along the nearest cardinal direction based on where you look.
- **Projectile trajectory** – Full function-based flight with particle trails and collision.
- **Multi-shot compatibility** – Arrows spread slightly when using the Multishot enchantment.
- **Safe by default** – Prevents rendering outside world height or exceeding performance limits.
- **Player feedback** – Chat messages show expression, block name, execution time and origin coordinates.

---

## 🧩 Requirements

- **Minecraft**: 1.21.1 (NeoForge)
- **NeoForge**: 21.1.176 or later

---

## 📦 Installation

1. Download the latest JAR from [Modrinth](https://modrinth.com/mod/math-function/versions) or [GitHub Releases](https://github.com/aiLinMc/Math-Function/releases).
2. Place it into your `mods` folder.
3. Launch Minecraft with NeoForge installed.
4. Start building graphs or launching function-powered projectiles!

---

## ⚖️ License

This mod is released under the **GNU General Public License v3.0**.  
You may freely use, modify, and redistribute it, provided you share your changes under the same license.  
See the [LICENSE](https://github.com/aiLinMc/Math-Function/blob/main/LICENSE) file for details.

---

## 🔗 Links

- [GitHub Repository](https://github.com/aiLinMc/Math-Function)
- [Issue Tracker](https://github.com/aiLinMc/Math-Function/issues)
- Releases: [GitHub](https://github.com/aiLinMc/Math-Function/releases), [Modrinth](https://modrinth.com/mod/math-function/versions)

---

<br>

# 中文介绍

## 📖 概述

**Math Function** 是一个 Minecraft NeoForge 模组，将数学表达式转化为游戏中的交互体验。无论是用方块生成函数图像，还是让弹射物沿自定义曲线飞行，这个模组都能让数学的美丽直接展现在你的世界里。

### 一个模组，两大核心功能：

**1. 方块图像生成** – 通过简单的命令，将任意函数可视化为 3D 方块结构。

**2. 函数附魔** – 为武器和工具赋予数学轨迹，让箭矢、TNT、烟花火箭甚至你自身的飞行都变成可控的函数曲线！

### 支持的函数
| 类别 | 函数 |
|------|------|
| 基础运算 | `+` `-` `*` `/` |
| 幂运算 | `^`（如 `x^2`） |
| 一元负号 | `-x`（负数） |
| 三角函数 | `sin(x)` `cos(x)` `tan(x)` |
| 对数 | `log(x)` `ln(x)` `log(底数, 真数)` `log2` `log10` |
| 根号 | `sqrt(x)` `sqrt(根指数, 被开方数)`（如 `sqrt(3, 27)` = 27 的立方根） |
| 取整 | `floor(x)`（向下取整），`ceil(x)`（向上取整），`round(x)`（四舍五入），`trunc(x)`（截断小数） |
| 其他 | `abs(x)` `exp(x)` `min(a, b)` `max(a, b)` `mod(a, b)` |
| 随机数 | `Ran#`（0~1 随机小数）`RanInt(min, max)`（指定范围随机整数） |
| 常数 | `e` `pi` |
| 嵌套 | `3*x^2 + sin(2*x)` |

---

## 🧪 方块图像命令
```
/fx "<表达式>" <最小值_x> <最大值_x> <方块> [缩放] [模式] [坐标系]
```

**参数说明**

| 参数                 | 说明                                                                 |
|----------------------|----------------------------------------------------------------------|
| `表达式`             | 以 `x` 为自变量的数学表达式，支持 `+ - * / ^ sin cos tan sqrt log ln` |
| `最小值_x`，`最大值_x` | 定义域（x 的取值范围）                                                |
| `方块`               | 构建图像所用的方块（如 `minecraft:red_concrete`）                     |
| `缩放`（可选）        | 1 个单位长度对应多少格，默认 1，范围 1~10                             |
| `模式`（可选）       | `stand` – 竖立图像（Y = f(x)），`floor` – 平铺图像（Z = f(x)）        |
| `坐标系`（可选）       | `true` / `false`，是否生成石英块坐标轴（原点位于 (0,0)，向左/下偏移 1 格） |

**示例**
```
/fx "x^2" -5 5 minecraft:blue_wool 2 stand true
```
> 生成从 x=-5 到 x=5 的蓝色羊毛抛物线，每单位长度 = 2 格，并显示坐标系。

---

## ⚔️ 函数附魔

将任意兼容物品重命名为 `f(x)=表达式` 或 `y=表达式`（例如 `f(x)=x^2`），附魔会自动激活。无需命令，只需在铁砧中重命名即可！

### 兼容物品与效果

| 物品 | 效果 |
|------|------|
| **弓 / 弩** | 箭矢沿函数曲线 y = f(x) 飞行，其中 x 为水平距离，y 为相对发射点的高度。 |
| **打火石** | 点燃的 TNT 沿函数曲线飞行，碰撞后爆炸。 |
| **烟花火箭** | 烟花沿曲线滑翔，碰到方块或实体时爆炸。 |
| **三叉戟（激流附魔）** | 玩家在水中/雨中冲刺时沿函数曲线飞行 – 按下跳跃键可随时停止！ |
| **其他弹射物（如雪球、鸡蛋等）** | 同样支持函数轨迹，重命名对应物品即可！ |

### 使用示例
1. 获取一把弓，并为其附魔**函数**或通过命令获取。
2. 在铁砧中将弓重命名为 `f(x)=sin(x)*2`。
3. 射出箭矢 – 它将沿美丽的正弦波飞行！
4. TNT 用法：将打火石重命名为 `y=cos(x)`，然后点燃 TNT。
5. 三叉戟用法：将带有激流附魔的三叉戟重命名为 `f(x)=x^2/10`，在水中冲刺！
6. 雪球用法：将雪球重命名为 `f(x)=sqrt(25-x^2)`，扔出后它会沿半圆形轨迹飞行！

### 技术细节
- 起始高度自动设为 f(0)，确保常函数也能正常工作。
- 实时数值微分计算切线速度，实现平滑运动。
- 碰撞检测防止弹射物穿透方块。
- 超出范围、超时或撞击后自动恢复原版物理。
- 自 2.0 版本起支持，持续迭代优化。

---

## ✨ 特色功能

- **实时表达式解析** – 快速可靠，无需外部库。
- **连续曲线图像** – 使用 Bresenham 直线算法填充采样点间隙。
- **自动处理间断点** – 遇到无效点（如 `1/x` 在 0 附近、`tan(x)` 的渐近线）自动断开。
- **沿轴向放置** – 图像根据玩家面朝方向自动对齐东南西北。
- **弹射物轨迹** – 完整的函数飞行，带有粒子尾迹和碰撞处理。
- **多重射击兼容** – 使用多重射击附魔时箭矢会略微分散。
- **默认安全机制** – 限制超出世界高度或性能开销过大的情况。
- **玩家反馈** – 聊天栏显示表达式、方块名称、执行耗时和原点坐标。

---

## 🧩 运行要求

- **Minecraft 版本**：1.21.1（NeoForge）
- **NeoForge 版本**：21.1.176 或更高

---

## 📦 安装方法

1. 从 [Modrinth](https://modrinth.com/mod/math-function/versions) 或 [GitHub Releases](https://github.com/aiLinMc/Math-Function/releases) 下载最新 JAR 文件。
2. 放入你的 Minecraft `mods` 文件夹。
3. 确保已安装 NeoForge 并启动游戏。
4. 开始生成函数图像或发射函数弹射物！

---

## ⚖️ 法律信息

本模组采用 **GNU 通用公共许可证 v3.0** 发布。  
你可以自由使用、修改和重新分发，但必须同样以 GPLv3 许可证公开你的修改。  
详见 [LICENSE](https://github.com/aiLinMc/Math-Function/blob/main/LICENSE) 文件。

---

## 🔗 相关链接

- [GitHub 仓库](https://github.com/aiLinMc/Math-Function)
- [问题反馈](https://github.com/aiLinMc/Math-Function/issues)
- 版本发布：[GitHub](https://github.com/aiLinMc/Math-Function/releases)，[Modrinth](https://modrinth.com/mod/math-function/versions)