package exhibition.scooby;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

/**
 * @author Nilanjan
 * @version 1.0.1
 */
public class MainActivity extends Activity implements View.OnTouchListener, CameraBridgeViewBase.CvCameraViewListener2 {

    //Variables Section
    public static final String TAG = "Scooby:";
    private boolean mIsColorSelected = false;
    private Mat mRgba;
    private Scalar mBlobColorRgba;
    private Scalar mBlobColorHsv;
    private ColorBlobDetector mDetector;
    private Mat mSpectrum;
    private Size SPECTRUM_SIZE;
    private Scalar CONTOUR_COLOR;
    private Point currentContourCOG;
    private double currentContourArea;
    private CameraBridgeViewBase mOpenCvCameraView;
    String previousDirection = "";

    /**
     * Handler to display Toasts about direction of hand movement
     */
    Handler handler=new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Toast.makeText(getApplicationContext(), msg.obj.toString(),Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * Constructor: Just for debugging
     */
    public MainActivity() {
        Log.d(TAG, "New Object Instantiated " + this.getClass());
    }

    /**
     * Inner Class to check if the openCV library was successfully loaded.
     * If true, then the openCV camera view is enabled and Touch Listener is activated
     */
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                                    {
                                        Log.i(TAG, "OpenCV loaded successfully");
                                        mOpenCvCameraView.enableView();
                                        mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                                    } break;
                default:
                        {
                            super.onManagerConnected(status);
                        } break;
            }
        }
    };

    /**
     * Method to initialise the Activity and the layout
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

    }

    /**
     * If app is paused then Camera view will be disabled enabling Java garbage collector for resource reallocation.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    /**
     * onResume will search for an inbuilt openCV library. If not found then will call openCV Manager for an appropriate
     * openCV library. Here we are using openCV version 3.0.0
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Initialising variables for color blob detection
     * @param width -  the width of the frames that will be delivered
     * @param height - the height of the frames that will be delivered
     */
    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255, 0, 0, 255);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    /**
     * Callback method: Will be called on receiving a new video frame
     * @param inputFrame: The input new camera-frame received from the camera
     * @return The RBGA color matrix of the frame
     */
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        if (mIsColorSelected) {
            mDetector.process(mRgba);
            List<MatOfPoint> contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + contours.size());
            Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);
            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);
            Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
            if(currentContourCOG != null) {

                String direction = getDirection(currentContourCOG, ColorBlobDetector.centerOfGravity,
                        currentContourArea, ColorBlobDetector.contourArea);
                if(previousDirection.compareTo(direction) != 0 && direction.compareTo("Stop") != 0) {
                    Log.d("direction:", direction);
                    Message message = handler.obtainMessage();
                    message.obj = direction;
                    handler.sendMessage(message);
                    previousDirection = direction;
                }
            }

            currentContourCOG = new Point(ColorBlobDetector.centerOfGravity.x,
                    ColorBlobDetector.centerOfGravity.y);
            currentContourArea = ColorBlobDetector.contourArea;
        }

        return mRgba;
    }

    /**
     * Will process and compare the center of gravity of previous Dominant Contour with the new one
     * and the previous Contour area with the new one Contour area to determine direction of motion of tracked object
     * @param currentPoint The current Contour's Center Of Gravity
     * @param newPoint The new Dominant Contour's Center Of Gravity
     * @param currentArea The current Contour's area
     * @param newArea The new Contour's area
     * @return The direction of motion viz. Forward Backward Left Right Stop
     */
    public String getDirection(Point currentPoint, Point newPoint, double currentArea, double newArea) {

        double displacementX = currentPoint.x - newPoint.x;
        double displacementArea = currentArea - newArea;
        try {
            Log.d("DPoint",currentPoint.x + " " + newPoint.x);
            Log.d("DArea", currentArea + " " + newArea);

            if (Math.abs(displacementX) / currentPoint.x > 0.5) {
                if (displacementX > 0)
                    return "Right ";
                else
                    return "Left ";
            }
            if (Math.abs(displacementArea) / currentArea > 0.5) {
                if (displacementArea > 0)
                    return "Forward";
                else
                    return "Backward";
            }
            return "Stop";
        }catch (Exception e) {
            Log.e("Error", e.getMessage());
            return "Stop";
        }
    }

    /**
     * Callback method: Determines the color matrix of the region touched and the its surrounding areas to determine the color blob to be tracked.
     * @param v The view of the camera
     * @param event The event callback
     * @return Disables subsequent simultaneous call backs from touch listener
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = convertScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch
    }

    /**
     * Converts a HSV color matrix into its corresponding RGBA color matrix
     * @param hsvColor The HSV Scalar to be converted
     * @return The corresponding RGBA Scalar
     */
    private Scalar convertScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}
