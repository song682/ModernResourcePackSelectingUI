/*
 * ModernResourcePackUI - Linux XDnD drop handler
 *
 * Design notes:
 *   LWJGL 2.9 already owns the X event loop on the main Display connection.
 *   We can't compete for XNextEvent there - so instead we use the standard
 *   XdndProxy indirection:
 *     1. Open an independent Display connection to the same $DISPLAY.
 *     2. Create a small InputOnly proxy window on it.
 *     3. On the LWJGL main window set XdndAware=5 + XdndProxy=<proxy_window>.
 *     4. The proxy window advertises XdndAware=5 too.
 *     5. Any XDnD source now sends all XDnD ClientMessages to proxy_window
 *        on our private connection. We run a background thread that polls
 *        XNextEvent on that connection and handles the Enter/Position/Drop
 *        sequence - completely out of LWJGL's way.
 *
 * Targets are parsed through XConvertSelection / XdndSelection with the
 * "text/uri-list" atom, then URL-decoded into plain filesystem paths.
 */
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>
#include <ctype.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>

#define MAX_FILES 32
#define MAX_PATH_LEN 2048
#define XDND_VERSION 5

/* ---- XDnD state ---- */

static Display *g_aux_display = NULL;        /* our private connection */
static Window   g_proxy_window = 0;          /* proxy window on g_aux_display */
static Display *g_main_display = NULL;       /* LWJGL's connection (for property set only) */
static Window   g_main_window = 0;

static Atom A_XdndAware;
static Atom A_XdndProxy;
static Atom A_XdndEnter;
static Atom A_XdndPosition;
static Atom A_XdndStatus;
static Atom A_XdndDrop;
static Atom A_XdndFinished;
static Atom A_XdndLeave;
static Atom A_XdndSelection;
static Atom A_XdndActionCopy;
static Atom A_XdndActionPrivate;
static Atom A_XdndTypeList;
static Atom A_TextUriList;

static Atom g_wanted_type = 0;       /* resolved text/uri-list type we accept */
static Window g_src_window = 0;      /* current drag source window */
static int    g_src_version = 0;
static int    g_drop_pending = 0;    /* saw XdndDrop, waiting for SelectionNotify */
static Time   g_drop_timestamp = 0;

/* shared file buffer between the poll thread and Java */
static char g_files[MAX_FILES][MAX_PATH_LEN];
static int  g_file_count = 0;
static pthread_mutex_t g_files_mutex = PTHREAD_MUTEX_INITIALIZER;

static pthread_t g_poll_thread;
static int g_thread_running = 0;

/* ---- Helpers ---- */

static int hex_val(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
    if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
    return -1;
}

/* In-place URL decode. Safe because the decoded string is never longer. */
static void url_decode_inplace(char *s) {
    char *w = s;
    for (char *r = s; *r; ) {
        if (*r == '%' && r[1] && r[2]) {
            int h = hex_val(r[1]);
            int l = hex_val(r[2]);
            if (h >= 0 && l >= 0) {
                *w++ = (char)((h << 4) | l);
                r += 3;
                continue;
            }
        }
        *w++ = *r++;
    }
    *w = '\0';
}

static void strip_trailing_cr(char *s) {
    size_t len = strlen(s);
    while (len > 0 && (s[len - 1] == '\r' || s[len - 1] == '\n')) {
        s[--len] = '\0';
    }
}

static void parse_uri_list(const char *data, int size) {
    pthread_mutex_lock(&g_files_mutex);
    g_file_count = 0;

    char *buffer = (char *)malloc((size_t)size + 1);
    if (!buffer) { pthread_mutex_unlock(&g_files_mutex); return; }
    memcpy(buffer, data, (size_t)size);
    buffer[size] = '\0';

    char *save = NULL;
    char *line = strtok_r(buffer, "\n", &save);
    while (line && g_file_count < MAX_FILES) {
        /* skip comment lines per RFC 2483 */
        if (line[0] != '#') {
            strip_trailing_cr(line);
            if (strncmp(line, "file://", 7) == 0) {
                line += 7;
                /* file:///path -> /path ; file://host/path -> /path (drop host) */
                if (line[0] != '/') {
                    char *slash = strchr(line, '/');
                    if (slash) line = slash;
                }
            }
            if (line[0] != '\0') {
                strncpy(g_files[g_file_count], line, MAX_PATH_LEN - 1);
                g_files[g_file_count][MAX_PATH_LEN - 1] = '\0';
                url_decode_inplace(g_files[g_file_count]);
                g_file_count++;
            }
        }
        line = strtok_r(NULL, "\n", &save);
    }

    free(buffer);
    pthread_mutex_unlock(&g_files_mutex);
}

