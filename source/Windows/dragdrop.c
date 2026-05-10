#include <windows.h>
#include <shellapi.h>
#include <jni.h>

#define MAX_FILES 16
#define MAX_PATH_LEN 260

static WNDPROC oldWndProc = NULL;
static HWND g_hwnd = NULL;
static char g_files[MAX_FILES][MAX_PATH_LEN];
static int g_fileCount = 0;

LRESULT CALLBACK DragDropWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (msg == WM_DROPFILES) {
        HDROP hDrop = (HDROP)wParam;
        UINT count = DragQueryFileA(hDrop, 0xFFFFFFFF, NULL, 0);
        if (count > MAX_FILES) count = MAX_FILES;

        for (UINT i = 0; i < count; i++) {
            DragQueryFileA(hDrop, i, g_files[i], MAX_PATH_LEN);
        }
        g_fileCount = (int)count;
        DragFinish(hDrop);
        return 0;
    }
    return CallWindowProc(oldWndProc, hwnd, msg, wParam, lParam);
}

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeRegisterDragDrop
  (JNIEnv *env, jclass clazz, jlong hwnd) {
    g_hwnd = (HWND)hwnd;
    DragAcceptFiles(g_hwnd, TRUE);
    oldWndProc = (WNDPROC)SetWindowLongPtr(g_hwnd, GWLP_WNDPROC, (LONG_PTR)DragDropWndProc);
    g_fileCount = 0;
}

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeUnregisterDragDrop
  (JNIEnv *env, jclass clazz) {
    if (g_hwnd && oldWndProc) {
        DragAcceptFiles(g_hwnd, FALSE);
        SetWindowLongPtr(g_hwnd, GWLP_WNDPROC, (LONG_PTR)oldWndProc);
        oldWndProc = NULL;
        g_hwnd = NULL;
    }
    g_fileCount = 0;
}

JNIEXPORT jint JNICALL Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeGetDroppedFileCount
  (JNIEnv *env, jclass clazz) {
    return g_fileCount;
}

JNIEXPORT jstring JNICALL Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeGetDroppedFile
  (JNIEnv *env, jclass clazz, jint index) {
    if (index < 0 || index >= g_fileCount) return NULL;
    return (*env)->NewStringUTF(env, g_files[index]);
}

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeClearDroppedFiles
  (JNIEnv *env, jclass clazz) {
    g_fileCount = 0;
}
