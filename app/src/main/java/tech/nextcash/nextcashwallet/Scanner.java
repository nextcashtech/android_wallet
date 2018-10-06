package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;


public class Scanner
{
    public static final String logTag = "Scanner";

    public static final int FACING_FRONT = CameraMetadata.LENS_FACING_FRONT;
    public static final int FACING_BACK = CameraMetadata.LENS_FACING_BACK;

    private static final int MIN_FRAME_WIDTH  = 240;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_WIDTH  = 1200; // = 5/8 * 1920
    private static final int MAX_FRAME_HEIGHT = 675; // = 5/8 * 1080

    public static final int FAIL_ACCESS   = 1;
    public static final int FAIL_CREATION = 2;

    public interface CallBack
    {
        void onScannerResult(String pResult);
        void onScannerFailed(int pFailReason);
    }

    private boolean mIsValid, mIsClosing;

    private CameraDevice mDevice;
    private Size mCaptureSize;
    private int [] mOutputFormats;
    private int [] mDesiredOutputFormats;

    private SurfaceHolder mSurfaceHolder;
    private boolean mSurfaceCreated;
    private ImageReader mImageReader;
    private CaptureRequest mSurfaceRequest, mImageReaderRequest;
    private final MultiFormatReader mMultiFormatReader;

    private CameraCaptureSession mSession;
    private HandlerThread mHandlerThread;

    private CameraDevice.StateCallback mDeviceCallBack;
    private CameraCaptureSession.StateCallback mCaptureStateCallBack;
    private SurfaceHolder.Callback mSurfaceCallBack;
    private CameraCaptureSession.CaptureCallback mCaptureCallBack;
    private CallBack mCallBack;
    private Handler mCallBackHandler;


