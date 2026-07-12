# Build the JNI drag-drop library

Well, quite simple — 这个 mod 的拖放功能在每个平台都用一个小 JNI 库实现。源码都在 `source/{Windows,Linux,Mac}/`，编译产物按约定放到 `src/main/resources/natives/{windows,linux,macos}/`，jar 打包时自动塞进去；运行时 `ResourcePackDropHandler` 会解压到 `.minecraft/ModernResourcePackUI/` 再 `System.load`。

JNI 导出函数全部挂在这个类上：
`decok.dfcdvadstf.modernresourcepack.utils.handlers.ResourcePackDropHandler`

所以 C 侧的函数名前缀是：
`Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_*`

注意中间那个 `utils_` —— 早期有一版少了这一段，编出来的 .so / .dll 就算被加载也会 `UnsatisfiedLinkError`。

跨平台的一个通用规则 —— 原生库必须在**对应平台**编译，别想着在 Windows 上交叉编译 Linux `.so`。Linux 就用 WSL 就行。

---

## Linux

### 依赖

Ubuntu / Debian：

```bash
sudo apt install -y build-essential libx11-dev openjdk-8-jdk-headless
```

用 JDK 8 对齐 MC 1.7.10 的运行时。其他发行版装等价包就行。

### 一键脚本（推荐）

```bash
bash source/Linux/compile.sh
```

脚本会：

- 自动找 `JAVA_HOME`（`/usr/lib/jvm/java-8-openjdk-amd64` 优先）
- 编译 `source/Linux/dragdrop.c`
- 产物同时放两份：
  - `src/main/resources/natives/linux/libdragdrop.so` —— 打进 jar 的那份
  - `Linux/libdragdrop.so` —— 仓库根目录镜像，方便翻看

### WSL 跑法

```powershell
wsl -d "Ubuntu-24.04" -- bash -c "cd '/mnt/d/GAMES/Minecraft/modss/project/ModernResourcePackUI' && bash source/Linux/compile.sh"
```

WSL 里挂载的 `/mnt/<盘符>/...` 直接就是 Windows 侧的路径，编完 Windows 这边立刻就能看到 `.so`，不用复制。

### 手动编译

脚本挂了也能自己来：

```bash
gcc -O2 -fPIC -shared -Wall -Wextra \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/linux" \
    source/Linux/dragdrop.c \
    -lX11 -lpthread \
    -o src/main/resources/natives/linux/libdragdrop.so
```

**两个坑提前踩好：**

- `-lpthread` 必须加 —— 我们用独立 Display 连接 + 后台线程 poll XDnD 事件（避免和 LWJGL 主事件循环打架），内部用了 `pthread_create` / `pthread_mutex`。
- `-lX11` 必须加 —— 走的是标准 X11 XDnD 协议（`XdndEnter/Position/Drop/Finished`）。Wayland 系统也 OK，因为 LWJGL 2.9 在 Linux 下一律走 XWayland，XDnD 消息照样能传过来。

### 设计要点

Linux 这边不是 JNI 就能随便收事件的 —— LWJGL 独占着主 Display 连接的 `XNextEvent`。所以我们用了 XDnD 标准的 **XdndProxy** 机制：

1. `XOpenDisplay()` 开一条**独立** X 连接
2. 在独立连接上建一个不可见 InputOnly 代理窗口
3. 在 LWJGL 主窗口上写 `XdndAware=5` 和 `XdndProxy=<代理窗口>` 两个属性
4. 后台 pthread 循环在独立连接上 `XNextEvent`，处理 Enter/Position/Drop 三部曲
5. `XConvertSelection` 拉 `text/uri-list`，URL 解码后扔进共享数组

LWJGL 主循环完全不受影响。

### Java 侧怎么拿到句柄

反射 —— 跟 Windows 端风格一致。LWJGL 2.9 把底层句柄全藏在 private 字段里，没公开 API：

| 平台 | 反射目标 |
| --- | --- |
| Windows | `WindowsDisplay.hwnd` (instance) |
| Linux | `LinuxDisplay.display` + `LinuxDisplay.current_window` (static) |
| Mac | 不反射 — native 代码自己用 `[NSApp windows]` 遍历找 LWJGL/Minecraft 的 NSWindow，取 `.contentView` |

