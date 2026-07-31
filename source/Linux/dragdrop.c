/*
 * ModernResourcePackUI - Linux XDnD drop handler (GNOME / XWayland friendly)
 *
 * Why the old designs failed:
 *   1. "aux connection + XdndProxy proxy window + poll thread": mutter's
 *      Wayland->X11 DnD bridge (meta-xwayland-dnd.c) ignores the XdndProxy
 *      property and sends every XDnD ClientMessage straight to the LWJGL
 *      toplevel. Per X11, XSendEvent with NoEventMask is delivered ONLY to
 *      the client that created the destination window (LWJGL's connection),
 *      so a second connection never sees them.
 *   2. "once-per-frame XCheckIfEvent in drawScreen": LWJGL's Display.update()
 *      drains the ENTIRE X queue with XNextEvent every frame (LinuxDisplay
 *      .processEvents) and silently discards all XDnD ClientMessages. Polling
 *      in drawScreen runs long before that drain, so nearly every message is
 *      eaten before we look -> races, low success rate.
 *
 * Current design (single connection, main thread, no pthread):
 *   - Set XdndAware=5 on the LWJGL toplevel through LWJGL's own Display*.
 *   - nativePollDndEventsX11() is called from the render thread TWICE per
 *     frame: once at the HEAD of Minecraft.func_147120_f() (immediately
 *     before Display.update() drains) and once from drawScreen. The pre-drain
 *     call shrinks the "eat window" to microseconds, so XdndStatus replies to
 *     XdndPosition become reliable and the source consistently permits drops.
 *   - When we catch XdndDrop we fetch the selection SYNCHRONOUSLY right there:
 *     XConvertSelection + a bounded XCheckTypedWindowEvent wait for our own
 *     SelectionNotify. This removes the async selection race entirely without
 *     a second thread or connection.
 *
 * Works on plain X11 too, since real XDnD sources also deliver to the
 * XdndAware toplevel when no proxy is honored.
 */
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>

#define MAX_FILES 32
#define MAX_PATH_LEN 2048
#define XDND_VERSION 5

/* How long the synchronous selection fetch will wait for SelectionNotify. */
#define SELECTION_TIMEOUT_MS 500
#define SELECTION_POLL_STEP_MS 2

/* ---- XDnD state (all accessed from the render/main thread only) ---- */

static Display *g_display = NULL;   /* LWJGL's own connection */
static Window   g_window = 0;       /* LWJGL's toplevel window */

static Atom A_XdndAware;
static Atom A_XdndEnter;
static Atom A_XdndPosition;
static Atom A_XdndStatus;
static Atom A_XdndDrop;
static Atom A_XdndFinished;
static Atom A_XdndLeave;
static Atom A_XdndSelection;
static Atom A_XdndActionCopy;
static Atom A_XdndTypeList;
static Atom A_TextUriList;
static Atom A_DndProperty;          /* our property for XConvertSelection */

static Atom   g_wanted_type = 0;    /* resolved text/uri-list type we accept */
static Window g_src_window = 0;     /* current drag source window */
static int    g_registered = 0;

static char g_files[MAX_FILES][MAX_PATH_LEN];
static int  g_file_count = 0;

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
    g_file_count = 0;

    char *buffer = (char *)malloc((size_t)size + 1);
    if (!buffer) return;
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
}

static void send_xdnd_status(Window source, int accept) {
    XEvent ev;
    memset(&ev, 0, sizeof(ev));
    ev.xclient.type = ClientMessage;
    ev.xclient.display = g_display;
    ev.xclient.window = source;
    ev.xclient.message_type = A_XdndStatus;
    ev.xclient.format = 32;
    ev.xclient.data.l[0] = (long)g_window;
    ev.xclient.data.l[1] = accept ? 1 : 0;   /* bit0: will accept */
    ev.xclient.data.l[2] = 0;                /* x,y rect: 0 => query every move */
    ev.xclient.data.l[3] = 0;                /* w,h rect */
    ev.xclient.data.l[4] = accept ? (long)A_XdndActionCopy : 0;
    XSendEvent(g_display, source, False, NoEventMask, &ev);
    XFlush(g_display);
}

