package decok.dfcdvadstf.modernresourcepack.utils.handlers;

import cpw.mods.fml.common.Loader;
import org.lwjgl.opengl.Display;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;

public class ResourcePackDropHandler {
    private static boolean registered = false;
    private static boolean libraryLoaded = false;
    private static File extractedLib = null;
    private static Platform currentPlatform = Platform.UNKNOWN;

    private enum Platform { WINDOWS, LINUX, MAC, UNKNOWN }

    // Windows JNI
    private static native void nativeRegisterDragDrop(long hwnd);
    private static native void nativeUnregisterDragDrop();

    // Linux JNI (X11 based) - takes Display* and Window handle
    private static native void nativeRegisterDragDropX11(long displayPtr, long windowPtr);
    private static native void nativeUnregisterDragDropX11(long displayPtr, long windowPtr);

    // Mac JNI (Cocoa based) - no parameters, native code finds the NSView via [NSApp keyWindow]
    private static native void nativeRegisterDragDropMac();
    private static native void nativeUnregisterDragDropMac();

    // Shared across platforms - backed by per-platform native arrays
    private static native int nativeGetDroppedFileCount();
    private static native String nativeGetDroppedFile(int index);
    private static native void nativeClearDroppedFiles();

    private static void loadLibrary() {
        if (libraryLoaded) return;

        String os = System.getProperty("os.name").toLowerCase();
        String libName;
        String libResourcePath;
        if (os.contains("win")) {
            currentPlatform = Platform.WINDOWS;
            libName = "dragdrop.dll";
            libResourcePath = "/natives/windows/dragdrop.dll";
        } else if (os.contains("mac")) {
            currentPlatform = Platform.MAC;
            libName = "libdragdrop.dylib";
            libResourcePath = "/natives/macos/libdragdrop.dylib";
        } else {
            currentPlatform = Platform.LINUX;
            libName = "libdragdrop.so";
            libResourcePath = "/natives/linux/libdragdrop.so";
        }

        File modDir = new File(System.getProperty("user.dir"), "ModernResourcePackUI");
        extractedLib = new File(modDir, libName);

        // 如果已经存在，直接加载
        if (extractedLib.exists()) {
            try {
                System.load(extractedLib.getAbsolutePath());
                libraryLoaded = true;
                System.out.println("[ModernResourcePackUI] Loaded existing native library: " + extractedLib.getAbsolutePath());
                return;
            } catch (Throwable e) {
                System.out.println("[ModernResourcePackUI] Existing library failed to load, will re-extract.");
            }
        }

        // 从 jar 中提取原生库到 .minecraft/ModernResourcePackUI/
        try {
            InputStream in = ResourcePackDropHandler.class.getResourceAsStream(libResourcePath);
            if (in != null) {
                if (!modDir.exists()) modDir.mkdirs();

                try (OutputStream out = new FileOutputStream(extractedLib)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                in.close();

                System.load(extractedLib.getAbsolutePath());
                libraryLoaded = true;
                System.out.println("[ModernResourcePackUI] Extracted and loaded native library: " + extractedLib.getAbsolutePath());
            } else {
                System.err.println("[ModernResourcePackUI] Native library not found in jar: " + libResourcePath);
            }
        } catch (Throwable e) {
            System.err.println("[ModernResourcePackUI] Failed to load native library. Drag and drop will not work.");
            e.printStackTrace();
        }
    }

    public static void register() {
        if (registered) return;

        // VintageResourcify (via FentLib + lwjgl3ify SDL3) already handles drag-and-drop.
        // Skip our JNI registration to avoid conflicts.
        if (Loader.isModLoaded("vintage-resourcify")) {
            System.out.println("[ModernResourcePackUI] VintageResourcify detected — JNI drag-drop disabled");
            return;
        }

        loadLibrary();
        if (!libraryLoaded) return;

        try {
            Field implField = Display.class.getDeclaredField("display_impl");
            implField.setAccessible(true);
            Object impl = implField.get(null);
            if (impl == null) return;

            String implClassName = impl.getClass().getName();

            if (currentPlatform == Platform.WINDOWS && implClassName.contains("WindowsDisplay")) {
                Field hwndField = impl.getClass().getDeclaredField("hwnd");
                hwndField.setAccessible(true);
                long hwndValue = hwndField.getLong(impl);
                nativeRegisterDragDrop(hwndValue);
                registered = true;
            } else if (currentPlatform == Platform.LINUX && implClassName.contains("LinuxDisplay")) {
                // LinuxDisplay has package-private static fields: display, current_window
                Class<?> cls = impl.getClass();
                Field displayField = cls.getDeclaredField("display");
                Field windowField = cls.getDeclaredField("current_window");
                displayField.setAccessible(true);
                windowField.setAccessible(true);
                long displayPtr = displayField.getLong(null);
                long windowPtr = windowField.getLong(null);
                if (displayPtr == 0L || windowPtr == 0L) {
                    System.err.println("[ModernResourcePackUI] LinuxDisplay handles not ready (display=" + displayPtr + ", window=" + windowPtr + ")");
                    return;
                }
                nativeRegisterDragDropX11(displayPtr, windowPtr);
                registered = true;
                System.out.println("[ModernResourcePackUI] Registered XDnD on display=0x" + Long.toHexString(displayPtr) + " window=0x" + Long.toHexString(windowPtr));
            } else if (currentPlatform == Platform.MAC && implClassName.contains("MacOSXDisplay")) {
                nativeRegisterDragDropMac();
                registered = true;
                System.out.println("[ModernResourcePackUI] Registered Cocoa drag-drop on Mac");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unregister() {
        if (!registered) return;
        try {
            if (currentPlatform == Platform.WINDOWS) {
                nativeUnregisterDragDrop();
            } else if (currentPlatform == Platform.LINUX) {
                Field implField = Display.class.getDeclaredField("display_impl");
                implField.setAccessible(true);
                Object impl = implField.get(null);
                if (impl != null) {
                    Class<?> cls = impl.getClass();
                    Field displayField = cls.getDeclaredField("display");
                    Field windowField = cls.getDeclaredField("current_window");
                    displayField.setAccessible(true);
                    windowField.setAccessible(true);
                    long displayPtr = displayField.getLong(null);
                    long windowPtr = windowField.getLong(null);
                    nativeUnregisterDragDropX11(displayPtr, windowPtr);
                }
            } else if (currentPlatform == Platform.MAC) {
                nativeUnregisterDragDropMac();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        registered = false;
    }

    public static String[] pollPendingFiles() {
        if (!libraryLoaded && !registered) return null;
        try {
            int count = nativeGetDroppedFileCount();
            if (count == 0) return null;

            String[] files = new String[count];
            for (int i = 0; i < count; i++) {
                files[i] = nativeGetDroppedFile(i);
            }
            nativeClearDroppedFiles();
            return files;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