如果哪天升 LWJGL 或 JDK 9+ 这些字段名可能变，到时候再调整。

---

## Windows

### 依赖

装 **MSYS2**（MinGW-w64 工具链）。

### 编译

```bash
gcc -shared -o src/main/resources/natives/windows/dragdrop.dll \
    source/Windows/dragdrop.c \
    -I"%JAVA_HOME%\include" \
    -I"%JAVA_HOME%\include\win32" \
    -lshell32 -luser32
```

### 踩坑提示

- MSYS2 里的 `gcc` 在 PowerShell 默认 PATH 里不可用，得开 **MSYS2 MinGW64 Shell** 专门跑，或者用完整路径 `C:\msys64\mingw64\bin\gcc.exe`。
- MSYS2 里 `JAVA_HOME` 环境变量一般没继承过来，手动导一下：`export JAVA_HOME=/c/Program\ Files/Java/jdk1.8.0_xxx`。
- 64 位 Windows 必须用 `GWLP_WNDPROC`（不是 `GWL_WNDPROC`）配 `SetWindowLongPtr`，不然窗口子类化会炸。

---

## Mac

> **注意：** 目前还没有 Mac 版本 —— 因为我手头没有任何 Apple 生态的设备，编译和测试都做不了。以下仅为预留的编译方式参考，等有设备了再补产物。如果有人愿意帮忙编译并提供 `.dylib` 产物，我将深深感谢。

> **未来计划：** 用 GitHub Actions `macos-latest` runner（ARM64 M1，2024 年起已开放）来 CI 自动构建 `.dylib`。一个 workflow dispatch 下去就跑，连 Apple 硬件都不用。有空就补上这份 Actions 配置。

### 依赖

装 **Xcode Command Line Tools**：

```bash
xcode-select --install
```

### 编译

```bash
clang -dynamiclib -o src/main/resources/natives/macos/libdragdrop.dylib \
    source/Mac/dragdrop.m \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/darwin" \
    -framework Cocoa -lobjc
```

Mac 一定要 **clang**，不要用 gcc —— `.m` 是 Objective-C，系统 gcc 是 clang 伪装的，但别装的 Homebrew gcc 不认识 Objective-C 扩展。

### 设计要点

Mac 这边跟 Linux 一样，不能跟 LWJGL 抢事件循环 —— 但方案简单得多：原生 Cocoa 拖放是**声明式的**，你只需要往 NSView 上 `registerForDraggedTypes:` 注册一个 `NSDraggingDestination` handler，然后 NSApplication 的事件循环自动帮你把 `draggingEntered:` / `performDragOperation:` 回调过来。

所以这个实现做的事：

1. `nativeRegisterDragDropMac()` **无参** —— Java 侧不传句柄，C 侧自己 `[NSApp windows]` 遍历
2. 匹配标题包含 `"LWJGL"` 或 `"Minecraft"` 的窗口 → 拿 `.contentView`
3. 注册两类 pasteboard type：`NSFilenamesPboardType`（旧系统兼容）+ `NSPasteboardTypeFileURL`（10.13+）
4. `performDragOperation:` 回调里解析文件路径，缓存到共享数组
5. `objc_setAssociatedObject` 把 ObjC handler 绑定到 view 上，防 ARC 回收

Java 侧 `register()` 只需要检测到 `MacOSXDisplay` → 调 `nativeRegisterDragDropMac()`，一步到位。不反射，不传参，干手净脚。

---

## 产物汇总一眼看

| 平台 | 源 | 产物 |
| --- | --- | --- |
| Windows | `source/Windows/dragdrop.c` | `src/main/resources/natives/windows/dragdrop.dll` |
| Linux | `source/Linux/dragdrop.c` | `src/main/resources/natives/linux/libdragdrop.so` |
| Mac | `source/Mac/dragdrop.m` | `src/main/resources/natives/macos/libdragdrop.dylib` |

改完别忘了重建 jar（`gradlew build`），不然打进去的还是旧库。

---

## 调试 tips

Linux 下库加载成功、注册成功的话会看到：

