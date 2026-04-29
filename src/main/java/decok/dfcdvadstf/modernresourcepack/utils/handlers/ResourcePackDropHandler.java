package decok.dfcdvadstf.modernresourcepack.utils.handlers;

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

    private static native void nativeRegisterDragDrop(long hwnd);
    private static native void nativeUnregisterDragDrop();
    private static native int nativeGetDroppedFileCount();
    private static native String nativeGetDroppedFile(int index);
    private static native void nativeClearDroppedFiles();

    private static void loadLibrary() {
        if (libraryLoaded) return;

        String os = System.getProperty("os.name").toLowerCase();
        String libName;
        String libResourcePath;
        if (os.contains("win")) {
            libName = "dragdrop.dll";
            libResourcePath = "/natives/windows/dragdrop.dll";
        } else if (os.contains("mac")) {
            libName = "libdragdrop.dylib";
            libResourcePath = "/natives/macos/libdragdrop.dylib";
        } else {
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

        loadLibrary();
        if (!libraryLoaded) return;

        try {
            Field implField = Display.class.getDeclaredField("display_impl");
            implField.setAccessible(true);
            Object impl = implField.get(null);

            if (impl == null || !impl.getClass().getName().contains("WindowsDisplay")) {
                return;
            }

            Field hwndField = impl.getClass().getDeclaredField("hwnd");
            hwndField.setAccessible(true);
            long hwndValue = hwndField.getLong(impl);

            nativeRegisterDragDrop(hwndValue);
            registered = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unregister() {
        if (!registered) return;
        try {
            nativeUnregisterDragDrop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        registered = false;
    }

    public static String[] pollPendingFiles() {
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
