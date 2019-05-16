package com.stream.frame.utils;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {

    /**新添加的保存到手机的方法*/
    @SuppressLint("SdCardPath")
    public static void saveBitmap(Bitmap bitmap, String bitName) {
        File appDir = new File(Environment.getExternalStorageDirectory()+"/"+"smartPhoneCamera", "Images");
        if (!appDir.exists()) {
            appDir.mkdir();
        }
        File file = new File(appDir, bitName);     // 创建文件
        try {                                       // 写入图片
            FileOutputStream fos = new FileOutputStream(file);
            Bitmap endBit = Bitmap.createScaledBitmap(bitmap, 720, 1280, true); //创建新的图像大小
            endBit.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
            endBit.recycle();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除文件夹中的内容,不删除文件夹本身
     * @param path
     */
    public static void deleteDirectoryContent(String path){
        Log.w("test","deleteDirectory.."+path);
        File file=new File(path);
        if(!file.exists()){
            return;
        }
        String fPath=file.getAbsolutePath();
        if(file.isDirectory()){
            String[] files=getDirectoryFiles(path);
            if(files==null){
                deleteFile(path);
                return;
            }
            for(String str:files){
                str=fPath+"/"+str;
                file=new File(str);
                if(file.isDirectory()){
                    deleteDirectory(str);
                }else if(file.isFile()){
                    deleteFile(str);
                }
            }
//          deleteFile(path);
        }else if(file.isFile()){
            deleteFile(path);
        }
    }

    /**
     * 删除文件夹中的内容
     * @param path
     */
    public static void deleteDirectory(String path){
        Log.w("test","deleteDirectory.."+path);
        File file=new File(path);
        if(!file.exists()){
            return;
        }
        String fPath=file.getAbsolutePath();
        if(file.isDirectory()){
            String[] files=getDirectoryFiles(path);
            if(files==null){
                deleteFile(path);
                return;
            }
            for(String str:files){
                str=fPath+"/"+str;
                file=new File(str);
                if(file.isDirectory()){
                    deleteDirectory(str);
                }else if(file.isFile()){
                    deleteFile(str);
                }
            }
            deleteFile(path);
        }else if(file.isFile()){
            deleteFile(path);
        }
    }

    /**
     * 删除指定路径的文件
     * @param filePath
     *        文件路径
     */
    public static void deleteFile(String filePath){
        Log.w("test","deleteFile:filePath="+filePath);
        if(filePath==null){
            return;
        }
        File file=new File(filePath);
        if(file.exists()){
            file.delete();
        }
    }


    /**
     * 获取文件夹下面的所有文件
     * @param path
     * @return
     */
    public static String[] getDirectoryFiles(String path){
        if(path==null){
            return null;
        }
        File file=new File(path);
        if(!file.exists()){
            return null;
        }
        String[] files=file.list();
        if(files==null || files.length<=0){
            return null;
        }
        return files;
    }
}
