package com.cgfay.facedetect.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cgfay.facedetect.listener.FaceTrackerCallback;
import com.cgfay.facedetect.utils.ConUtil;
import com.cgfay.facedetect.utils.SensorEventUtil;
import com.cgfay.landmark.LandmarkEngine;
import com.cgfay.landmark.OneFace;
import com.zeusee.main.hyperlandmark.jni.Face;
import com.zeusee.main.hyperlandmark.jni.FaceTracking;

import java.util.List;

import androidx.annotation.Nullable;

/**
 * 人脸检测器
 */
public final class FaceTracker {

    private static final String TAG = "FaceTracker";
    private static final boolean VERBOSE = false;

    private final Object mSyncFence = new Object();

    // 人脸检测参数
    private FaceTrackParam mFaceTrackParam;

    // 检测线程
    private TrackerThread mTrackerThread;

    private static class FaceTrackerHolder {
        private static FaceTracker instance = new FaceTracker();
    }

    private FaceTracker() {
        mFaceTrackParam = FaceTrackParam.getInstance();
    }

    public static FaceTracker getInstance() {
        return FaceTrackerHolder.instance;
    }

    /**
     * 检测回调
     *
     * @param callback
     * @return
     */
    public FaceTrackerBuilder setFaceCallback(FaceTrackerCallback callback) {
        return new FaceTrackerBuilder(this, callback);
    }

    /**
     * 准备检测器
     */
    void initTracker() {
        synchronized (mSyncFence) {
            mTrackerThread = new TrackerThread("FaceTrackerThread");
            mTrackerThread.start();
            mTrackerThread.waitUntilReady();
        }
    }

    /**
     * 初始化人脸检测
     *
     * @param context     上下文
     * @param orientation 图像角度
     * @param width       图像宽度
     * @param height      图像高度
     */
    public void prepareFaceTracker(Context context, int orientation, int width, int height) {
        synchronized (mSyncFence) {
            if (mTrackerThread != null) {
                mTrackerThread.prepareFaceTracker(context, orientation, width, height);
            }
        }
    }

    /**
     * 检测人脸
     *
     */
    public void trackFace(byte[] data, int width, int height) {
        synchronized (mSyncFence) {
            if (mTrackerThread != null) {
                mTrackerThread.trackFace(data, width, height);
            }
        }
    }

    /**
     * 销毁检测器
     */
    public void destroyTracker() {
        synchronized (mSyncFence) {
            mTrackerThread.quitSafely();
        }
    }

    /**
     * 是否后置摄像头
     *
     * @param backCamera
     * @return
     */
    public FaceTracker setBackCamera(boolean backCamera) {
        mFaceTrackParam.isBackCamera = backCamera;
        return this;
    }

    /**
     * 是否允许3D姿态角
     *
     * @param enable
     * @return
     */
    public FaceTracker enable3DPose(boolean enable) {
        mFaceTrackParam.enable3DPose = enable;
        return this;
    }

    /**
     * 是否允许区域检测
     *
     * @param enable
     * @return
     */
    public FaceTracker enableROIDetect(boolean enable) {
        mFaceTrackParam.enableROIDetect = enable;
        return this;
    }

    /**
     * 是否允许106个关键点
     *
     * @param enable
     * @return
     */
    public FaceTracker enable106Points(boolean enable) {
        mFaceTrackParam.enable106Points = enable;
        return this;
    }

    /**
     * 是否允许多人脸检测
     *
     * @param enable
     * @return
     */
    public FaceTracker enableMultiFace(boolean enable) {
        mFaceTrackParam.enableMultiFace = enable;
        return this;
    }

    /**
     * 是否允许人脸年龄检测
     *
     * @param enable
     * @return
     */
    public FaceTracker enableFaceProperty(boolean enable) {
        mFaceTrackParam.enableFaceProperty = enable;
        return this;
    }

    /**
     * 最小检测人脸大小
     *
     * @param size
     * @return
     */
    public FaceTracker minFaceSize(int size) {
        mFaceTrackParam.minFaceSize = size;
        return this;
    }

