package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
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
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;


public class Scanner
{
    public static final String logTag = "Scanner";

    public static final int FACING_FRONT = CameraMetadata.LENS_FACING_FRONT;
    public static final int FACING_BACK = CameraMetadata.LENS_FACING_BACK;

    private static final int MIN_FRAME_WIDTH  = 240;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_WIDTH  = 1920;
    private static final int MAX_FRAME_HEIGHT = 1080;

    public static final int FAIL_ACCESS   = 1;
    public static final int FAIL_CREATION = 2;

    public interface CallBack
    {
        void onScannerResult(String pResult);
        void onScannerFailed(int pFailReason);
    }

    private boolean mIsValid, mIsClosing, mFinished;

    private CameraDevice mDevice;
    private Size mCaptureSize;
    private int [] mOutputFormats;
    private int [] mDesiredProcessOutputFormats;
    private CameraCharacteristics mCameraCharacteristics;

    private boolean mSurfaceConfigured;
    private Surface mSurface;
    private int mImageFormat;
    private ImageReader mImageReader;
    private MultiFormatReader mMultiFormatReader;

    private CameraCaptureSession mSession;
    private HandlerThread mHandlerThread;

    private boolean mCaptureSessionCreated;
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
        mFinished = false;
        mDevice = null;
        mSession = null;
        mSurfaceConfigured = false;
        mCaptureSessionCreated = false;
        mCaptureSize = null;
        mSurface = null;
        mImageFormat = 0;
        mImageReader = null;
        mOutputFormats = null;
        mHandlerThread = null;
        mIsClosing = false;
        mCameraCharacteristics = null;

        mMultiFormatReader = new MultiFormatReader();
        HashMap<DecodeHintType, Object> decodeHints = new HashMap<>();
        ArrayList<BarcodeFormat> decodeFormats = new ArrayList<>();
        decodeFormats.add(BarcodeFormat.QR_CODE);
        decodeHints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
        mMultiFormatReader.setHints(decodeHints);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            mDesiredProcessOutputFormats = new int[] { ImageFormat.YUV_420_888, ImageFormat.YUV_422_888,
              ImageFormat.YUV_444_888 };
        else
            mDesiredProcessOutputFormats = new int[] { ImageFormat.YUV_420_888 };

        mSurfaceCallBack = new SurfaceHolder.Callback()
        {
            @Override
            public void surfaceCreated(SurfaceHolder pSurfaceHolder)
            {
                if(pSurfaceHolder == null)
                {
                    Log.e(logTag, "SurfaceHolder created a null surface");
                    postFailCallBack(FAIL_CREATION);
                    close();
                    return;
                }

                Log.i(logTag, "SurfaceHolder surface created");
                if(mSurface == null)
                    Log.d(logTag, "SurfaceHolder created a new surface");
                mSurface = pSurfaceHolder.getSurface();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder pSurfaceHolder)
            {
                if(pSurfaceHolder == null)
                    Log.e(logTag, "SurfaceHolder destroyed a null surface");

                Log.i(logTag, "SurfaceHolder surface destroyed");
                mSurfaceConfigured = false;
                mSurface = null;
            }

            @Override
            public void surfaceChanged(SurfaceHolder pSurfaceHolder, int pFormat, int pWidth, int pHeight)
            {
                if(pSurfaceHolder == null)
                {
                    Log.e(logTag, "SurfaceHolder changed a null surface");
                    postFailCallBack(FAIL_CREATION);
                    close();
                    return;
                }

                Log.d(logTag, String.format(Locale.US,
                  "SurfaceHolder surface changed : format %s, size %d, %d", pixelFormatName(pFormat), pWidth,
                  pHeight));
                mSurface = pSurfaceHolder.getSurface();
                if(mSurfaceConfigured || (pWidth == mCaptureSize.getWidth() && pHeight == mCaptureSize.getHeight()))
                {
                    mSurfaceConfigured = true;
                    attemptCreateSession();
                }
            }
        };

