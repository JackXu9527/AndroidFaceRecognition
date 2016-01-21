package com.wistron.opencvtest;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;                                                                                                                
import android.content.ContextWrapper; 
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat; 
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.YuvImage;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode; 
import android.graphics.Paint; 
import android.graphics.RectF; 
import android.graphics.Rect; 
import android.graphics.Matrix; 
import android.graphics.Paint.Align; 
import android.view.WindowManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.HandlerThread; 
import android.os.Handler;
import android.os.Message;
import android.net.Uri;
import android.util.Log;
import android.provider.MediaStore.Images.Media;    
import android.hardware.SensorManager;
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
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.View.OnClickListener; 
import java.io.IOException;                                                                                                                    
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.File;                                                                                                                           
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.FilenameFilter;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.lang.Thread;  
import static org.bytedeco.javacpp.opencv_face.*;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;


public class MainActivity extends Activity implements SurfaceHolder.Callback , Camera.PreviewCallback, Handler.Callback{

    private static final String TAG = "opencvtest";
    private static final String TRAINDIR = "/storage/sdcard0/DCIM/Camera/facetraining/";
//    private static final float DW = 1.75f; //320*240
 //   private static final float DH = 1.34f;
    private static final float DW = 0.89f; //640*480
    private static final float DH = 0.68f;
    public Camera camera;
    public SurfaceView surfaceView;
    public SurfaceHolder surfaceHolder;
    private ImageView showImage;   
    boolean previewing = false;      
    private FaceRecognizer faceRecognizer;
    public boolean isFacePredict = false;
    public DrawingView drawingView;
    private int mDisplayRotation;
    private int mDisplayOrientation;
    private int mOrientation;
    private int mOrientationCompensation;
    private OrientationEventListener mOrientationEventListener;

    private Paint paint = new Paint();
    private List<People> mPeople;
    private HandlerThread mThreadPredict;
    private Handler mHandler;
    static final int MSG_PREDICT_START = 101;
    public String mImagePath;

