# j2me-web

[English](README.md)

在现代浏览器中运行 J2ME MIDlet。

j2me-web 将单线程 Rust/WebAssembly JVM 与浏览器平台适配层结合，提供 Canvas 渲染、
输入、音频、网络和 RMS 持久化能力。它支持 MIDP/LCDUI 应用、M3G（JSR-184）内容及
部分常见设备扩展。

> 本项目是兼容运行时，不是 Java ME 设备的完整实现，也不提供 MIDlet 文件。请自行
> 提供 JAR，并确保拥有使用它的权利。

## 接入方式

安装浏览器包：

```sh
pnpm add j2me-web
```

创建 Canvas、加载 MIDlet JAR，然后启动运行时：

```ts
import { J2MEWeb } from 'j2me-web';

const canvas = document.querySelector<HTMLCanvasElement>('#game');
if (!canvas) throw new Error('Missing game canvas');

canvas.width = 240;
canvas.height = 320;

const response = await fetch('/midlets/example.jar');
if (!response.ok) throw new Error(`Could not load JAR: ${response.status}`);

const j2me = new J2MEWeb({
  canvas,
  jar: await response.arrayBuffer(),
  midletId: 'example',
});

await j2me.start();
```

传入 `<canvas>` 和 JAR 数据即可。

## 技术栈

- Rust 编写的 J2ME JVM，编译为 WebAssembly；Java 线程在单线程调度器中协作执行。
- TypeScript 浏览器适配层，接入 Canvas、键盘/触摸、字体、网络和音频。
- WebGPU 渲染 LCDUI 图形与 M3G / JSR-184 3D 内容。
- IndexedDB 实现 RecordStore（RMS）持久化。

## 重点能力

### 3D 支持

通过 WebGPU 渲染 JSR-184 M3G 内容，并实现了持续演进中的 Mascot Capsule / Micro3D
兼容层。

### 实时存档与读档

无需重启 MIDlet 即可在运行中存档和恢复。除可持久化的存档数据外，还支持快速内存回退
快照，用于在活跃会话中即时回退。

## 功能范围

| 范围 | 已实现 |
| --- | --- |
| Java 运行时 | CLDC 1.1 风格类库、JVM 字节码、协作式 Java 线程、同步、资源加载与 GC。 |
| MIDP UI | MIDP 2.0 / LCDUI 的表单、列表、命令、`Canvas`、`GameCanvas`、文本/日期输入、图片、字体和触摸/按键事件。 |
| 运行控制 | 按键/触摸注入、暂停恢复、截图、性能数据、网络拦截和软键标签回调。 |
| 存储与网络 | IndexedDB RecordStore；基于 `fetch` 的 HTTP 请求，并支持由宿主拦截。 |
| 媒体 | Mobile Media 播放路径与浏览器音频；实际编解码支持受浏览器和自动播放策略影响。 |
| 厂商扩展 | Nokia UI、DirectGraphics、音效、震动等；Bluetooth 由嵌入应用按需提供宿主桥接。 |

## 浏览器要求

j2me-web 仅面向浏览器运行，目前没有 Canvas 2D、WebGL 或其他非 WebGPU 的渲染后备实现。

| 要求 | 用途 |
| --- | --- |
| 安全上下文与 WebGPU | 必需渲染器：`navigator.gpu`、`GPUCanvasContext` 及可用的 WebGPU adapter/device。实际部署应使用 HTTPS（或 `localhost`）。 |
| WebAssembly 与 `fetch` | 加载和运行 Rust/WASM JVM。JAR 和包内资源必须可被获取。 |
| DOM、`HTMLCanvasElement` 与 Canvas 2D | 宿主 Canvas、字体测量、图像转换，以及原生文本/日期输入层。 |
| `MessageChannel` 与 `performance.now()` | 运行时调度和计时。 |
| IndexedDB | RMS 持久化。存储不可用时，RMS 会退化为空读取或写入失败。 |
| Web Audio API 与 `HTMLAudioElement` | 音频播放。MIDI 还需要 `AudioWorklet`；浏览器自动播放策略要求由用户操作触发声音播放。 |

### 截图

| LCDUI 游戏 | M3G 游戏 |
| --- | --- |
| ![LCDUI 游戏](docs/images/lcdui.png) | ![运行 M3G 的 Galaxy on Fire](docs/images/m3g.png) |

## 许可证与声明

本仓库采用 [MIT License](LICENSE)。保留的第三方源码与资源声明（包括 M3G 核心源码
文件头）见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
