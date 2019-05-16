package com.stream.frame.utils;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {

    //新添加的保存到手机的方法
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
}