    FaceDetectionListener faceDetectionListener = new FaceDetectionListener(){
        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            drawingView.setFaces(faces);
    }};
    
    public void setPredict(Bitmap picture, int id, People o)
    {
        int left = (int)(o.getRect().left/DW);
        int right  = (int)(o.getRect().right/DW);
        int top = (int)(o.getRect().top/DH);
        int bottom = (int)(o.getRect().bottom/DH);
        int width  = right -left;
        int height = bottom -top;
        int[] plabel = new int[1];
        double[] pconfidence = new double[1];

        long startTime = System.currentTimeMillis();
        Matrix matrix = new Matrix(); 
        matrix.preScale(-1.0f, 1.0f); 

//        Log.d(TAG, "["+left+"=("+o.getRect().left+"/"+DW+"), "+top+"=("+o.getRect().top+"/"+DH+"), "+width+", "+height+"]");
        Bitmap faceBmp = Bitmap.createBitmap(picture, left, top, width, height, matrix, false);  //320*240
        Bitmap scaleBmp = faceBmp;
        if(width>100 || height > 100){
                scaleBmp = Bitmap.createScaledBitmap(faceBmp , 100, 100, false);
                width = 100;
                height = 100;
        }
        Bitmap emptyBmp = Bitmap.createBitmap(width, height, scaleBmp.getConfig());
        Bitmap grayBmp = setGrayImage(width, height, scaleBmp , emptyBmp);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        grayBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArrayFromPicture = stream.toByteArray();

        String filePath = mImagePath + "/tmp"+id;
        OutputStream imageFileOS;
        try {
            imageFileOS = new FileOutputStream(filePath);
            imageFileOS.write(byteArrayFromPicture);
            imageFileOS.flush();
            imageFileOS.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Mat testImage = imread(filePath, CV_LOAD_IMAGE_GRAYSCALE);

        long recogTime = System.currentTimeMillis();
        faceRecognizer.predict(testImage, plabel , pconfidence);
        long endTime   = System.currentTimeMillis();

        long imageTime = recogTime- startTime;
        long predictTime = endTime - recogTime;
        long totalTime = endTime - startTime;

        String people = null;
//        if(pconfidence[0]<=120){
            switch(plabel[0]){
                case 1: people="Heidi";break;
                case 2: people="Guochi";break;
                case 3: people="Klutee";break;
            } 
 //       }
        synchronized(o){
            o.setName(people);
            o.setLevel(pconfidence[0]);
            o.setTime(predictTime);
        }
        Log.d(TAG, "name:"+people+"-"+id+", id:"+o.getId()+", level:"+o.getLevel()+", predict time:"+o.getTime()+"ms, total time:"+totalTime+"ms");
    }

    private Bitmap setGrayImage(int w, int h, Bitmap scale, Bitmap graypic)
    {
        for(int x = 0; x < w; ++x) {
            for(int y = 0; y < h; ++y) {
                int pixel = scale.getPixel(x, y);
                int a = Color.alpha(pixel);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                r = g = b = (int)(0.299 * r + 0.587 * g + 0.114 * b);

                graypic.setPixel(x, y, Color.argb(a, r, g, b));
            }
        }
        return graypic;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setFormat(PixelFormat.UNKNOWN);
        surfaceView = (SurfaceView)findViewById(R.id.camerapreview);

//       surfaceView = new SurfaceView(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        drawingView = new DrawingView(this);
        LayoutParams layoutParamsDrawing 
         = new LayoutParams(LayoutParams.FILL_PARENT, 
           LayoutParams.FILL_PARENT);
        this.addContentView(drawingView, layoutParamsDrawing);

        faceTraining(TRAINDIR);

        mOrientationEventListener = new SimpleOrientationEventListener(this);
        mOrientationEventListener.enable();
        mPeople = Collections.synchronizedList(new ArrayList<People>()); 
        mThreadPredict = new HandlerThread("mThreadPredict");
        mThreadPredict.start();
        mHandler = new Handler(mThreadPredict.getLooper(), this);

        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        File directory = cw.getDir("images", Context.MODE_PRIVATE);
        mImagePath =  directory.getAbsolutePath();


/*      WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1, //Must be at least 1x1                 
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, 0,//Don't know if this is a safe default                 
            PixelFormat.UNKNOWN);
        wm.addView(surfaceView, params);
*/
    }



    /**
     * We need to react on OrientationEvents to rotate the screen and
     * update the views.
     */
    private class SimpleOrientationEventListener extends OrientationEventListener {

        public SimpleOrientationEventListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util2.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util2.getDisplayRotation(MainActivity.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                drawingView.setOrientation(mOrientationCompensation);
            }
        }
    }

    @Override
    protected void onPause() {
        mOrientationEventListener.disable();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }


    @Override
    public void onResume()
    {
        mOrientationEventListener.enable();
        super.onResume();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // TODO Auto-generated method stub
        if(previewing){
            camera.stopFaceDetection();
            camera.stopPreview();
            previewing = false;
        }

        if (camera != null){
            camera.setPreviewCallback(this);
            mDisplayRotation = Util2.getDisplayRotation(MainActivity.this);
            mDisplayOrientation = Util2.getDisplayOrientation(mDisplayRotation, 0);
            camera.setDisplayOrientation(mDisplayOrientation);
            if (drawingView!= null) {
                drawingView.setDisplayOrientation(mDisplayOrientation);
            }
            try {
                Log.d(TAG, "Max Face: " + camera.getParameters().getMaxNumDetectedFaces());
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
                camera.startFaceDetection();
                previewing = true;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        //camera = Camera.open(0);
        camera.setFaceDetectionListener(faceDetectionListener);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surface destroyed, camera");
        // TODO Auto-generated method stub
        previewing = false;
        mCamera = null;
        mData = null;
        mThreadPredict.interrupt(); 
        mThreadPredict= null;
        camera.setPreviewCallback(null);
        camera.stopFaceDetection();
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    public byte[] mData;
    public Camera mCamera;

    @Override
    public void onPreviewFrame(final byte[] data, Camera camera) {
        if(isFacePredict == false){
            mData = data;
            mCamera = camera;
            mHandler.sendEmptyMessage(MSG_PREDICT_START);
        }
    }

    public void facePredict()
    {
        isFacePredict = true;
        if(mCamera !=null && mData!=null){
            Camera.Parameters parameters = mCamera.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;

            YuvImage yuv = new YuvImage(mData, parameters.getPreviewFormat(), width, height, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);

            byte[] bytes = out.toByteArray();
            Bitmap picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            int i=0;
             
            synchronized(mPeople){
                for(People o: mPeople){
                    setPredict(picture , i, o);
                    i++;
                }
            }
        }
        isFacePredict = false;
    }


    private void faceTraining(String dir)
    {
        String trainingDir = dir;
        File root = new File(trainingDir);
        FilenameFilter imgFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                name = name.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };

        File[] imageFiles = root.listFiles(imgFilter);
        MatVector images = new MatVector(imageFiles.length);
        Mat labels = new Mat(imageFiles.length, 1, CV_32SC1);
        IntBuffer labelsBuf = labels.getIntBuffer();
        int counter = 0;
        for (File image : imageFiles) {
            Mat img = imread(image.getAbsolutePath(), CV_LOAD_IMAGE_GRAYSCALE);
            int label = Integer.parseInt(image.getName().split("-")[0]);
            images.put(counter, img);
            labelsBuf.put(counter, label);
            counter++;
            Log.d(TAG, "label:"+label+", name:"+image.getName()+", count:"+counter);
        }
        Log.d(TAG, "DONE!!!!!!");
        //faceRecognizer = createFisherFaceRecognizer();
        // faceRecognizer = createEigenFaceRecognizer();
        faceRecognizer = createLBPHFaceRecognizer();
        faceRecognizer.train(images, labels);
    }


    @Override
    public boolean handleMessage(Message msg) {
        if(msg.what == MSG_PREDICT_START){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            facePredict();
        }
        return false;
    }

    private class DrawingView extends View{
        Paint drawingPaint;
        Paint mTextPaint;
        private int mDisplayOrientation;
        private int mOrientation;
        private Face[] mFaces;

        public DrawingView(Context context) {
            super(context);
            drawingPaint = new Paint();
            drawingPaint.setColor(Color.GREEN);
            drawingPaint.setStyle(Paint.Style.STROKE); 
            drawingPaint.setStrokeWidth(1);

            mTextPaint = new Paint();
            mTextPaint.setAntiAlias(true);
            mTextPaint.setDither(true);
            mTextPaint.setTextSize(20);
            mTextPaint.setColor(Color.GREEN);
            mTextPaint.setStyle(Paint.Style.FILL);
     
        }
                
        public void setFaces(Face[] faces){
            mFaces = faces;
            invalidate();
        }

        public void setOrientation(int orientation) {
            mOrientation = orientation;
        }


        public void setDisplayOrientation(int displayOrientation) {
            mDisplayOrientation = displayOrientation;
            invalidate();
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            // TODO Auto-generated method stub
            super.onDraw(canvas);
            if (mFaces != null && mFaces.length > 0) {
                int rw = getWidth();
                int rh = getHeight();

                Matrix matrix = new Matrix();
                Util2.prepareMatrix(matrix, false, mDisplayOrientation, rw, rh);
                matrix.postRotate(mOrientation);

                Matrix matrixDraw = new Matrix();
                Util2.prepareMatrix(matrixDraw, true, mDisplayOrientation, rw, rh);
                matrixDraw.postRotate(mOrientation);

                canvas.save();
                canvas.rotate(-mOrientation);

                for (Face face : mFaces) {
                    RectF mRect = new RectF();
                    RectF mRectDraw = new RectF();
                    mRect.set(face.rect);
                    matrix.mapRect(mRect);
                    mRectDraw.set(face.rect);
                    matrixDraw.mapRect(mRectDraw);

                    synchronized(mPeople){
                    boolean find = false;
                        for(People o: mPeople){
                            if(o.getId() == face.id){
                                o.setRect(mRect);
                                o.setRectDraw(mRectDraw);
                                find = true;
                                break;
                            }            
                        }
                        if(find == false){
                            People p  = new People();
                            p.setRect(mRect);
                            p.setId(face.id);
                            p.setRectDraw(mRectDraw);
                            mPeople.add(p);
                        }
                    }
                    canvas.drawOval(mRectDraw, drawingPaint);
                }
                int psize = mPeople.size();
                int fsize = mFaces.length;
                if(fsize < psize){
                    int df = psize-fsize; 
                    synchronized(mPeople){
                        for(int i=psize;i>fsize;i--){ 
                    //        Log.d(TAG, "psize:"+psize+", fsize:"+fsize+", df:"+df+", i:"+i);
                            mPeople.remove(i-1);
                        }
                    }
                }
                synchronized(mPeople){
                    for(People o: mPeople){
                        String msg = o.getName()+" ("+o.getLevel()+", "+o.getTime()+"ms)";
                        if(o.getName() != null){
                            canvas.drawText(msg, o.getRectDraw().left, o.getRectDraw().bottom+10, mTextPaint);
                        }
                    }
                }
                canvas.restore();
            }else{
                mPeople.clear();
                canvas.drawColor(0, Mode.CLEAR);
            }
        }
    }

    public class People 
    {
        private RectF rectf;
        private Rect rect;
        private RectF rectdraw;
        private String name;
        private double level;
        private long time;
        private int id;
        public void setTime(long t){
            this.time = t;
        } 
        public void setLevel(double l){
            this.level = l;
        } 
        public void setRectDraw(RectF r){
            this.rectdraw = r;
        } 
        public void setId(int i){
            this.id= i;
        } 
        public void setRect(RectF f){
            this.rectf = f;
        } 
        public void setName(String n){
            this.name= n;
        } 
        public int getLevel(){
            return (int)this.level;
        }
        public long getTime(){
            return this.time;
        }
        public int getId(){
            return this.id;
        }
        public RectF getRectDraw(){
            return this.rectdraw;
        }
        public RectF getRect(){
            return this.rectf;
        }
        public String getName(){
            return this.name;
        }
    }
    
}

