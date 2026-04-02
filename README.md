# Math Function

**Let you use a command to build a function graph with blocks in Minecraft**

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[![GitHub Release](https://img.shields.io/github/v/release/aiLinMc/Math-Function)](https://github.com/aiLinMc/Math-Function/releases)
[![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/aiLinMc/Math-Function)](https://github.com/aiLinMc/Math-Function)

---

## 📖 Introduction

Math Function is a **Minecraft NeoForge mod** that allows you to visualize mathematical functions directly in your world.  
By using a simple command, you can generate a graph made of blocks – perfect for teaching, building, or just having fun with math.

- **Dynamic rendering**: Enter any expression (e.g. `x^2`, `sin(x)`, `sqrt(200-x^2)`) and see the curve appear.
- **Two display modes**: `stand` – vertical wall‑like graph; `floor` – horizontal graph on the ground.
- **Customizable**: Choose the block type, scaling factor (unit length in blocks), and even an optional coordinate system.
- **Performance safe**: Automatic limiting of Y‑range and vertical span to prevent lag or crashes.
- **Localized feedback**: Messages in your game language (English/Chinese supported).

---

## 🧪 Command Syntax
```
/fx "<expression>" <min_x> <max_x> <block> [scale] [mode] [coordinateSystem]
```

**Parameters**

| Parameter           | Description                                                                 |
|---------------------|-----------------------------------------------------------------------------|
| `expression`        | Mathematical expression in terms of `x` (e.g. `"x^2 + 3*x - 5"`). Supports `+ - * / ^ sin cos tan sqrt log ln`. |
| `min_x`, `max_x`    | Domain of the function (values of `x`).                                     |
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

## ✨ Features

- **Real‑time expression parsing** – No external libraries, fast and reliable.
- **Continuous lines** – Uses Bresenham’s line algorithm to fill gaps between sample points.
- **Automatic discontinuity handling** – Breaks the line at invalid points (e.g. `1/x` near 0, `tan(x)` at asymptotes).
- **Axis‑aligned placement** – The graph extends along the nearest cardinal direction (N/S/E/W) based on where you look.
- **Safe by default** – Prevents rendering outside world height limits or over huge vertical jumps.
- **Player feedback** – Chat message shows expression, block name, execution time and origin coordinates.

---

## 🧩 Requirements

- **Minecraft**: 1.21.1 (NeoForge)
- **NeoForge**: 21.1.176 or later

---

## 📦 Installation

1. Download the latest JAR from the [Modrinth Version](https://modrinth.com/mod/math-function/versions) or [GitHub Releases](https://github.com/aiLinMc/Math-Function/releases) page.
2. Place it into your `mods` folder.
3. Launch Minecraft with NeoForge installed.
4. Enjoy building graphs!

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

**Math Function** 是一个 Minecraft NeoForge 模组，让你可以直接在游戏世界中用方块生成函数图像。  
只需一条命令，就能把数学公式变成实体的方块曲线 —— 无论是教学、装饰还是纯粹玩数学，都非常有趣。

- **动态解析**：输入任意表达式（例如 `x^2`、`sin(x)`、`sqrt(200-x^2)`），曲线立即呈现。
- **两种模式**：`stand` – 竖直墙面式图像；`floor` – 地面平铺式图像。
- **高度自定义**：选择方块类型、缩放比例（1 单位长度对应几格），还可生成坐标轴。
- **性能安全**：自动限制 Y 轴范围和垂直跨度，避免卡顿或崩溃。
- **本地化反馈**：游戏内消息支持中文/英文。

---

## 🧪 命令格式
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

## ✨ 特色功能

- **实时表达式解析** – 无需外部库，解析快速可靠。
- **连续曲线** – 使用 Bresenham 直线算法填充采样点之间的空隙。
- **自动处理间断点** – 遇到无效点（如 `1/x` 在 0 附近、`tan(x)` 的渐近线）会自动断开连线。
- **沿轴向放置** – 图像会根据玩家面朝方向，自动对齐东南西北四个基本方向。
- **默认安全机制** – 限制超出世界高度的点，避免巨大垂直跨度。
- **玩家反馈** – 聊天栏显示表达式、方块名称、执行耗时和原点坐标。

---

## 🧩 运行要求

- **Minecraft 版本**：1.21.1（NeoForge）
- **NeoForge 版本**：21.1.176 或更高

---

## 📦 安装方法

1. 从 [Modrinth Version](https://modrinth.com/mod/math-function/versions) 或 [GitHub Releases](https://github.com/aiLinMc/Math-Function/releases) 页面下载最新 JAR 文件。
2. 放入你的 Minecraft `mods` 文件夹。
3. 确保已安装 NeoForge 并启动游戏。
4. 尽情使用命令生成函数图像！

---

## ⚖️ 开源许可

本模组采用 **GNU 通用公共许可证 v3.0** 发布。  
你可以自由使用、修改和重新分发，但必须同样以 GPLv3 许可证公开你的修改。  
详见 [LICENSE](https://github.com/aiLinMc/Math-Function/blob/main/LICENSE) 文件。

---

## 🔗 相关链接

- [GitHub 仓库](https://github.com/aiLinMc/Math-Function)
- [问题反馈](https://github.com/aiLinMc/Math-Function/issues)
- 版本发布：[GitHub](https://github.com/aiLinMc/Math-Function/releases)，[Modrinth](https://modrinth.com/mod/math-function/versions)
