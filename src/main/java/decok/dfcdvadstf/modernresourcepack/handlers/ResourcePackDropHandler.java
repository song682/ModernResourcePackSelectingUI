package decok.dfcdvadstf.modernresourcepack.handlers;

import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;

public class ResourcePackDropHandler {
    private static boolean registered = false;

    static {
        try {
            System.loadLibrary("dragdrop");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[ModernResourcePackUI] dragdrop.dll not found. Drag and drop will not work.");
        }
    }

    private static native void nativeRegisterDragDrop(long hwnd);
    private static native void nativeUnregisterDragDrop();
    private static native int nativeGetDroppedFileCount();
    private static native String nativeGetDroppedFile(int index);
    private static native void nativeClearDroppedFiles();

    public static void register() {
        if (registered) return;

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
