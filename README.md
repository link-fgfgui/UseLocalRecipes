# Use Local Recipes

[![CurseForge](https://img.shields.io/badge/CurseForge-Use%20Local%20Recipes-orange)](https://www.curseforge.com/minecraft/mc-mods)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Fabric%20%7C%20NeoForge-blue)](#)

> **English description is placed first, followed by Chinese (中文说明见下文).**

---

## 📖 About The Mod

**Use Local Recipes** is a client-side Minecraft utility mod that fixes empty or missing recipe viewers (such as JEI) when playing on remote dedicated servers, proxies (Velocity/BungeeCord), or paper/spigot servers that do not synchronize vanilla or modded recipes to clients.

Normally, client-side recipe viewers rely entirely on the server to send recipe packets (`ClientboundUpdateRecipesPacket`). When connected to servers that disable recipe syncing or send incomplete data, recipe viewers are left blank.

**Use Local Recipes** solves this by reading recipes directly from your local game files, installed client mods, and data packs, seamlessly feeding them to client recipe viewers while keeping the server authoritative.

---

## ✨ Main Features

### 🔍 1. Automatic Local Recipe Fallback
- When joining a multiplayer server, the mod detects if the server fails to provide recipes.
- Reads recipes directly from the client's local Minecraft jar and installed client-side mod jars without needing any server-side companion mod.

### 🛡️ 2. Smart Server-Authoritative Merging
- Server recipes **always** take precedence.
- If the server provides recipes for a particular type, those recipes win. Local recipes never overwrite custom server recipes.
- Deduplicates recipes cleanly by identifier.

### 🏷️ 3. Full Vanilla Tag & Codec Resolution
- Unlike naive JSON parsers, **Use Local Recipes** constructs an authentic data pack stack (`ServerPacksSource` + client mod data packs) and resolves pending registry tags (`#minecraft:planks`, `#c:ingots`, etc.).
- Tag-based ingredients correctly resolve to their matching items, ensuring recipe inputs and outputs are 100% valid.

### ⚡ 4. Asynchronous & Zero-Lag Loading
- Local recipe reading occurs in the background on a dedicated worker thread (`uselocalrecipes-reader`).
- Joining a world or connecting to a server remains buttery smooth with zero frame hitching or connection timeouts.

### 🖱️ 5. Client-Side JEI Recipe Transfer
- When JEI is not installed on the server, clicking the `+` button in JEI normally fails with a "JEI is not installed on the server" error.
- **Use Local Recipes** includes a client-side click simulation fallback (inspired by EMI):
  - Automatically calculates item movements and simulates player clicks.
  - Supports both regular crafting transfer and batch max-transfer (Shift-Click).
  - Works safely without requiring JEI on the server.

### ⚙️ 6. Friendly In-Game Feedback & Configurable Options
- Notifies the player in chat when local fallback recipes are being displayed.
- Everything is configurable in `.minecraft/config/uselocalrecipes.json`:
  - `enabled`: Enable or disable the mod.
  - `syncDelayTicks`: Grace period ticks after connecting before falling back to local files (default: `60`).
  - `preferServerTypes`: Prefer server-provided recipe types over local duplicates (default: `true`).
  - `warnAboutLocalRecipes`: Toggle the chat notification message (default: `true`).
  - `logFailedRecipes`: Log unparseable recipe JSONs to help pack developers debug broken recipes (default: `false`).
  - `clientSideTransfer`: Enable client-side JEI `+` button click transfer fallback (default: `true`).

---

## 🔌 Compatibility & Requirements

- **Side**: 100% Client-Only. Do **not** install on dedicated servers. Singleplayer automatically bypasses the mod since the integrated server already holds complete recipes.
- **Mod Loaders**: Fabric & NeoForge (Multiloader architecture).
- **Supported Recipe Viewers**: JEI (Just Enough Items).
- **Dependencies**:
  - Fabric: Fabric API
  - NeoForge: NeoForge Loader

---

<br>

---

# 简体中文 (Chinese Description)

## 📖 模组简介

**Use Local Recipes**（使用本地配方）是一款纯客户端实用的 Minecraft 模组。

在连接到某些未向客户端同步配方的远程服务器（如部分第三方服务端、Spigot/Paper、代理服或开启了特殊保护的服务器）时，JEI 等配方查询器常常因为收不到服务端的配方数据包而无法显示合成表。

本模组通过在客户端后台解析本地客户端与已安装模组的数据包文件，自动将配方注入到客户端配方查看器中，彻底解决联机时查不到配方的问题！

---

## ✨ 核心特性

### 🔍 1. 自动本地配方回退
- 进入多人服务器时自动检测；若服务端未同步配方，自动从客户端本地文件和已安装模组中提取配方补齐。
- 服务端完全无需安装任何前置或配套插件/模组。

### 🛡️ 2. 智能合并与服务端优先
- 服务端配方始终拥有最高优先级。
- 如果服务端自定义了配方，本地配方不会覆盖服务端的定制内容，仅填补缺失部分。

### 🏷️ 3. 完整原生标签（Tags）解析
- 采用原版标准的 DataPack 加载流程，先解析 Registry Tags，保证带有物品标签（如 `#minecraft:logs`）的配方原料能正确匹配，避免粗暴解析 JSON 导致的配方损坏。

### ⚡ 4. 异步无卡顿加载
- 读取与解析完全在后台独立守护线程执行，进服与游戏过程丝滑流畅，绝不卡顿或拖慢连接。

### 🖱️ 5. 客户端 JEI 配方一键填充（+ 号支持）
- 当远程服务器未安装 JEI 时，点击 JEI 界面的 `+` 号通常会提示服务端未安装。
- 本模组内置模拟点击填充机制（灵感源于 EMI），通过模拟客户端点击直接把背包材料填入工作台，无需服务端支持亦可享受一键合成转移功能，支持单次与批量（Shift）转移！

### ⚙️ 6. 贴心提示与高度可配置
- 当启用本地配方时在聊天栏轻度提醒玩家（可随时关闭）。
- 配置文件位于 `.minecraft/config/uselocalrecipes.json`，可自由调整同步延时、警告提示、日志调试与配方传输等。

---

## 📄 授权与鸣谢 (License & Credits)

- **License**: MIT
- **Inspiration**: Inspired by EMI's client recipe loader & recipe transfer handling.
