package decok.dfcdvadstf.modernresourcepack.utils.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.I18n;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ResourcePackDragHandler {
    
    // 用于存储拖拽的文件路径
    private static String[] droppedFiles = null;
    
    public static void initializeDragDrop() {
        // 为Display设置拖拽回调（这需要额外的原生库支持）
        // 由于Minecraft 1.7.10没有原生拖拽支持，我们将使用一个模拟方法
    }
    
    public static boolean hasDroppedFiles() {
        return droppedFiles != null && droppedFiles.length > 0;
    }
    
    public static String[] getDroppedFiles() {
        return droppedFiles;
    }
    
    public static void setDroppedFiles(String[] files) {
        droppedFiles = files;
    }
    
    public static void handleDragAndDrop(GuiScreenResourcePacks gui) {
        if (hasDroppedFiles()) {
            // 显示确认对话框
            showConfirmationDialog(gui, getDroppedFiles());
            // 清空已处理的文件
            setDroppedFiles(null);
        }
    }
    
    private static void showConfirmationDialog(GuiScreenResourcePacks gui, String[] files) {
        // 提取文件名
        List<String> fileNames = new ArrayList<>();
        for (String filePath : files) {
            File file = new File(filePath);
            fileNames.add(file.getName());
        }
        
        // 创建确认对话框
        String[] namesArray = fileNames.toArray(new String[0]);
        decok.dfcdvadstf.modernresourcepack.gui.GuiResourcePackConfirm confirmGui = 
            new decok.dfcdvadstf.modernresourcepack.gui.GuiResourcePackConfirm(
                gui, 
                namesArray, 
                I18n.format("resourcepack.install.confirm.title"),
                I18n.format("resourcepack.install.confirm.message", String.join("\n", namesArray))
            );
        
        Minecraft.getMinecraft().displayGuiScreen(confirmGui);
    }
}