    /**
     * 检测时间间隔
     *
     * @param interval
     * @return
     */
    public FaceTracker detectInterval(int interval) {
        mFaceTrackParam.detectInterval = interval;
        return this;
    }

    /**
     * 检测模式
     *
     * @param mode
     * @return
     */
    public FaceTracker trackMode(int mode) {
        mFaceTrackParam.trackMode = mode;
        return this;
    }

//    /**
//     * Face++SDK联网请求验证
//     */
//    public static void requestFaceNetwork(Context context) {
//        if (Facepp.getSDKAuthType(ConUtil.getFileContent(context, R.raw
//                .megviifacepp_0_4_7_model)) == 2) {// 非联网授权
//            FaceTrackParam.getInstance().canFaceTrack = true;
//            return;
//        }
//        final LicenseManager licenseManager = new LicenseManager(context);
//        licenseManager.setExpirationMillis(Facepp.getApiExpirationMillis(context,
//                ConUtil.getFileContent(context, R.raw.megviifacepp_0_4_7_model)));
//        String uuid = ConUtil.getUUIDString(context);
//        long apiName = Facepp.getApiName();
//        licenseManager.setAuthTimeBufferMillis(0);
//        licenseManager.takeLicenseFromNetwork(uuid, FaceppConstraints.API_KEY, FaceppConstraints.API_SECRET, apiName,
//                LicenseManager.DURATION_30DAYS, "Landmark", "1", true,
//                new LicenseManager.TakeLicenseCallback() {
//                    @Override
//                    public void onSuccess() {
//                        if (VERBOSE) {
//                            Log.d(TAG, "success to register license!");
//                        }
//                        FaceTrackParam.getInstance().canFaceTrack = true;
//                    }
//
//                    @Override
//                    public void onFailed(int i, byte[] bytes) {
//                        if (VERBOSE) {
//                            Log.d(TAG, "Failed to register license!");
//                        }
//                        FaceTrackParam.getInstance().canFaceTrack = false;
//                    }
//                });
//    }


    /**
     * 检测线程
     */
    private static class TrackerThread extends Thread {

        private final Object mStartLock = new Object();
        private boolean mReady = false;

        // 人脸检测实体
//        private Facepp facepp;
        // 传感器监听器
        private SensorEventUtil mSensorUtil;

        private Looper mLooper;
        private @Nullable
        Handler mHandler;

        public TrackerThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (this) {
                mLooper = Looper.myLooper();
                notifyAll();
                mHandler = new Handler(mLooper);
            }
            synchronized (mStartLock) {
                mReady = true;
                mStartLock.notify();
            }
            Looper.loop();
            synchronized (this) {
                release();
                mHandler.removeCallbacksAndMessages(null);
                mHandler = null;
            }
            synchronized (mStartLock) {
                mReady = false;
            }
        }

        /**
         * 等待线程准备完成
         */
        public void waitUntilReady() {
            synchronized (mStartLock) {
                while (!mReady) {
                    try {
                        mStartLock.wait();
                    } catch (InterruptedException e) {
//
                    }
                }
            }
        }

        /**
         * 安全退出
         *
         * @return
         */
        public boolean quitSafely() {
            Looper looper = getLooper();
            if (looper != null) {
                looper.quitSafely();
                return true;
            }
            return false;
        }

