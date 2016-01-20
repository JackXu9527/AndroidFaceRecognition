package com.wistron.opencvtest;                                                                                                                

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.graphics.Paint.Align;
import android.view.WindowManager;
import android.os.Bundle;
import android.os.Environment;
import android.net.Uri;
import android.util.Log;
import android.provider.MediaStore.Images.Media;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.widget.ImageView;
import android.widget.Button;
import android.view.WindowManager;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.Surface;
import android.view.OrientationEventListener;


/**
 * This class uses Utility functions written for the Camera module of Android.
 * These snippets have been taken from:
 *
 *      https://android.googlesource.com/platform/packages/apps/Camera/
 *
 *  Android code is released under terms of the Apache 2.0 license. You can obtain the copy in
 *  the assets folder coming with this project.
 *
 *  Copyright (C) 2011 The Android Open Source Project
 *
 */
public class Util2 {

    // Orientation hysteresis amount used in rounding, in degrees
    private static final int ORIENTATION_HYSTERESIS = 5;

    public static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: return 0;
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
        }
        return 0;
    }

    public static int getDisplayOrientation(int degrees, int cameraId) {
        // See android.hardware.Camera.setDisplayOrientation for
        // documentation.
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    public static void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
                                     int viewWidth, int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }

    public static int roundOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation = false;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
            int dist = Math.abs(orientation - orientationHistory);
            dist = Math.min( dist, 360 - dist );
            changeOrientation = ( dist >= 45 + ORIENTATION_HYSTERESIS );
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }
}