static void send_xdnd_finished(Window source, int succeeded) {
    XEvent ev;
    memset(&ev, 0, sizeof(ev));
    ev.xclient.type = ClientMessage;
    ev.xclient.display = g_display;
    ev.xclient.window = source;
    ev.xclient.message_type = A_XdndFinished;
    ev.xclient.format = 32;
    ev.xclient.data.l[0] = (long)g_window;
    ev.xclient.data.l[1] = succeeded ? 1 : 0;
    ev.xclient.data.l[2] = succeeded ? (long)A_XdndActionCopy : 0;
    XSendEvent(g_display, source, False, NoEventMask, &ev);
    XFlush(g_display);
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
    if (XGetWindowProperty(g_display, source, A_XdndTypeList,
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

/*
 * Synchronously fetch XdndSelection into g_files. Runs on the render thread
 * right after we see XdndDrop, so it does not compete with LWJGL's per-frame
 * drain. Only pulls our OWN SelectionNotify (by window+type), leaving every
 * other event in the queue for LWJGL. Returns 1 on success.
 */
static int fetch_drop_selection(Time drop_time) {
    if (g_wanted_type == 0) return 0;

    XConvertSelection(g_display, A_XdndSelection, g_wanted_type,
                      A_DndProperty, g_window, drop_time);
    XFlush(g_display);

    int waited = 0;
    XEvent ev;
    while (waited < SELECTION_TIMEOUT_MS) {
        if (XCheckTypedWindowEvent(g_display, g_window, SelectionNotify, &ev)) {
            if (ev.xselection.property == None) return 0;

            Atom actual_type;
            int actual_format;
            unsigned long nitems, bytes_after;
            unsigned char *data = NULL;
            if (XGetWindowProperty(g_display, g_window, ev.xselection.property,
                                   0, (~0L), True /* delete after read */,
                                   AnyPropertyType,
                                   &actual_type, &actual_format,
                                   &nitems, &bytes_after, &data) == Success
                && data) {
                parse_uri_list((const char *)data, (int)nitems);
                XFree(data);
                return g_file_count > 0;
            }
            return 0;
        }
        usleep(SELECTION_POLL_STEP_MS * 1000);
        waited += SELECTION_POLL_STEP_MS;
    }
    return 0; /* timed out */
}

/* XCheckIfEvent predicate: grab only the XDnD handshake ClientMessages. */
static Bool dnd_event_predicate(Display *d, XEvent *ev, XPointer arg) {
    (void)d; (void)arg;
    if (ev->type == ClientMessage && ev->xclient.window == g_window) {
        Atom mt = ev->xclient.message_type;
        return (mt == A_XdndEnter || mt == A_XdndPosition ||
                mt == A_XdndLeave || mt == A_XdndDrop) ? True : False;
    }
    return False;
}

static void handle_dnd_event(XEvent *ev) {
    Atom mt = ev->xclient.message_type;
    if (mt == A_XdndEnter) {
        g_src_window  = (Window)ev->xclient.data.l[0];
        g_wanted_type = pick_uri_list_type(g_src_window, &ev->xclient);
    } else if (mt == A_XdndPosition) {
        Window src = (Window)ev->xclient.data.l[0];
        /* If LWJGL swallowed the XdndEnter before we grabbed it, assume
         * text/uri-list - Nautilus/mutter always offer it for files.
         * A wrong guess just fails the later conversion (finished(0)). */
        if (g_src_window != src) {
            g_src_window  = src;
            if (g_wanted_type == 0) g_wanted_type = A_TextUriList;
        }
        send_xdnd_status(src, g_wanted_type != 0 ? 1 : 0);
    } else if (mt == A_XdndLeave) {
        g_src_window = 0;
        g_wanted_type = 0;
    } else if (mt == A_XdndDrop) {
        Window src = (Window)ev->xclient.data.l[0];
        g_src_window = src;
        Time ts = (Time)ev->xclient.data.l[2];
        if (ts == 0) ts = CurrentTime;
        int ok = fetch_drop_selection(ts);
        send_xdnd_finished(src, ok);
        g_src_window = 0;
        g_wanted_type = 0;
    }
}

static void intern_atoms(Display *d) {
    A_XdndAware      = XInternAtom(d, "XdndAware", False);
    A_XdndEnter      = XInternAtom(d, "XdndEnter", False);
    A_XdndPosition   = XInternAtom(d, "XdndPosition", False);
    A_XdndStatus     = XInternAtom(d, "XdndStatus", False);
    A_XdndDrop       = XInternAtom(d, "XdndDrop", False);
    A_XdndFinished   = XInternAtom(d, "XdndFinished", False);
    A_XdndLeave      = XInternAtom(d, "XdndLeave", False);
    A_XdndSelection  = XInternAtom(d, "XdndSelection", False);
    A_XdndActionCopy = XInternAtom(d, "XdndActionCopy", False);
    A_XdndTypeList   = XInternAtom(d, "XdndTypeList", False);
    A_TextUriList    = XInternAtom(d, "text/uri-list", False);
    A_DndProperty    = XInternAtom(d, "MODERNRPUI_DND_DATA", False);
}

/* ---- JNI entry points ---- */

JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeRegisterDragDropX11
  (JNIEnv *env, jclass clazz, jlong displayPtr, jlong windowPtr) {
    (void)env; (void)clazz;

    if (g_registered) return;

    g_display = (Display *)(intptr_t)displayPtr;
    g_window  = (Window)windowPtr;
    if (!g_display || !g_window) return;

    intern_atoms(g_display);

    /* Advertise XDnD on the LWJGL toplevel. mutter's XWayland bridge (and
     * plain X11 sources) will target this window directly. */
    long version = XDND_VERSION;
    XChangeProperty(g_display, g_window, A_XdndAware, XA_ATOM, 32,
                    PropModeReplace, (unsigned char *)&version, 1);
    XFlush(g_display);

    g_file_count = 0;
    g_src_window = 0;
    g_wanted_type = 0;
    g_registered = 1;

    fprintf(stdout, "[ModernResourcePackUI] XdndAware set on window=0x%lx (pre-drain poll mode)\n",
            (unsigned long)g_window);
}

JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeUnregisterDragDropX11
  (JNIEnv *env, jclass clazz, jlong displayPtr, jlong windowPtr) {
    (void)env; (void)clazz;

    Display *d = (Display *)(intptr_t)displayPtr;
    Window w = (Window)windowPtr;

    if (d && w) {
        Atom aware = XInternAtom(d, "XdndAware", False);
        XDeleteProperty(d, w, aware);
        XFlush(d);
    }

    g_registered = 0;
    g_display = NULL;
    g_window = 0;
    g_src_window = 0;
    g_wanted_type = 0;
    g_file_count = 0;
}

/*
 * Pump XDnD events out of LWJGL's queue. MUST be called from the render/main
 * thread - ideally at the HEAD of Minecraft.func_147120_f(), right before
 * Display.update() would drain and discard them.
 */
JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativePollDndEventsX11
  (JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (!g_registered || !g_display) return;

    XEvent ev;
    while (XCheckIfEvent(g_display, &ev, dnd_event_predicate, NULL)) {
        handle_dnd_event(&ev);
    }
}

JNIEXPORT jint JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeGetDroppedFileCount
  (JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return (jint)g_file_count;
}

JNIEXPORT jstring JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeGetDroppedFile
  (JNIEnv *env, jclass clazz, jint index) {
    (void)clazz;
    if (index < 0 || index >= g_file_count) return NULL;
    return (*env)->NewStringUTF(env, g_files[index]);
}

JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeClearDroppedFiles
  (JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    g_file_count = 0;
}
