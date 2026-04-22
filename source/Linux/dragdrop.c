#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>

#define MAX_FILES 16
#define MAX_PATH_LEN 1024

static Display *g_display = NULL;
static Window g_window = 0;
static Atom g_xdnd_enter = 0;
static Atom g_xdnd_position = 0;
static Atom g_xdnd_drop = 0;
static Atom g_xdnd_finished = 0;
static Atom g_xdnd_type_list = 0;
static Atom g_uri_list = 0;
static Atom g_text_uri_list = 0;
static char g_files[MAX_FILES][MAX_PATH_LEN];
static int g_fileCount = 0;
static int g_xdnd_version = 0;

static int is_uri_list_atom(Display *display, Atom atom) {
    char *name = XGetAtomName(display, atom);
    if (!name) return 0;
    
    int result = (strcmp(name, "text/uri-list") == 0);
    XFree(name);
    return result;
}

static void parse_uri_list(const char *data, int size) {
    g_fileCount = 0;
    
    char *buffer = malloc(size + 1);
    memcpy(buffer, data, size);
    buffer[size] = '\0';
    
    char *line = strtok(buffer, "\n");
    while (line && g_fileCount < MAX_FILES) {
        if (strncmp(line, "file://", 7) == 0) {
            line += 7;
            
            if (line[0] == '/' && line[1] == '/') {
                line += 2;
            }
            
            int len = strlen(line);
            if (len > 0 && line[len - 1] == '\r') {
                line[len - 1] = '\0';
            }
            
            strncpy(g_files[g_fileCount], line, MAX_PATH_LEN - 1);
            g_files[g_fileCount][MAX_PATH_LEN - 1] = '\0';
            g_fileCount++;
        }
        line = strtok(NULL, "\n");
    }
    
    free(buffer);
}

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_handlers_ResourcePackDropHandler_nativeRegisterDragDrop
  (JNIEnv *env, jclass clazz, jlong displayPtr, jlong windowPtr) {
    g_display = (Display *)displayPtr;
    g_window = (Window)windowPtr;
    
    if (!g_display || !g_window) return;
    
    g_xdnd_enter = XInternAtom(g_display, "XdndEnter", False);
    g_xdnd_position = XInternAtom(g_display, "XdndPosition", False);
    g_xdnd_drop = XInternAtom(g_display, "XdndDrop", False);
    g_xdnd_finished = XInternAtom(g_display, "XdndFinished", False);
    g_text_uri_list = XInternAtom(g_display, "text/uri-list", False);
    
    Atom xdnd_aware = XInternAtom(g_display, "XdndAware", False);
    unsigned long version = 5;
    
    XChangeProperty(g_display, g_window, xdnd_aware, XA_ATOM, 32,
                    PropModeReplace, (unsigned char *)&version, 1);
    
    g_fileCount = 0;
}

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_handlers_ResourcePackDropHandler_nativeUnregisterDragDrop
  (JNIEnv *env, jclass clazz, jlong displayPtr, jlong windowPtr) {
    Display *display = (Display *)displayPtr;
    Window window = (Window)windowPtr;
    
    if (display && window) {
        Atom xdnd_aware = XInternAtom(display, "XdndAware", False);
        XDeleteProperty(display, window, xdnd_aware);
    }
    
    g_fileCount = 0;
    g_display = NULL;
    g_window = 0;
}

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_handlers_ResourcePackDropHandler_nativeHandleXEvent
  (JNIEnv *env, jclass clazz, jlong eventPtr) {
#ifdef __linux__
    XEvent *event = (XEvent *)eventPtr;
    
    if (event->type == ClientMessage) {
        Atom message_type = event->xclient.message_type;
        
        if (message_type == g_xdnd_drop) {
            Window source_window = event->xclient.data.l[0];
            
            Atom actual_type;
            int actual_format;
            unsigned long nitems, bytes_after;
            unsigned char *data = NULL;
            
            XGetWindowProperty(g_display, source_window, g_text_uri_list,
                             0, 1024, False, g_text_uri_list,
                             &actual_type, &actual_format, &nitems, &bytes_after, &data);
            
            if (data && actual_type == g_text_uri_list) {
                parse_uri_list((char *)data, nitems);
                XFree(data);
            }
            
            XEvent finished;
            memset(&finished, 0, sizeof(finished));
            finished.type = ClientMessage;
            finished.xclient.window = source_window;
            finished.xclient.message_type = g_xdnd_finished;
            finished.xclient.format = 32;
            finished.xclient.data.l[0] = g_window;
            finished.xclient.data.l[1] = (g_fileCount > 0) ? 1 : 0;
            XSendEvent(g_display, source_window, False, NoEventMask, &finished);
            XFlush(g_display);
        }
    }
#endif
}

JNIEXPORT jint JNICALL Java_decok_dfcdvadstf_modernresourcepack_handlers_ResourcePackDropHandler_nativeGetDroppedFileCount
  (JNIEnv *env, jclass clazz) {
    return g_fileCount;
}

JNIEXPORT jstring JNICALL Java_decok_dfcdvadstf_modernresourcepack_handlers_ResourcePackDropHandler_nativeGetDroppedFile
  (JNIEnv *env, jclass clazz, jint index) {
    if (index < 0 || index >= g_fileCount) return NULL;
    return (*env)->NewStringUTF(env, g_files[index]);
}

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_handlers_ResourcePackDropHandler_nativeClearDroppedFiles
  (JNIEnv *env, jclass clazz) {
    g_fileCount = 0;
}
