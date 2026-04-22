#include <jni.h>
#include <stdio.h>
#include <string.h>

#ifdef __APPLE__
#import <Cocoa/Cocoa.h>
#import <objc/runtime.h>
#endif

#define MAX_FILES 16
#define MAX_PATH_LEN 1024

static char g_files[MAX_FILES][MAX_PATH_LEN];
static int g_fileCount = 0;

#ifdef __APPLE__
static id g_oldDragHandler = nil;

@interface DragDropHandler : NSObject
@end

@implementation DragDropHandler

- (NSDragOperation)draggingEntered:(id<NSDraggingInfo>)sender {
    return NSDragOperationCopy;
}

- (NSDragOperation)draggingUpdated:(id<NSDraggingInfo>)sender {
    return NSDragOperationCopy;
}

- (BOOL)prepareForDragOperation:(id<NSDraggingInfo>)sender {
    return YES;
}

- (BOOL)performDragOperation:(id<NSDraggingInfo>)sender {
    id<NSPasteboard> pboard = [sender draggingPasteboard];
    NSArray *files = [pboard propertyListForType:NSFilenamesPboardType];
    
    if (files && [files count] > 0) {
        int count = (int)[files count];
        if (count > MAX_FILES) count = MAX_FILES;
        
        for (int i = 0; i < count; i++) {
            NSString *path = [files objectAtIndex:i];
            strncpy(g_files[i], [path UTF8String], MAX_PATH_LEN - 1);
            g_files[i][MAX_PATH_LEN - 1] = '\0';
        }
        g_fileCount = count;
    }
    
    return YES;
}

- (void)concludeDragOperation:(id<NSDraggingInfo>)sender {
}

@end
#endif

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_handlers_ResourcePackDropHandler_nativeRegisterDragDrop
  (JNIEnv *env, jclass clazz, jlong viewPtr) {
#ifdef __APPLE__
    NSView *view = (NSView *)viewPtr;
    if (!view) return;
    
    DragDropHandler *handler = [[DragDropHandler alloc] init];
    [view registerForDraggedTypes:[NSArray arrayWithObject:NSFilenamesPboardType]];
    
    objc_setAssociatedObject(view, "dragdrop_handler", handler, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    g_fileCount = 0;
#endif
}

JNIEXPORT void JNICALL Java_decok_dfcdvadstf_modernresourcepack_handlers_ResourcePackDropHandler_nativeUnregisterDragDrop
  (JNIEnv *env, jclass clazz, jlong viewPtr) {
#ifdef __APPLE__
    NSView *view = (NSView *)viewPtr;
    if (!view) return;
    
    [view unregisterDraggedTypes];
    objc_setAssociatedObject(view, "dragdrop_handler", nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    g_fileCount = 0;
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