        mDeviceCallBack = new CameraDevice.StateCallback()
        {
            @Override
            public void onOpened(@NonNull CameraDevice pDevice)
            {
                Log.i(logTag,"Camera device opened");
                mDevice = pDevice;
                attemptCreateSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice pDevice)
            {
                Log.i(logTag,"Camera device disconnected");
                mDevice = null;
                mIsValid = false;
                close(); // Clean up other objects
            }

            @Override
            public void onError(@NonNull CameraDevice pDevice, int pError)
            {
                Log.e(logTag, String.format(Locale.US, "Camera device error : %d", pError));
                postFailCallBack(FAIL_CREATION);
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

                try
                {
                    // Put camera in repeating burst mode.
                    CaptureRequest.Builder requestBuilder;
                    int[] capabilities =
                      mCameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

                    Log.i(logTag, "Using preview template");
                    requestBuilder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                    if(contains(mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES),
                      CameraCharacteristics.CONTROL_MODE_USE_SCENE_MODE) &&
                      contains(mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES),
                      CameraCharacteristics.CONTROL_SCENE_MODE_BARCODE))
                    {
                        Log.i(logTag, "Using barcode scene mode");

                        // Configure camera for barcode scanning. Disables auto-exposure and auto-focus modes.
                        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
                        requestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
                          CaptureRequest.CONTROL_SCENE_MODE_BARCODE);
                    }
                    else
                    {
                        Log.i(logTag, "Using basic auto mode");

                        // Settings below this depend on this setting.
                        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

                        // Turn on continuous auto focus.
                        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO);

