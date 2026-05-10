/*
 * ModernResourcePackUI - macOS Cocoa drag-drop handler
 *
 * Design notes:
 *   LWJGL 2.9 on macOS creates a native NSWindow / NSOpenGLView through JNI,
 *   and the NSView pointer is never exposed to Java.  No problem — we just
 *   ask Cocoa for it ourselves with [NSApp keyWindow].contentView.
 *
 *   The register path looks up the active LWJGL window, creates a tiny
 *   ObjC DragDropHandler that implements NSDraggingDestination, and hooks
 *   it onto the content view via registerForDraggedTypes:.
 *
 *   Shared file buffer + poll/clear methods follow the same pattern as the
 *   Windows and Linux variants so ResourcePackDropHandler.pollPendingFiles()
 *   works uniformly across all three platforms.
 */
#include <jni.h>
#include <stdio.h>
#include <string.h>

#ifdef __APPLE__
#import <Cocoa/Cocoa.h>
#import <objc/runtime.h>
#endif

#define MAX_FILES 32
#define MAX_PATH_LEN 2048

/* ---- shared file buffer (same pattern as Windows & Linux) ---- */

static char g_files[MAX_FILES][MAX_PATH_LEN];
static int  g_file_count = 0;

#ifdef __APPLE__

static NSView *g_registered_view = nil;
static id       g_handler = nil;          /* DragDropHandler instance, retained */

@interface DragDropHandler : NSObject
@end

@implementation DragDropHandler

- (NSDragOperation)draggingEntered:(id<NSDraggingInfo>)sender {
    NSPasteboard *pboard = [sender draggingPasteboard];
    if ([pboard.types containsObject:NSFilenamesPboardType] ||
        [pboard.types containsObject:NSPasteboardTypeFileURL]) {
        return NSDragOperationCopy;
    }
    return NSDragOperationNone;
}

- (NSDragOperation)draggingUpdated:(id<NSDraggingInfo>)sender {
    return NSDragOperationCopy;
}

- (BOOL)prepareForDragOperation:(id<NSDraggingInfo>)sender {
    return YES;
}

- (BOOL)performDragOperation:(id<NSDraggingInfo>)sender {
    NSPasteboard *pboard = [sender draggingPasteboard];

    /* macOS 10.13+ deprecates NSFilenamesPboardType in favour of
     * NSPasteboardTypeFileURL, but NSFilenamesPboardType still works
     * on every version LWJGL 2.9 supports.  Try the new type first,
     * fall back to the old one. */
    NSArray *files = nil;

    if ([pboard.types containsObject:NSPasteboardTypeFileURL]) {
        NSArray *urls = [pboard readObjectsForClasses:@[[NSURL class]]
                                              options:nil];
        if (urls.count > 0) {
            NSMutableArray *paths = [NSMutableArray arrayWithCapacity:urls.count];
            for (NSURL *url in urls) {
                if ([url isFileURL]) {
                    [paths addObject:url.path];
                }
            }
            files = paths;
        }
    }

    if (!files || files.count == 0) {
        files = [pboard propertyListForType:NSFilenamesPboardType];
    }

    if (files && files.count > 0) {
        int count = (int)files.count;
        if (count > MAX_FILES) count = MAX_FILES;
        for (int i = 0; i < count; i++) {
            NSString *path = files[i];
            if ([path isKindOfClass:[NSURL class]]) continue; /* safety */
            const char *utf8 = [path UTF8String];
            strncpy(g_files[i], utf8, MAX_PATH_LEN - 1);
            g_files[i][MAX_PATH_LEN - 1] = '\0';
        }
        g_file_count = count;
    }

    return YES;
}

- (void)concludeDragOperation:(id<NSDraggingInfo>)sender {
    /* no-op */
}

@end

/* ---- internal helpers ---- */

static NSView *find_lwjgl_content_view(void) {
    /* macOS 10.6+ supports [NSApp windows] which returns all app windows.
     * LWJGL's NSWindow has "LWJGL" in its title, making it easy to find. */
    for (NSWindow *win in [NSApp windows]) {
        if ([win.title containsString:@"LWJGL"] ||
            [win.title containsString:@"Minecraft"]) {
            return win.contentView;
        }
    }

    /* Fallback: the key window (usually the only window for fullscreen MC). */
    NSWindow *key = [NSApp keyWindow];
    if (key) return key.contentView;

    /* Last resort: the first visible window. */
    for (NSWindow *win in [NSApp windows]) {
        if (win.isVisible) return win.contentView;
    }

    return nil;
}

#endif /* __APPLE__ */

/* ---- JNI entry points ---- */

JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeRegisterDragDropMac
  (JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;

#ifdef __APPLE__
    if (g_registered_view) return; /* already registered */

    NSView *view = find_lwjgl_content_view();
    if (!view) {
        fprintf(stderr,
                "[ModernResourcePackUI] Could not locate LWJGL NSView — "
                "drag & drop will not be available.\n");
        return;
    }

    DragDropHandler *handler = [[DragDropHandler alloc] init];
    [view registerForDraggedTypes:@[NSFilenamesPboardType,
                                     NSPasteboardTypeFileURL]];

    /* Tie the handler's lifetime to the view so ARC doesn't collect it. */
    objc_setAssociatedObject(view,
                             "mrpui_dragdrop_handler",
                             handler,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);

    g_registered_view = view;
    g_handler = handler;
    g_file_count = 0;

    NSLog(@"[ModernResourcePackUI] Registered drag & drop on NSView %p", (void *)view);
#else
    fprintf(stderr,
            "[ModernResourcePackUI] Mac native code compiled without __APPLE__ "
            "— something is wrong with the build.\n");
#endif
}

JNIEXPORT void JNICALL
Java_decok_dfcdvadstf_modernresourcepack_utils_handlers_ResourcePackDropHandler_nativeUnregisterDragDropMac
  (JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;

#ifdef __APPLE__
    if (g_registered_view) {
        [g_registered_view unregisterDraggedTypes];
        objc_setAssociatedObject(g_registered_view,
                                 "mrpui_dragdrop_handler",
                                 nil,
                                 OBJC_ASSOCIATION_ASSIGN);
    }
    g_registered_view = nil;
    g_handler = nil;
    g_file_count = 0;
#endif
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