    public Scanner(CallBack pCallBack, Handler pCallBackHandler)
    {
        mCallBack = pCallBack;
        mCallBackHandler = pCallBackHandler;
        mIsValid = false;
        mDevice = null;
        mSurfaceHolder = null;
        mSession = null;
        mSurfaceCreated = false;
        mCaptureSize = null;
        mImageReader = null;
        mOutputFormats = null;
        mSurfaceRequest = null;
        mImageReaderRequest = null;
        mHandlerThread = null;
        mIsClosing = false;

        mMultiFormatReader = new MultiFormatReader();
        HashMap<DecodeHintType, Object> decodeHints = new HashMap<>();
        ArrayList<BarcodeFormat> decodeFormats = new ArrayList<>();
        decodeFormats.add(BarcodeFormat.QR_CODE);
        decodeHints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
        mMultiFormatReader.setHints(decodeHints);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            mDesiredOutputFormats = new int[] { ImageFormat.YUV_420_888, ImageFormat.YUV_422_888,
              ImageFormat.YUV_444_888 };
        else
            mDesiredOutputFormats = new int[] { ImageFormat.YUV_420_888 };

        mSurfaceCallBack = new SurfaceHolder.Callback()
        {
            @Override
            public void surfaceCreated(SurfaceHolder pSurfaceHolder)
            {
                if(pSurfaceHolder == null)
                    Log.e(logTag, "SurfaceHolder created a null surface");
                else if (!mSurfaceCreated)
                {
                    mSurfaceCreated = true;
                    attemptCreateSession();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder pSurfaceHolder)
            {
                mSurfaceCreated = false;
            }

            @Override
            public void surfaceChanged(SurfaceHolder pSurfaceHolder, int pFormat, int pWidth, int pHeight)
            {
                Log.d(logTag, "SurfaceHolder surfaceChange");
            }
        };

        mDeviceCallBack = new CameraDevice.StateCallback()
        {
            @Override
            public void onOpened(@NonNull CameraDevice pDevice)
            {
                Log.i(logTag,"Camera opened");
                mDevice = pDevice;
                attemptCreateSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice pDevice)
            {
                Log.i(logTag,"Camera disconnected");
                mDevice = null;
                mIsValid = false;
                close(); // Clean up other objects
            }

            @Override
            public void onError(@NonNull CameraDevice pDevice, int pError)
            {
                Log.e(logTag, String.format(Locale.US, "Failed to open camera device : error %d", pError));
                close();
            }
        };

        mCaptureStateCallBack = new CameraCaptureSession.StateCallback()
        {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession pSession)
            {
                Log.i(logTag,"Camera capture session configured");
                mSession = pSession;
                ArrayList<CaptureRequest> requests = new ArrayList<>();
                try
                {
                    // Add surface request
                    CaptureRequest.Builder builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    builder.addTarget(mSurfaceHolder.getSurface());
                    mSurfaceRequest = builder.build();
                    requests.add(mSurfaceRequest);

                    // Add image reader request
                    builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    builder.addTarget(mImageReader.getSurface());
                    mImageReaderRequest = builder.build();
                    requests.add(mImageReaderRequest);

                    mSession.setRepeatingBurst(requests, mCaptureCallBack, null);
                }
                catch(CameraAccessException pException)
                {
                    Log.e(logTag,"Failed to create/set capture request", pException);
                    close();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession pSession)
            {
                Log.e(logTag,"Failed to configure camera capture session");
                mSession = null;
                close();
            }
        };

        mCaptureCallBack = new CameraCaptureSession.CaptureCallback()
        {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession pSession, @NonNull CaptureRequest pRequest,
                                         long pTimeStamp, long pFrameNumber)
            {
                super.onCaptureStarted(pSession, pRequest, pTimeStamp, pFrameNumber);
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession pSession, @NonNull CaptureRequest pRequest,
                                            @NonNull CaptureResult pPartialResult)
            {
                super.onCaptureProgressed(pSession, pRequest, pPartialResult);
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession pSession, @NonNull CaptureRequest pRequest,
                                           @NonNull TotalCaptureResult pResult)
            {
                if(mImageReaderRequest == pRequest)
                {
                    Image image = mImageReader.acquireLatestImage();
                    Rect frame = calculateFramingRect();
                    if(image != null && frame != null)
                    {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        LuminanceSource source = new PlanarYUVLuminanceSource(bytes, image.getWidth(),
                          image.getHeight(), frame.left, frame.top, frame.width(), frame.height(),
                          false);
                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                        Result result;

                        try
                        {
                            result = mMultiFormatReader.decodeWithState(bitmap);
                            if(result != null)
                                postResultCallBack(result.getText());
                        }
                        catch(ReaderException pException)
                        {
                            // I think this just happens when no code is found.
                            //Log.e(logTag, "Capture decode read exception", pException);
                        }
                        finally
                        {
                            mMultiFormatReader.reset();
                        }

                        image.close();
                    }
                }

                super.onCaptureCompleted(pSession, pRequest, pResult);
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession pSession, @NonNull CaptureRequest pRequest,
                                        @NonNull CaptureFailure pFailure)
            {
                super.onCaptureFailed(pSession, pRequest, pFailure);
            }

            @Override
            public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession pSession, int pSequenceID,
                                                   long pFrameNumber)
            {
                super.onCaptureSequenceCompleted(pSession, pSequenceID, pFrameNumber);
            }

            @Override
            public void onCaptureSequenceAborted(@NonNull CameraCaptureSession pSession, int pSequenceID)
            {
                super.onCaptureSequenceAborted(pSession, pSequenceID);
            }

            @Override
            public void onCaptureBufferLost(@NonNull CameraCaptureSession pSession, @NonNull CaptureRequest pRequest,
                                            @NonNull Surface pTarget, long pFrameNumber)
            {
                super.onCaptureBufferLost(pSession, pRequest, pTarget, pFrameNumber);
            }
        };
    }

    public boolean isValid() { return mIsValid; }
    public boolean isOpen() { return mDevice != null; }
    public Size captureSize() { return mCaptureSize; } // For scanner view to fix aspect ratio

    public synchronized boolean open(Context pContext, SurfaceHolder pSurfaceHolder, int pFacing)
    {
        if(mIsClosing)
        {
            Log.e(logTag, "Failed to open scanner : still closing");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        mIsValid = false;
        mSurfaceHolder = pSurfaceHolder;
        mSurfaceHolder.addCallback(mSurfaceCallBack);

        CameraManager manager = (CameraManager)pContext.getSystemService(Context.CAMERA_SERVICE);
        if(manager == null)
        {
            Log.e(logTag, "Failed to get camera manager");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        String[] cameraIDs;
        try
        {
            cameraIDs = manager.getCameraIdList();
        }
        catch(CameraAccessException pException)
        {
            Log.e(logTag, "Failed to get camera list", pException);
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        if(cameraIDs.length == 0)
        {
            Log.e(logTag, "No cameras found");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        String selectedCamera = null;
        CameraCharacteristics characteristics = null;
        Integer facing;
        try
        {
            for(String cameraID : cameraIDs)
            {
                characteristics = manager.getCameraCharacteristics(cameraID);
                facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing != null && facing == pFacing)
                {
                    selectedCamera = cameraID;
                    break;
                }
            }
        }
        catch(CameraAccessException pException)
        {
            Log.e(logTag, "Failed to get camera characteristics", pException);
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        if(selectedCamera == null)
        {
            Log.e(logTag, "Failed to get camera facing the desired direction");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        StreamConfigurationMap configurationMap =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if(configurationMap == null)
        {
            Log.e(logTag, "Failed to get camera configuration map");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        // Choose the largest size that is smaller than pContainingSize.
        Size[] sizes = configurationMap.getOutputSizes(SurfaceHolder.class);
        mCaptureSize = null;
        for(Size size : sizes)
            if(size.getWidth() > MIN_FRAME_WIDTH && size.getHeight() > MIN_FRAME_HEIGHT &&
              MAX_FRAME_WIDTH > size.getWidth() && MAX_FRAME_HEIGHT > size.getHeight() &&
              (mCaptureSize == null ||
              (mCaptureSize.getWidth() * mCaptureSize.getHeight()) < (size.getWidth() * size.getWidth())))
                mCaptureSize = size;

        if(mCaptureSize == null)
        {
            Log.e(logTag, "Failed to find appropriate camera output size");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        mOutputFormats = configurationMap.getOutputFormats();

        Log.i(logTag, String.format(Locale.US, "Set camera output size to %d, %d", mCaptureSize.getWidth(),
          mCaptureSize.getHeight()));
        mSurfaceHolder.setFixedSize(mCaptureSize.getWidth(), mCaptureSize.getHeight());

        // Start handler thread to run callbacks in.
        mHandlerThread = new HandlerThread("ScannerCapture", Thread.MIN_PRIORITY);
        mHandlerThread.start();

        try
        {
            manager.openCamera(selectedCamera, mDeviceCallBack, new Handler(mHandlerThread.getLooper()));
            mIsValid = true;
        }
        catch(SecurityException pException)
        {
            Log.e(logTag, "Failed to get camera permission", pException);
            close();
            postFailCallBack(FAIL_ACCESS);
            return false;
        }
        catch(CameraAccessException pException)
        {
            Log.e(logTag, "Failed to get camera access", pException);
            close();
            postFailCallBack(FAIL_ACCESS);
            return false;
        }

        return true;
    }

    private void internalClose()
    {
        Log.i(logTag,"Closing camera");
        mIsValid = false;

        // Close capture session
        if(mSession != null)
        {
            mSession.close();
            mSession = null;
        }

        // Close image reader
        if(mImageReader != null)
        {
            mImageReader.close();
            mImageReader = null;
        }

        if(mSurfaceRequest != null)
            mSurfaceRequest = null;

        if(mImageReaderRequest != null)
            mImageReaderRequest = null;

        // Disconnect from device
        if(mDevice != null)
        {
            mDevice.close();
            mDevice = null;
        }

        if(mHandlerThread != null)
        {
            mHandlerThread.quit();
            mHandlerThread = null;
        }

        mIsClosing = false;
        Log.i(logTag,"Camera closed");
    }

    public synchronized void close()
    {
        if(mIsClosing)
            return;

        mIsClosing = true;

        // Quit handler thread
        if(mHandlerThread == null)
            internalClose();
        else
        {
            Log.i(logTag,"Closing camera in handler thread");
            Handler closeHandler = new Handler(mHandlerThread.getLooper());
            closeHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    internalClose();
                }
            });
        }
    }

    public String imageFormatName(int pFormat)
    {
        switch(pFormat)
        {
        case ImageFormat.RGB_565:
            return "RGB_565";
        case ImageFormat.NV16:
            return "NV16";
        case ImageFormat.YUY2:
            return "YUY2";
        case ImageFormat.YV12:
            return "YV12";
        case ImageFormat.JPEG:
            return "JPEG";
        case ImageFormat.NV21:
            return "NV21";
        case ImageFormat.YUV_420_888:
            return "YUV_420_888";
        case ImageFormat.YUV_422_888:
            return "YUV_422_888";
        case ImageFormat.YUV_444_888:
            return "YUV_444_888";
        case ImageFormat.FLEX_RGB_888:
            return "FLEX_RGB_888";
        case ImageFormat.FLEX_RGBA_8888:
            return "FLEX_RGBA_8888";
        case ImageFormat.RAW_SENSOR:
            return "RAW_SENSOR";
        case ImageFormat.RAW_PRIVATE:
            return "RAW_PRIVATE";
        case ImageFormat.RAW10:
            return "RAW10";
        case ImageFormat.RAW12:
            return "RAW12";
        case ImageFormat.DEPTH16:
            return "DEPTH16";
        case ImageFormat.DEPTH_POINT_CLOUD:
            return "DEPTH_POINT_CLOUD";
        case ImageFormat.PRIVATE:
            return "PRIVATE";
//        case ImageFormat.RAW_DEPTH:
//            return "RAW_DEPTH";
        default:
            return "UNDEFINED";
        }
    }

    private void attemptCreateSession()
    {
        if(mIsClosing || !mIsValid || mDevice == null || mSurfaceHolder == null || !mSurfaceCreated)
            return;

        Log.i(logTag, "Creating capture session");

        // Start capture session on screen surface
        ArrayList<Surface> surfaces = new ArrayList<>();
        surfaces.add(mSurfaceHolder.getSurface());

        // Add ImageReader to process QR codes
        // Close previous image reader
        if(mImageReader != null)
        {
            mImageReader.close();
            mImageReader = null;
        }

        boolean formatSelected = false;
        int selectedFormat = 0;
        for(int desiredFormat : mDesiredOutputFormats)
        {
            for(int supportedFormat : mOutputFormats)
                if(supportedFormat == desiredFormat)
                {
                    formatSelected = true;
                    selectedFormat = supportedFormat;
                    break;
                }

            if(formatSelected)
                break;
        }

        if(!formatSelected)
        {
            String supportedText = "";
            for(int supportedFormat : mOutputFormats)
            {
                if(supportedText.length() > 0)
                    supportedText += ", ";
                supportedText += imageFormatName(supportedFormat);
            }
            Log.e(logTag, String.format("Failed to find desired output format : %s", supportedText));
            close();
            postFailCallBack(FAIL_CREATION);
            return;
        }

        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(), selectedFormat,
          1);

        surfaces.add(mImageReader.getSurface());

        try
        {
            mDevice.createCaptureSession(surfaces, mCaptureStateCallBack, new Handler(mHandlerThread.getLooper()));
        }
        catch(CameraAccessException pException)
        {
            Log.e(logTag, "Failed to get camera access to create capture session", pException);
            close();
            postFailCallBack(FAIL_ACCESS);
        }
    }

    public Rect calculateFramingRect()
    {
        if(mCaptureSize == null)
            return null;

        int width = findDesiredDimensionInRange(mCaptureSize.getWidth(), MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
        int height = findDesiredDimensionInRange(mCaptureSize.getHeight(), MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);

        int left = (mCaptureSize.getWidth() - width) / 2;
        int top = (mCaptureSize.getHeight() - height) / 2;
        return new Rect(left, top, left + width, top + height);
    }

    private static int findDesiredDimensionInRange(int pResolution, int pHardMin, int pHardMax)
    {
        int dim = 5 * pResolution / 8; // Target 5/8 of each dimension
        if(dim < pHardMin)
        {
            return pHardMin;
        }
        if(dim > pHardMax)
        {
            return pHardMax;
        }
        return dim;
    }

    private void postResultCallBack(final String pResult)
    {
        mCallBackHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mCallBack.onScannerResult(pResult);
            }
        });
    }

    private void postFailCallBack(final int pError)
    {
        mCallBackHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mCallBack.onScannerFailed(pError);
            }
        });
    }
}
