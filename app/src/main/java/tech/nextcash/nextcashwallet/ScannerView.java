package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Size;
import android.view.SurfaceView;
import android.view.View;

// Used to resize the view based on the capture aspect ratio.
public class ScannerView extends SurfaceView
{
    private Scanner mScanner;

    public ScannerView(Context context)
    {
        super(context);
        mScanner = null;
    }

    public ScannerView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        mScanner = null;
    }

    public ScannerView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        mScanner = null;
    }

    public ScannerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
        mScanner = null;
    }

    public void setCamera(Scanner pCamera)
    {
        mScanner = pCamera;
    }

    @Override
    protected void onMeasure(int pWidthMeasureSpec, int pHeightMeasureSpec)
    {
        if(mScanner != null)
        {
            Size captureSize = mScanner.captureSize();
            if(captureSize != null)
            {
                // Adjust the height so the view has the same aspect ratio as the camera capture.
                double aspectRatio = (double)captureSize.getWidth() / (double)captureSize.getHeight();
                int specWidth = View.MeasureSpec.getSize(pWidthMeasureSpec);
                setMeasuredDimension(specWidth, (int)((double)specWidth * aspectRatio));
                return;
            }
        }

        super.onMeasure(pWidthMeasureSpec, pHeightMeasureSpec);
    }
}