static void send_xdnd_status(Window source, int accept) {
    XEvent ev;
    memset(&ev, 0, sizeof(ev));
    ev.xclient.type = ClientMessage;
    ev.xclient.display = g_aux_display;
    ev.xclient.window = source;
    ev.xclient.message_type = A_XdndStatus;
    ev.xclient.format = 32;
    ev.xclient.data.l[0] = (long)g_proxy_window;
    ev.xclient.data.l[1] = accept ? 1 : 0;   /* bit0: will accept */
    ev.xclient.data.l[2] = 0;                /* x,y rect */
    ev.xclient.data.l[3] = 0;                /* w,h rect */
    ev.xclient.data.l[4] = accept ? (long)A_XdndActionCopy : 0;
    XSendEvent(g_aux_display, source, False, NoEventMask, &ev);
    XFlush(g_aux_display);
}

static void send_xdnd_finished(Window source, int succeeded) {
    XEvent ev;
    memset(&ev, 0, sizeof(ev));
    ev.xclient.type = ClientMessage;
    ev.xclient.display = g_aux_display;
    ev.xclient.window = source;
    ev.xclient.message_type = A_XdndFinished;
    ev.xclient.format = 32;
    ev.xclient.data.l[0] = (long)g_proxy_window;
    ev.xclient.data.l[1] = succeeded ? 1 : 0;
    ev.xclient.data.l[2] = succeeded ? (long)A_XdndActionCopy : 0;
    XSendEvent(g_aux_display, source, False, NoEventMask, &ev);
    XFlush(g_aux_display);
}

/* Pick a target type we understand from the list the source advertises. */
static Atom pick_uri_list_type(Window source, XClientMessageEvent *enter) {
    long more_types_flag = enter->data.l[1] & 0x1;
    if (!more_types_flag) {
        /* types 0..2 are in data.l[2..4] */
        for (int i = 2; i <= 4; i++) {
            Atom t = (Atom)enter->data.l[i];
            if (t == A_TextUriList) return t;
        }
        return 0;
    }
    /* long type list stored in XdndTypeList property on the source */
    Atom actual_type;
    int actual_format;
    unsigned long nitems, bytes_after;
    unsigned char *data = NULL;
    if (XGetWindowProperty(g_aux_display, source, A_XdndTypeList,
                           0, 1024, False, XA_ATOM,
                           &actual_type, &actual_format, &nitems, &bytes_after, &data) == Success
        && data) {
        Atom *atoms = (Atom *)data;
        Atom found = 0;
        for (unsigned long i = 0; i < nitems; i++) {
            if (atoms[i] == A_TextUriList) { found = atoms[i]; break; }
        }
        XFree(data);
        return found;
    }
    return 0;
}

static void handle_selection_notify(XSelectionEvent *ev) {
    if (!g_drop_pending) return;
    if (ev->property == None) {
        send_xdnd_finished(g_src_window, 0);
        g_drop_pending = 0;
        return;
    }

    Atom actual_type;
    int actual_format;
    unsigned long nitems, bytes_after;
    unsigned char *data = NULL;

    if (XGetWindowProperty(g_aux_display, g_proxy_window, ev->property,
                           0, (~0L), True /* delete after read */,
                           AnyPropertyType,
                           &actual_type, &actual_format, &nitems, &bytes_after, &data) == Success
        && data) {
        parse_uri_list((const char *)data, (int)nitems);
        XFree(data);
        send_xdnd_finished(g_src_window, 1);
    } else {
        send_xdnd_finished(g_src_window, 0);
    }
    g_drop_pending = 0;
}