```
[ModernResourcePackUI] XDnD proxy window=0x... attached to main=0x...
```

Mac 下注册成功会看到：

```
[ModernResourcePackUI] Registered drag & drop on NSView 0x...
```

没看到就先查：

- Windows / Linux：反射字段名有没有对上（`NoSuchFieldException`）
- `ldd libdragdrop.so`（Linux）/ `nm -D --defined-only dragdrop.dll`（Windows）看依赖齐不齐
- `nm -D --defined-only *.so | grep ResourcePackDropHandler` 看各平台 JNI 符号全不全，漏一个都会 `UnsatisfiedLinkError`
- Windows 注意：源码 `source/Windows/dragdrop.c` 已经修正为 `utils_handlers` 包名，但 `src/main/resources/natives/windows/dragdrop.dll` **还是旧包名**，必须用 MSYS2 重新编译一遍，否则下次 `gradlew build` 后 `UnsatisfiedLinkError`

---

# English Edition — Build the JNI drag-drop library

This is only one tiny JNI library per platform. Sources live in `source/{Windows,Linux,Mac}/`, build output goes into `src/main/resources/natives/{windows,linux,macos}/` so the jar ships with them, and at runtime `ResourcePackDropHandler` extracts to `.minecraft/ModernResourcePackUI/` and `System.load`s it.

All JNI functions are exported with this prefix:

`Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_*`

And — one universal rule: build each native on its own platform. No cross-compile. Linux? Just use WSL.

## Linux

Install deps (Ubuntu / Debian):

```bash
sudo apt install -y build-essential libx11-dev openjdk-8-jdk-headless
```

One-shot script (recommended):

```bash
bash source/Linux/compile.sh
```

Or from WSL on Windows:

```powershell
wsl -d "Ubuntu-24.04" -- bash -c "cd '/mnt/d/GAMES/Minecraft/modss/project/ModernResourcePackUI' && bash source/Linux/compile.sh"
```

Manual command if the script breaks:

```bash
gcc -O2 -fPIC -shared -Wall -Wextra \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/linux" \
    source/Linux/dragdrop.c \
    -lX11 -lpthread \
    -o src/main/resources/natives/linux/libdragdrop.so
```

Links against `-lX11 -lpthread` — the impl uses an independent X11 connection plus a background pthread to poll XDnD events (so it never fights LWJGL's main event loop).

## Mac

> **Note:** No Mac build available yet — I don't have any Apple hardware to compile or test on. The commands below are kept as a reference for when that changes. If anyone builds and contributes the `.dylib`, I'd be deeply grateful.

> **Future:** The `.dylib` will be built via GitHub Actions `macos-latest` runner (ARM64 M1, available since 2024). A single workflow dispatch and it's done — no Apple hardware needed. Just need to write the Actions config.

Install Xcode CLT (`xcode-select --install`), then:

```bash
clang -dynamiclib -o src/main/resources/natives/macos/libdragdrop.dylib \
    source/Mac/dragdrop.m \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/darwin" \
    -framework Cocoa -lobjc
```

Use **clang**, not gcc — `.m` is Objective-C.

No handle passing from Java needed here. The native code calls `[NSApp windows]`, finds the LWJGL / Minecraft NSWindow, grabs its `.contentView`, and registers an `NSDraggingDestination` handler right there. Cocoa does the rest.

## Windows (recompile note)

Install **MSYS2** (MinGW-w64 toolchain), then:

```bash
gcc -shared -o src/main/resources/natives/windows/dragdrop.dll \
    source/Windows/dragdrop.c \
    -I"%JAVA_HOME%\include" \
    -I"%JAVA_HOME%\include\win32" \
    -lshell32 -luser32
```

## Output cheatsheet

| Platform | Source | Output |
| --- | --- | --- |
| Windows | `source/Windows/dragdrop.c` | `src/main/resources/natives/windows/dragdrop.dll` |
| Linux | `source/Linux/dragdrop.c` | `src/main/resources/natives/linux/libdragdrop.so` |
| Mac | `source/Mac/dragdrop.m` | `src/main/resources/natives/macos/libdragdrop.dylib` |

Rebuild the jar (`gradlew build`) after replacing a native — otherwise you're shipping the old one.
