package com.stream.frame.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import com.ai.tensorflow.personTracking.PersonTrackerImpl;
import com.stream.frame.R;
import com.stream.frame.utils.CameraSizeUtil;
import com.stream.frame.utils.CompareSizesByArea;
import com.stream.frame.utils.ImageUtil;
import com.stream.frame.view.AutoFitTextureView;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2Activity extends AppCompatActivity implements TextureView.SurfaceTextureListener{
    private static final String TAG = Camera2Activity.class.getSimpleName();

    private AutoFitTextureView mPreviewView;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private Size mPreviewSize;
    private CaptureRequest.Builder mPreviewBuilder;
    private ImageReader mImageReader;
    private static CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;

    /**gu 0: back 1: front camera*/
    private String mCameraId = "0";

    public byte[] mImageBytes;
    private static int mSensorOrientation;
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private int pic_name = 1;

    //==============================================================================================
    private String sdcard =  Environment.getExternalStoragePublicDirectory("")+"";
    // param
    private String rootPath = sdcard + File.separator + "tensorflow-lite-demo/tracking";
    private String logFolder = rootPath +  File.separator + "log";   //the path to save log file
    private PersonTrackerImpl personTracker;
    //==============================================================================================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mPreviewView = findViewById(R.id.texture);
        Log.e(TAG, "onCreate: ------------------------------------");

        //===============================================================
        /**创建AI识别*/
        /**Create AI distinguish*/
        personTracker = new PersonTrackerImpl(logFolder);
        personTracker.createPersonTracker(getAssets());
        //===============================================================
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.e(TAG, "onResume: ----camera fragment resume");

        mImageBytes = null;

        startBackgroundThread();
        if (mPreviewView.isAvailable()) {
            openCamera(mPreviewView.getWidth(), mPreviewView.getHeight());
        } else {
            mPreviewView.setSurfaceTextureListener(this);
        }
    }

    /**很多过程都变成了异步的了，所以这里需要一个子线程的looper*/
    private void startBackgroundThread() {
        mHandlerThread = new HandlerThread(Camera2Activity.class.getName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    /**这个方法要注意一下，因为每有一帧画面，都会回调一次此方法*/
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    /**打开相机*/
    private void openCamera(int width, int height) {
        try {
            /**获得所有摄像头的管理者CameraManager*/
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            /**获得某个摄像头的特征，支持的参数*/
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(mCameraId);
            //支持的STREAM CONFIGURATION
            //==================================================================================
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            //==================================================================================

            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Log.e(TAG, "openCamera: ----mSensorOrientation:" + mSensorOrientation);

            //=====================================================================================
            /**解决预览图像拉伸问题*/
            // For still image captures, we use the largest available size.
            Size largest = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new CompareSizesByArea());

            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
            //noinspection ConstantConditions
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    Log.e(TAG, "Display rotation is invalid: " + displayRotation);
            }
            Point displaySize = new Point();
            getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;
            if (swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }
            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }
            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            mPreviewSize = CameraSizeUtil.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, largest);

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mPreviewView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mPreviewView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            //=====================================================================================

            configureTransform(width, height);

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(Camera2Activity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                if (mHandlerThread != null && mHandlerThread.isAlive() && !mHandlerThread.isInterrupted()) {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mHandler);
                }
            }  else {
                ActivityCompat.requestPermissions(Camera2Activity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 1);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            try {
                mCameraDevice = camera;
                startPreview(camera);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }
    };

    /**开始预览，主要是camera.createCaptureSession这段代码很重要，创建会话*/
    /**Start preview, The important code is camera.createCaptureSession,there are create session*/
    private void startPreview(CameraDevice camera) throws CameraAccessException {

        if (null == mCameraDevice) {
            return;
        }

        SurfaceTexture texture = mPreviewView.getSurfaceTexture();

        /**这里设置的就是预览大小*/
        /**There is setting the size of preview*/
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            /**设置捕获请求为预览，这里还有拍照啊，录像等*/
            mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        /**就是在这里，通过这个set(key,value)方法，设置曝光啊，自动聚焦等参数！！ 如下举例：*/
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

        /**
         * 此处还有很多格式，比如我所用到YUV等
         * 最大的图片数，mImageReader里能获取到图片数，但是实际中是2+1张图片，就是多一张,"30"代表每秒取30帧的图片
         * */
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 40);

        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);

        mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);

        /**这里一定分别add两个surface，一个Textureview的，一个ImageReader的，如果没add，会造成没摄像头预览，或者没有ImageReader的那个回调！！*/
        mPreviewBuilder.addTarget(surface);
        mPreviewBuilder.addTarget(mImageReader.getSurface());

        if ( null == mCameraDevice) {
            return;
        }
        camera.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallback, mHandler);
    }

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            try {
                //The camera is already closed
                if (mCameraDevice == null) {
                    return;
                }
                mCaptureSession = session;
                updatePreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };

    private void updatePreview() {
        if (mHandler == null) return;
        mHandler.post(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
                try {
                    if (mCameraDevice == null) {
                        return;
                    }
                    mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            byte[] data68 = ImageUtil.getBytesFromImageAsType(image,2);
            int[] rgb = ImageUtil.decodeYUV420SP(data68, imageWidth, imageHeight);

            Bitmap bitmap2 = Bitmap.createBitmap(rgb, 0, imageWidth,
                    imageWidth, imageHeight,
                    android.graphics.Bitmap.Config.ARGB_8888);

            //================================================================================
            personTracker.personStreamDetect(bitmap2);
            //================================================================================

//            String picture_name = pic_name + ".jpg";
//            System.out.println(picture_name);
//            FileUtil.saveBitmap(bitmap2, picture_name);/**保存原始帧数据图片*/
//            pic_name = pic_name + 1;
//            System.out.println("aaaaaaaaaaaaaaaaaaaaaaaaassssssssssssssssssssssssssss");

            // 先判断是否已经回收
            if(bitmap2 != null && !bitmap2.isRecycled()){
                // 回收并且置为null
                bitmap2.recycle();
            }
            System.gc();
            image.close();

//            try {
//                Thread.sleep(3000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        closeCamera();
    }

    private void closeCamera() {

        try {
            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {//注意关闭顺序，先
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {//注意关闭顺序，后
            mImageReader.close();
            mImageReader = null;
        }
        stopBackgroundThread();

        //=============================================================
        personTracker.closePersonTracker();
        //=============================================================
    }

    private void stopBackgroundThread() {
        try {
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
                mHandlerThread.join();
                mHandlerThread = null;
            }
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mPreviewView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        //RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float widthHeight = (float) mPreviewSize.getWidth() / (float) mPreviewSize.getHeight();
        int bottom = (int) (viewWidth * widthHeight);
        RectF bufferRect = new RectF(0, 0, viewWidth, bottom);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        } else {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        }
        mPreviewView.setTransform(matrix);
    }
}