static void *poll_thread_main(void *unused) {
    (void)unused;
    while (g_thread_running) {
        /* Non-blocking drain so we can notice shutdown quickly. */
        while (g_thread_running && XPending(g_aux_display) > 0) {
            XEvent ev;
            XNextEvent(g_aux_display, &ev);

            if (ev.type == ClientMessage) {
                Atom mt = ev.xclient.message_type;
                if (mt == A_XdndEnter) {
                    g_src_window  = (Window)ev.xclient.data.l[0];
                    g_src_version = (int)((ev.xclient.data.l[1] >> 24) & 0xFF);
                    g_wanted_type = pick_uri_list_type(g_src_window, &ev.xclient);
                } else if (mt == A_XdndPosition) {
                    Window src = (Window)ev.xclient.data.l[0];
                    g_drop_timestamp = (Time)ev.xclient.data.l[3];
                    send_xdnd_status(src, g_wanted_type != 0 ? 1 : 0);
                } else if (mt == A_XdndLeave) {
                    g_src_window = 0;
                    g_wanted_type = 0;
                    g_drop_pending = 0;
                } else if (mt == A_XdndDrop) {
                    Window src = (Window)ev.xclient.data.l[0];
                    g_src_window = src;
                    Time ts = (Time)ev.xclient.data.l[2];
                    if (g_wanted_type == 0) {
                        send_xdnd_finished(src, 0);
                    } else {
                        g_drop_pending = 1;
                        XConvertSelection(g_aux_display, A_XdndSelection,
                                          g_wanted_type, A_XdndSelection,
                                          g_proxy_window, ts);
                        XFlush(g_aux_display);
                    }
                }
            } else if (ev.type == SelectionNotify) {
                handle_selection_notify(&ev.xselection);
            }
        }
        /* Small sleep to avoid spinning - XDnD isn't latency-critical. */
        usleep(30 * 1000);
    }
    return NULL;
}

/* ---- JNI entry points ---- */

static void intern_atoms(Display *d) {
    A_XdndAware         = XInternAtom(d, "XdndAware", False);
    A_XdndProxy         = XInternAtom(d, "XdndProxy", False);
    A_XdndEnter         = XInternAtom(d, "XdndEnter", False);
    A_XdndPosition      = XInternAtom(d, "XdndPosition", False);
    A_XdndStatus        = XInternAtom(d, "XdndStatus", False);
    A_XdndDrop          = XInternAtom(d, "XdndDrop", False);
    A_XdndFinished      = XInternAtom(d, "XdndFinished", False);
    A_XdndLeave         = XInternAtom(d, "XdndLeave", False);
    A_XdndSelection     = XInternAtom(d, "XdndSelection", False);
    A_XdndActionCopy    = XInternAtom(d, "XdndActionCopy", False);
    A_XdndActionPrivate = XInternAtom(d, "XdndActionPrivate", False);
    A_XdndTypeList      = XInternAtom(d, "XdndTypeList", False);
    A_TextUriList       = XInternAtom(d, "text/uri-list", False);
}

JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeRegisterDragDropX11
  (JNIEnv *env, jclass clazz, jlong displayPtr, jlong windowPtr) {
    (void)env; (void)clazz;

    if (g_thread_running) return; /* already registered */

    g_main_display = (Display *)(intptr_t)displayPtr;
    g_main_window  = (Window)windowPtr;
    if (!g_main_display || !g_main_window) return;

    /* Open our private connection to the same display. */
    const char *dpy_name = XDisplayString(g_main_display);
    g_aux_display = XOpenDisplay(dpy_name);
    if (!g_aux_display) {
        fprintf(stderr, "[ModernResourcePackUI] XOpenDisplay(aux) failed for %s\n", dpy_name ? dpy_name : "(null)");
        return;
    }

    intern_atoms(g_aux_display);

    /* Create a 1x1 InputOnly proxy window on our connection. */
    int screen = DefaultScreen(g_aux_display);
    Window root = RootWindow(g_aux_display, screen);
    XSetWindowAttributes swa;
    memset(&swa, 0, sizeof(swa));
    g_proxy_window = XCreateWindow(g_aux_display, root,
                                   -10, -10, 1, 1, 0,
                                   0, InputOnly, CopyFromParent,
                                   0, &swa);

    long version = XDND_VERSION;
    /* Advertise XDnD on proxy window. */
    XChangeProperty(g_aux_display, g_proxy_window, A_XdndAware, XA_ATOM, 32,
                    PropModeReplace, (unsigned char *)&version, 1);
    /* Self-proxy entry on proxy window (required by XDnD spec). */
    XChangeProperty(g_aux_display, g_proxy_window, A_XdndProxy, XA_WINDOW, 32,
                    PropModeReplace, (unsigned char *)&g_proxy_window, 1);
    XFlush(g_aux_display);

    /* Set XdndAware + XdndProxy on LWJGL's main window through its own display.
     * Property writes are safe here - we're not reading its event queue. */
    Atom mainAware = XInternAtom(g_main_display, "XdndAware", False);
    Atom mainProxy = XInternAtom(g_main_display, "XdndProxy", False);
    XChangeProperty(g_main_display, g_main_window, mainAware, XA_ATOM, 32,
                    PropModeReplace, (unsigned char *)&version, 1);
    XChangeProperty(g_main_display, g_main_window, mainProxy, XA_WINDOW, 32,
                    PropModeReplace, (unsigned char *)&g_proxy_window, 1);
    XFlush(g_main_display);

    /* Reset shared state. */
    pthread_mutex_lock(&g_files_mutex);
    g_file_count = 0;
    pthread_mutex_unlock(&g_files_mutex);

    g_thread_running = 1;
    if (pthread_create(&g_poll_thread, NULL, poll_thread_main, NULL) != 0) {
        g_thread_running = 0;
        fprintf(stderr, "[ModernResourcePackUI] pthread_create failed\n");
        XCloseDisplay(g_aux_display);
        g_aux_display = NULL;
        return;
    }

    fprintf(stdout, "[ModernResourcePackUI] XDnD proxy window=0x%lx attached to main=0x%lx\n",
            (unsigned long)g_proxy_window, (unsigned long)g_main_window);
}

JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeUnregisterDragDropX11
  (JNIEnv *env, jclass clazz, jlong displayPtr, jlong windowPtr) {
    (void)env; (void)clazz;

    Display *mainD = (Display *)(intptr_t)displayPtr;
    Window mainW = (Window)windowPtr;

    if (mainD && mainW) {
        Atom mainAware = XInternAtom(mainD, "XdndAware", False);
        Atom mainProxy = XInternAtom(mainD, "XdndProxy", False);
        XDeleteProperty(mainD, mainW, mainAware);
        XDeleteProperty(mainD, mainW, mainProxy);
        XFlush(mainD);
    }

    if (g_thread_running) {
        g_thread_running = 0;
        pthread_join(g_poll_thread, NULL);
    }

    if (g_aux_display) {
        if (g_proxy_window) {
            XDestroyWindow(g_aux_display, g_proxy_window);
            g_proxy_window = 0;
        }
        XCloseDisplay(g_aux_display);
        g_aux_display = NULL;
    }

    pthread_mutex_lock(&g_files_mutex);
    g_file_count = 0;
    pthread_mutex_unlock(&g_files_mutex);
}

JNIEXPORT jint JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeGetDroppedFileCount
  (JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    pthread_mutex_lock(&g_files_mutex);
    int c = g_file_count;
    pthread_mutex_unlock(&g_files_mutex);
    return (jint)c;
}

JNIEXPORT jstring JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeGetDroppedFile
  (JNIEnv *env, jclass clazz, jint index) {
    (void)clazz;
    char buf[MAX_PATH_LEN];
    pthread_mutex_lock(&g_files_mutex);
    if (index < 0 || index >= g_file_count) {
        pthread_mutex_unlock(&g_files_mutex);
        return NULL;
    }
    strncpy(buf, g_files[index], MAX_PATH_LEN);
    buf[MAX_PATH_LEN - 1] = '\0';
    pthread_mutex_unlock(&g_files_mutex);
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeClearDroppedFiles
  (JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    pthread_mutex_lock(&g_files_mutex);
    g_file_count = 0;
    pthread_mutex_unlock(&g_files_mutex);
}