        /**
         * 获取Looper
         *
         * @return
         */
        public Looper getLooper() {
            if (!isAlive()) {
                return null;
            }
            synchronized (this) {
                while (isAlive() && mLooper == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            return mLooper;
        }

        /**
         * 初始化人脸检测
         *
         * @param context     上下文
         * @param orientation 图像角度
         * @param width       图像宽度
         * @param height      图像高度
         */
        public void prepareFaceTracker(final Context context, final int orientation,
                                       final int width, final int height) {
            waitUntilReady();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    internalPrepareFaceTracker(context, orientation, width, height);
                }
            });
        }

        /**
         * 检测人脸
         *
         * @param data   图像数据， NV21 或者 RGBA格式
         * @param width  图像宽度
         * @param height 图像高度
         */
        public void trackFace(final byte[] data, final int width, final int height) {
            waitUntilReady();
            mHandler.post(() -> internalTrackFace(data, width, height));
        }


        /**
         * 释放资源
         */
        private void release() {
            ConUtil.releaseWakeLock();
//            if (facepp != null) {
//                facepp.release();
//                facepp = null;
//            }
        }

        /**
         * 初始化人脸检测
         *
         * @param context     上下文
         * @param orientation 图像角度，预览时设置相机的角度，如果是静态图片，则为0
         * @param width       图像宽度
         * @param height      图像高度
         */
        private synchronized void internalPrepareFaceTracker(Context context, int orientation, int width, int height) {
            FaceTrackParam faceTrackParam = FaceTrackParam.getInstance();
            if (!faceTrackParam.canFaceTrack) {
                return;
            }
            release();
//            facepp = new Facepp();
            if (mSensorUtil == null) {
                mSensorUtil = new SensorEventUtil(context);
            }
            ConUtil.acquireWakeLock(context);
            if (!faceTrackParam.previewTrack) {
                faceTrackParam.rotateAngle = orientation;
            } else {
                faceTrackParam.rotateAngle = faceTrackParam.isBackCamera ? orientation : 360 - orientation;
            }

            int left = 0;
            int top = 0;
            int right = width;
            int bottom = height;
            // 限定检测区域
            if (faceTrackParam.enableROIDetect) {
                float line = height * faceTrackParam.roiRatio;
                left = (int) ((width - line) / 2.0f);
                top = (int) ((height - line) / 2.0f);
                right = width - left;
                bottom = height - top;
            }

//            facepp.init(context, ConUtil.getFileContent(context, R.raw.megviifacepp_0_4_7_model));
//            Facepp.FaceppConfig faceppConfig = facepp.getFaceppConfig();
//            faceppConfig.interval = faceTrackParam.detectInterval;
//            faceppConfig.minFaceSize = faceTrackParam.minFaceSize;
//            faceppConfig.roi_left = left;
//            faceppConfig.roi_top = top;
//            faceppConfig.roi_right = right;
//            faceppConfig.roi_bottom = bottom;
//            faceppConfig.one_face_tracking = faceTrackParam.enableMultiFace ? 0 : 1;
//            faceppConfig.detectionMode = faceTrackParam.trackMode;
//            facepp.setFaceppConfig(faceppConfig);
        }

        /**
         * 检测人脸
         *
         * @param data 图像数据，预览时为NV21，静态图片则为RGBA格式
         */
        private synchronized void internalTrackFace(byte[] data, int width, int height) {
            FaceTrackParam faceTrackParam = FaceTrackParam.getInstance();
            if (!faceTrackParam.canFaceTrack
//                    || facepp == null
            ) {
                LandmarkEngine.getInstance().setFaceSize(0);
                if (faceTrackParam.trackerCallback != null) {
                    faceTrackParam.trackerCallback.onTrackingFinish();
                }
                return;
            }

            // 调整检测监督
            long faceDetectTime_action = System.currentTimeMillis();
            // 获取设备旋转
            int orientation = faceTrackParam.previewTrack ? mSensorUtil.orientation : 0;
            int rotation = 0;
            if (orientation == 0) {         // 0
                rotation = faceTrackParam.rotateAngle;
            } else if (orientation == 1) {  // 90
                rotation = 0;
            } else if (orientation == 2) {  // 270
                rotation = 180;
            } else if (orientation == 3) {  // 180
                rotation = 360 - faceTrackParam.rotateAngle;
            }
            // 设置旋转角度
//            Facepp.FaceppConfig faceppConfig = facepp.getFaceppConfig();
//            if (faceppConfig.rotation != rotation) {
//                faceppConfig.rotation = rotation;
//                facepp.setFaceppConfig(faceppConfig);
//            }

            // 人脸检测
//            final Facepp.Face[] faces = facepp.detect(data, width, height,
//                    faceTrackParam.previewTrack ? Facepp.IMAGEMODE_NV21 : Facepp.IMAGEMODE_RGBA);
            FaceTracking.getInstance().Update(data, height, width);
            List<Face> faces = FaceTracking.getInstance().getTrackingInfo();

            // 计算检测时间
            if (VERBOSE) {
                final long algorithmTime = System.currentTimeMillis() - faceDetectTime_action;
                Log.d("onFaceTracking", "track time = " + algorithmTime);
            }

            // 设置旋转方向
            LandmarkEngine.getInstance().setOrientation(orientation);
            // 设置是否需要翻转
            boolean needFlip = faceTrackParam.previewTrack && !faceTrackParam.isBackCamera;
            LandmarkEngine.getInstance().setNeedFlip(needFlip);

            // 计算人脸关键点
            if (faces != null && faces.size() > 0) {
                for (int index = 0; index < faces.size(); index++) {
                    // 关键点个数
//                    if (faceTrackParam.enable106Points) {
//                        facepp.getLandmark(faces.get(index), Facepp.FPP_GET_LANDMARK106);
//                    } else {
//                        facepp.getLandmark(faces[index], Facepp.FPP_GET_LANDMARK81);
//                    }
                    // 获取姿态角信息
//                    if (faceTrackParam.enable3DPose) {
//                        facepp.get3DPose(faces[index]);
//                    }
                    Face face = faces.get(index);
                    OneFace oneFace = LandmarkEngine.getInstance().getOneFace(index);
                    // 是否检测性别年龄属性
//                    if (faceTrackParam.enableFaceProperty) {
//                        facepp.getAgeGender(face);
//                        oneFace.gender = face.female > face.male ? OneFace.GENDER_WOMAN
//                                : OneFace.GENDER_MAN;
//                        oneFace.age = Math.max(face.age, 1);
//                    } else {
//                        oneFace.gender = -1;
//                        oneFace.age = -1;
//                    }
//
                    // 姿态角和置信度
                    oneFace.pitch = face.pitch;
                    if (faceTrackParam.isBackCamera) {
                        oneFace.yaw = -face.yaw;
                    } else {
                        oneFace.yaw = face.yaw;
                    }
                    oneFace.roll = face.roll;
                    if (faceTrackParam.previewTrack) {

                        if (faceTrackParam.isBackCamera) {
                            oneFace.roll = (float) (Math.PI / 2.0f + oneFace.roll);
                        } else {
                            oneFace.roll = (float) (Math.PI / 2.0f - face.roll);
                        }
                    }
//                    oneFace.confidence = face.isStable;

                    // 预览状态下，宽高交换
                    if (faceTrackParam.previewTrack) {
                        if (orientation == 1 || orientation == 2) {
                            int temp = width;
                            width = height;
                            height = temp;
                        }
                    }
//
                    // 获取一个人的关键点坐标
                    if (oneFace.vertexPoints == null || oneFace.vertexPoints.length != face.landmarks.length) {
                        oneFace.vertexPoints = new float[face.landmarks.length];
                    }
                    for (int i = 0; i + 1 < face.landmarks.length; i += 2) {
                        // orientation = 0、3 表示竖屏，1、2 表示横屏
                        float x = ((face.landmarks[i] / (float) height) * 2) - 1;
                        float y = ((face.landmarks[i + 1] / (float) width) * 2) - 1;
//                        float x = face.landmarks[i];
//                        float y = face.landmarks[i + 1];
                        float[] point = new float[]{x, -y};
                        if (orientation == 1) {
                            if (faceTrackParam.previewTrack && faceTrackParam.isBackCamera) {
                                point[0] = -y;
                                point[1] = -x;
                            } else {
                                point[0] = y;
                                point[1] = x;
                            }
                        } else if (orientation == 2) {
                            if (faceTrackParam.previewTrack && faceTrackParam.isBackCamera) {
                                point[0] = y;
                                point[1] = x;
                            } else {
                                point[0] = -y;
                                point[1] = -x;
                            }
                        } else if (orientation == 3) {
                            point[0] = -x;
                            point[1] = y;
                        }
                        // 顶点坐标
                        int vertexIndex = transform(i / 2);
                        if (faceTrackParam.previewTrack) {
                            if (faceTrackParam.isBackCamera) {
                                oneFace.vertexPoints[vertexIndex * 2] = point[0];
                            } else {
                                oneFace.vertexPoints[vertexIndex * 2] = -point[0];
                            }
                        } else { // 非预览状态下，左右不需要翻转
                            oneFace.vertexPoints[vertexIndex * 2] = point[0];
                        }
                        oneFace.vertexPoints[vertexIndex * 2 + 1] = point[1];
                    }
                    // 插入人脸对象
                    LandmarkEngine.getInstance().putOneFace(index, oneFace);
                }
            }
            // 设置人脸个数
            LandmarkEngine.getInstance().setFaceSize(faces != null ? faces.size() : 0);
            // 检测完成回调
            if (faceTrackParam.trackerCallback != null) {
                faceTrackParam.trackerCallback.onTrackingFinish();
            }
        }

        private int transform(int i) {
            switch (i) {
                case 0:
                    return 16;
                case 1:
                    return 53;
                case 2:
                    return 101;
                case 3:
                    return 73;
                case 4:
                    return 91;
                case 5:
                    return 6;
                case 6:
                    return 5;
                case 7:
                    return 4;
                case 8:
                    return 3;
                case 9:
                    return 2;
                case 10:
                    return 1;
                case 11:
                    return 0;
                case 12:
                    return 57;
                case 13:
                    return 32;
                case 14:
                    return 30;
                case 15:
                    return 31;
                case 16:
                    return 28;
                case 17:
                    return 29;
                case 18:
                    return 26;
                case 19:
                    return 33;
                case 20:
                    return 61;
                case 21:
                    return 43;
                case 22:
                    return 45;
                case 23:
                    return 44;
                case 24:
                    return 38;
                case 25:
                    return 99;
                case 26:
                    return 88;
                case 27:
                    return 58;
                case 28:
                    return 37;
                case 29:
                    return 35;
                case 30:
                    return 92;
                case 31:
                    return 82;
                case 32:
                    return 93;
                case 33:
                    return 89;
                case 34:
                    return 72;
                case 35:
                    return 78;
                case 36:
                    return 98;
                case 37:
                    return 85;
                case 38:
                    return 87;
                case 39:
                    return 86;
                case 40:
                    return 97;
                case 41:
                    return 75;
                case 42:
                    return 100;
                case 43:
                    return 76;
                case 44:
                    return 68;
                case 45:
                    return 84;
                case 46:
                    return 49;
                case 47:
                    return 62;
                case 48:
                    return 71;
                case 49:
                    return 24;
                case 50:
                    return 90;
                case 51:
                    return 63;
                case 52:
                    return 77;
                case 53:
                    return 54;
                case 54:
                    return 69;
                case 55:
                    return 104;
                case 56:
                    return 25;
                case 57:
                    return 12;
                case 58:
                    return 66;
                case 59:
                    return 55;
                case 60:
                    return 67;
                case 61:
                    return 96;
                case 62:
                    return 64;
                case 63:
                    return 103;
                case 64:
                    return 94;
                case 65:
                    return 95;
                case 66:
                    return 8;
                case 67:
                    return 56;
                case 68:
                    return 27;
                case 69:
                    return 46;
                case 70:
                    return 40;
                case 71:
                    return 70;
                case 72:
                    return 74;
                case 73:
                    return 39;
                case 74:
                    return 42;
                case 75:
                    return 41;
                case 76:
                    return 15;
                case 77:
                    return 14;
                case 78:
                    return 13;
                case 79:
                    return 36;
                case 80:
                    return 11;
                case 81:
                    return 10;
                case 82:
                    return 9;
                case 83:
                    return 65;
                case 84:
                    return 34;
                case 85:
                    return 60;
                case 86:
                    return 51;
                case 87:
                    return 50;
                case 88:
                    return 47;
                case 89:
                    return 48;
                case 90:
                    return 80;
                case 91:
                    return 79;
                case 92:
                    return 81;
                case 93:
                    return 83;
                case 94:
                    return 52;
                case 95:
                    return 18;
                case 96:
                    return 19;
                case 97:
                    return 17;
                case 98:
                    return 22;
                case 99:
                    return 23;
                case 100:
                    return 20;
                case 101:
                    return 21;
                case 102:
                    return 7;
                case 103:
                    return 102;
                case 104:
                    return 59;
                case 105:
                    return 105;
                default:
                    return -1;
            }
        }
    }

}