                        // Turn on auto flash in torch (continuous) mode.
                        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    }

                    int[] edgeModes = mCameraCharacteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES);
                    if(contains(edgeModes, CameraCharacteristics.EDGE_MODE_FAST))
                    {
                        Log.i(logTag, "Using fast edge mode");
                        requestBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST);
                    }
                    else if(contains(edgeModes, CameraCharacteristics.EDGE_MODE_HIGH_QUALITY))
                    {
                        Log.i(logTag, "Using high quality edge mode");
                        requestBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
                    }

                    int[] noiseReductionModes = mCameraCharacteristics.get(
                      CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
                    if(contains(noiseReductionModes, CameraCharacteristics.NOISE_REDUCTION_MODE_FAST))
                    {
                        Log.i(logTag, "Using fast noise reduction mode");
                        requestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                          CaptureRequest.NOISE_REDUCTION_MODE_FAST);
                    }
                    else if(contains(noiseReductionModes, CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY))
                    {
                        Log.i(logTag, "Using high quality noise reduction mode");
                        requestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                          CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                    }
                    else if(contains(noiseReductionModes, CameraCharacteristics.NOISE_REDUCTION_MODE_MINIMAL) &&
                      Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    {
                        Log.i(logTag, "Using minimal noise reduction mode");
                        requestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                          CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL);
                    }

                    // One request seems to handle both surfaces.
                    requestBuilder.addTarget(mImageReader.getSurface());
                    requestBuilder.addTarget(mSurface);

                    mSession.setRepeatingBurst(Collections.singletonList(requestBuilder.build()), mCaptureCallBack,
                      new Handler(mHandlerThread.getLooper()));
                }
                catch(IllegalArgumentException|CameraAccessException pException)
                {
                    Log.e(logTag,"Failed to create capture request", pException);
                    close();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession pSession)
            {
                Log.e(logTag,"Failed to configure camera capture session");
                mSession = null;
                postFailCallBack(FAIL_CREATION);
                close();
            }
        };

        mCaptureCallBack = new CameraCaptureSession.CaptureCallback()
        {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession pSession, @NonNull CaptureRequest pRequest,
                                           @NonNull TotalCaptureResult pResult)
            {
                if(mFinished)
                    return; // Don't process anymore images.

                Image image = mImageReader.acquireLatestImage();
                if(image != null)
                {
                    Log.i(logTag, String.format("Processing %s image", imageFormatName(image.getFormat())));

                    // Find square frame in the middle to process. This reduces data to parse.
                    int diff;
                    Rect frame;
                    if(image.getHeight() > image.getWidth())
                    {
                        diff = (image.getHeight() - image.getWidth()) / 2;
                        frame = new Rect(0, diff, image.getWidth(), diff + image.getWidth());
                    }
                    else
                    {
                        diff = (image.getWidth() - image.getHeight()) / 2;
                        frame = new Rect(diff, 0, diff + image.getHeight(), image.getHeight());
                    }

                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    // The PlanarYUVLuminanceSource dataWidth was tricky.
                    // In some newer devices the data width is not the same as the image width.
                    LuminanceSource source = new PlanarYUVLuminanceSource(bytes,
                      plane.getRowStride() / plane.getPixelStride(),
                      image.getHeight(), frame.left, frame.top, frame.width(), frame.height(),
                      true);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                    Result result;

                    try
                    {
                        result = mMultiFormatReader.decodeWithState(bitmap);
                        if(result != null)
                            postResultCallBack(result.getText());
                    }
                    catch(NotFoundException pException)
                    {
                        // This happens every time an image is captured and a QR code is not found.
                    }
                    finally
                    {
                        mMultiFormatReader.reset();
                    }

                    image.close();
                }

                super.onCaptureCompleted(pSession, pRequest, pResult);
            }
        };
    }

    public boolean isValid() { return mIsValid; }
    public boolean isOpen() { return mDevice != null; }
    public Size captureSize() { return mCaptureSize; } // For scanner view to fix aspect ratio

    public synchronized boolean open(Context pContext, SurfaceHolder pSurfaceHolder, int pFacing)
    {
        Log.i(logTag, "Opening scanner");

        if(mIsClosing)
        {
            Log.e(logTag, "Failed to open scanner : still closing previous scanner");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        mIsValid = false;
        mFinished = false;

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
        mCameraCharacteristics = null;
        Integer facing;
        try
        {
            for(String cameraID : cameraIDs)
            {
                mCameraCharacteristics = manager.getCameraCharacteristics(cameraID);
                facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing != null && facing == pFacing)
                {
                    selectedCamera = cameraID;
                    break;
                }
            }

            if(selectedCamera == null)
            {
                Log.e(logTag, "Failed to get camera facing the desired direction. Using first");
                selectedCamera = cameraIDs[0];
                mCameraCharacteristics = manager.getCameraCharacteristics(selectedCamera);
            }
        }
        catch(CameraAccessException pException)
        {
            Log.e(logTag, "Failed to get camera characteristics", pException);
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        // Determine capture template
        Integer hardwareLevel = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if(hardwareLevel == null)
            Log.i(logTag, "Hardware support level not specified");
        else
        {
            switch(hardwareLevel)
            {
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                    Log.i(logTag, "Hardware support level legacy");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:
                    Log.i(logTag, "Hardware support level external");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                    Log.i(logTag, "Hardware support level limited");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                    Log.i(logTag, "Hardware support level full");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                    Log.i(logTag, "Hardware support level 3");
                    break;
                default:
                    Log.i(logTag, "Hardware support level unknown");
                    break;
            }
        }

        StreamConfigurationMap configurationMap =
          mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if(configurationMap == null)
        {
            Log.e(logTag, "Failed to get camera configuration map");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        // Determine the largest, most square size between min frame size and twice the max frame size.
        Size[] sizes = configurationMap.getOutputSizes(SurfaceHolder.class);
        mCaptureSize = null;
        double selectedWidthHeightRatio = 1.0, currentWidthHeightRatio = 1.0;
        for(Size size : sizes)
            if(size.getWidth() > MIN_FRAME_WIDTH && size.getHeight() > MIN_FRAME_HEIGHT &&
              MAX_FRAME_WIDTH > size.getWidth() && MAX_FRAME_HEIGHT > size.getHeight())
            {
                if(mCaptureSize == null)
                {
                    mCaptureSize = size;
                    selectedWidthHeightRatio = (double)Math.min(mCaptureSize.getWidth(), mCaptureSize.getHeight()) /
                      (double)Math.max(mCaptureSize.getWidth(), mCaptureSize.getHeight());
                }
                else
                {
                    currentWidthHeightRatio = (double)Math.min(size.getWidth(), size.getHeight()) /
                      (double)Math.max(size.getWidth(), size.getHeight());
                    if(currentWidthHeightRatio > selectedWidthHeightRatio ||
                      (currentWidthHeightRatio == selectedWidthHeightRatio &&
                      (size.getWidth() * size.getHeight()) > (mCaptureSize.getWidth() * mCaptureSize.getHeight())))
                    {
                        mCaptureSize = size;
                        selectedWidthHeightRatio = currentWidthHeightRatio;
                    }
                }
            }

        if(mCaptureSize == null)
        {
            Log.e(logTag, "Failed to find appropriate camera output size");
            close();
            postFailCallBack(FAIL_CREATION);
            return false;
        }

        // Determine format for data processing.
        mOutputFormats = configurationMap.getOutputFormats();
        boolean formatSelected = false;
        for(int desiredFormat : mDesiredProcessOutputFormats)
        {
            for(int supportedFormat : mOutputFormats)
                if(supportedFormat == desiredFormat)
                {
                    formatSelected = true;
                    mImageFormat = supportedFormat;
                    break;
                }

            if(formatSelected)
                break;
        }

        if(!formatSelected)
        {
            StringBuilder supportedText = new StringBuilder();
            for(int supportedFormat : mOutputFormats)
            {
                if(supportedText.length() > 0)
                    supportedText.append(", ");
                supportedText.append(imageFormatName(supportedFormat));
            }
            Log.e(logTag, String.format("Failed to find desired output format : %s", supportedText.toString()));
            close();
            postFailCallBack(FAIL_ACCESS);
            return false;
        }

        Log.i(logTag, String.format("Using format %s", imageFormatName(mImageFormat)));

        Log.i(logTag, String.format(Locale.US, "Set camera output size to %d, %d",
          mCaptureSize.getWidth(), mCaptureSize.getHeight()));
        pSurfaceHolder.setFixedSize(mCaptureSize.getWidth(), mCaptureSize.getHeight());
        pSurfaceHolder.setKeepScreenOn(true);
        // This later provides the surface object, so no need to keep the holder.
        pSurfaceHolder.addCallback(mSurfaceCallBack);

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
        Log.i(logTag,"Closing scanner");

        mIsValid = false;
        mSurfaceConfigured = false;
        mCaptureSessionCreated = false;
        mCameraCharacteristics = null;

        // Close capture session
        if(mSession != null)
        {
            try
            {
                mSession.abortCaptures();
                mSession.stopRepeating();
            }
            catch(IllegalStateException|CameraAccessException pException)
            {
                Log.d(logTag, "Failed to stop repeating capture session");
            }

            try
            {
                mSession.close();
            }
            catch(Exception pException)
            {
                Log.d(logTag, "Failed to close session");
            }

            mSession = null;
        }

        // Close image reader
        if(mImageReader != null)
        {
            mImageReader.close();
            mImageReader = null;
        }

        mSurface = null;

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
        Log.i(logTag,"Scanner closed");
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
            Log.i(logTag,"Closing scanner in handler thread");
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

    private synchronized void attemptCreateSession()
    {
        if(mIsClosing || !mIsValid || mDevice == null || !mSurfaceConfigured)
            return;

        if(mCaptureSessionCreated)
        {
            Log.i(logTag, "Recreating capture session");
            // Documentation says you don't need to close the previous session.
            mSession.close();
        }
        else
        {
            Log.i(logTag, "Creating capture session");
            mCaptureSessionCreated = true;
        }

        ArrayList<Surface> surfaces = new ArrayList<>();

        // Add screen surface view.
        surfaces.add(mSurface);

        if(mImageReader == null)
            mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(), mImageFormat,
              2);
        else
            mImageReader.close();

        // Add ImageReader for processing QR codes
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

    public String pixelFormatName(int pFormat)
    {
        switch(pFormat)
        {
        case PixelFormat.TRANSLUCENT:
            return "TRANSLUCENT";
        case PixelFormat.TRANSPARENT:
            return "TRANSPARENT";
        case PixelFormat.RGBA_8888:
            return "RGBA_8888";
        case PixelFormat.RGBX_8888:
            return "RGBX_8888";
        case PixelFormat.RGB_888:
            return "RGB_888";
        case PixelFormat.RGB_565:
            return "RGB_565";
        case PixelFormat.RGBA_5551:
            return "RGBA_5551";
        case PixelFormat.RGBA_4444:
            return "RGBA_4444";
        case PixelFormat.A_8:
            return "A_8";
        case PixelFormat.L_8:
            return "L_8";
        case PixelFormat.LA_88:
            return "LA_88";
        case PixelFormat.RGB_332:
            return "RGB_332";
        case PixelFormat.YCbCr_422_SP:
            return "YCbCr_422_SP";
        case PixelFormat.YCbCr_420_SP:
            return "YCbCr_420_SP";
        case PixelFormat.YCbCr_422_I:
            return "YCbCr_422_I";
        case PixelFormat.RGBA_F16:
            return "RGBA_F16";
        case PixelFormat.RGBA_1010102:
            return "RGBA_1010102";
        case PixelFormat.JPEG:
            return "JPEG";
        default:
            return "UNDEFINED";
        }
    }

    private void postResultCallBack(final String pResult)
    {
        mFinished = true;
        mCallBackHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mCallBack.onScannerResult(pResult);
            }
        });
        close();
    }

    private void postFailCallBack(final int pError)
    {
        mFinished = true;
        mCallBackHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mCallBack.onScannerFailed(pError);
            }
        });
        close();
    }

    private boolean contains(int[] pList, int pValue)
    {
        if(pList == null)
            return false;

        for(int item : pList)
            if(item == pValue)
                return true;

        return false;
    }
}
