package com.example.solution_color;


import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.os.Environment;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.library.bitmap_utilities.BitMap_Helpers;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;



public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener {

    //these are constants and objects that I used, use them if you wish
    private static final String DEBUG_TAG = "CartoonActivity";
    private static final String ORIGINAL_FILE = "origfile.png";
    private static final String PROCESSED_FILE = "procfile.png";

    private static final int TAKE_PICTURE = 1;
    private static final double SCALE_FROM_0_TO_255 = 2.55;
    private static final int DEFAULT_COLOR_PERCENT = 3;
    private static final int DEFAULT_BW_PERCENT = 15;

    //preferences
    private int saturation = DEFAULT_COLOR_PERCENT;
    private int bwPercent = DEFAULT_BW_PERCENT;
    private String shareSubject;
    private String shareText;

    //where images go
    private String originalImagePath;   //where orig image is
    private String processedImagePath;  //where processed image is
    private Uri outputFileUri;          //tells camera app where to store image

    //used to measure screen size
    int screenheight;
    int screenwidth;

    private ImageView myImage;

    //these guys will hog space
    Bitmap bmpOriginal;                 //original image
    Bitmap bmpThresholded;              //the black and white version of original image
    Bitmap bmpThresholdedColor;         //the colorized version of the black and white image

    //TODO manage all the permissions you need
    private final int PERMISSION_REQUEST_CAMERA = 1;
    private final int REQUEST_IMAGE_CAPTURE = 2;
    private final int PERMISSION_READ_EXTERNAL = 3;
    private final int PERMISSION_WRITE_EXTERNAL = 4;
    private final int PERMISSION_REQUEST_CODE = 5;

    private int PERMISSION_ALL = 1;
    private String[] PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };



    private SharedPreferences myPreference;
    private SharedPreferences.OnSharedPreferenceChangeListener listener = null;
    private boolean enablePreferenceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // be sure to set up the appbar in the activity
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        myImage = (ImageView) findViewById(R.id.imageView1);
        //dont display these
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        FloatingActionButton fab = findViewById(R.id.buttonTakePicture);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // manage this, mindful of permissions
                MainActivity.this.doTakePicture();
            }

        });

        //get the default image



        // TODO manage the preferences and the shared preference listenes
        // TODO and get the values already there getPrefValues(settings);
        // TODO use getPrefValues(SharedPreferences settings)

        // Fetch screen height and width,
        DisplayMetrics metrics = this.getResources().getDisplayMetrics();
        screenheight = metrics.heightPixels;
        screenwidth = metrics.widthPixels;

        try {
            setUpFileSystem();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setImage() {
        //prefer to display processed image if available
        bmpThresholded = Camera_Helpers.loadAndScaleImage(processedImagePath, screenheight, screenwidth);
        if (bmpThresholded != null) {
            myImage.setImageBitmap(bmpThresholded);
            Log.d(DEBUG_TAG, "setImage: myImage.setImageBitmap(bmpThresholded) set");
            return;
        }

        //otherwise fall back to unprocessd photo
        bmpOriginal = Camera_Helpers.loadAndScaleImage(originalImagePath, screenheight, screenwidth);
        if (bmpOriginal != null) {
            myImage.setImageBitmap(bmpOriginal);
            Log.d(DEBUG_TAG, "setImage: myImage.setImageBitmap(bmpOriginal) set");
            return;
        }

        //worst case get from default image
        //save this for restoring
        bmpOriginal = BitMap_Helpers.copyBitmap(myImage.getDrawable());
        Log.d(DEBUG_TAG, "setImage: bmpOriginal copied");
    }

    //TODO use this to set the following member preferences whenever preferences are changed.
    //TODO Please ensure that this function is called by your preference change listener
    private void getPrefValues(SharedPreferences settings) {
        //TODO should track shareSubject, shareText, saturation, bwPercent
        shareSubject = settings.getString(String.valueOf(R.string.shareTitle), null);
        shareText = settings.getString(String.valueOf(R.string.sharemessage), null);
        saturation = settings.getInt(String.valueOf(R.integer.saturation), 0);
        bwPercent = settings.getInt(String.valueOf(R.integer.bwPercent), 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    private void setUpFileSystem() throws IOException {
        // do we have needed permissions?
        // if not then dont proceed
        if (!verifyPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            return;
        }
        //get some paths
        // Create the File where the photo should go
        File photoFile = createImageFile(ORIGINAL_FILE);
        originalImagePath = photoFile.getAbsolutePath();

        File processedfile = createImageFile(PROCESSED_FILE);
        processedImagePath=processedfile.getAbsolutePath();

        //worst case get from default image
        //save this for restoring
        if (bmpOriginal == null)
            bmpOriginal = BitMap_Helpers.copyBitmap(myImage.getDrawable());

        setImage();
    }

    //TODO manage creating a file to store camera image in
    //TODO where photo is stored
    private File createImageFile(final String fn) {
        try {
            File[] storageDir = getExternalMediaDirs();
            File imagefile = new File(storageDir[0], fn);
            if (!storageDir[0].exists()) {
                if (!storageDir[0].mkdirs()) {
                    return null;
                }
            }
            imagefile.createNewFile();
            originalImagePath = imagefile.getAbsolutePath();
            return imagefile;
        } catch (IOException e) {
            return null;
        }
    }

    //DUMP for students
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    // permissions

    /***
     * callback from requestPermissions
     * @param permsRequestCode  user defined code passed to requestpermissions used to identify what callback is coming in
     * @param permissions       list of permissions requested
     * @param grantResults      //results of those requests
     */
    @Override
    public void onRequestPermissionsResult(int permsRequestCode, String[] permissions, int[] grantResults) {
        // BEGIN_INCLUDE(onRequestPermissionsResult)
        boolean counter = true;
        switch (permsRequestCode) {
            case PERMISSION_REQUEST_CODE:
                for (int result : grantResults) {
                    counter = counter && (result == PackageManager.PERMISSION_GRANTED);
                }
                break;
            }
            if (counter) {
                doTakePicture();
        }
        // END_INCLUDE(onRequestPermissionsResult)
    }


    //DUMP for students
    /**
     * Verify that the specific list of permisions requested have been granted, otherwise ask for
     * these permissions.  Note this is coarse in that I assumme I need them all
     */
    private boolean verifyPermissions(Context context, String... permissions) {

        // fill in

        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }



    //take a picture and store it on external storage
    public void doTakePicture() {
        // verify that app has permission to use camera
        if (!verifyPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            return;
        }
        //TODO manage launching intent to take a picture
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile(ORIGINAL_FILE);
            if ( photoFile != null) {
                outputFileUri = FileProvider.getUriForFile(this, "com.example.solution_color.fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
                startActivityForResult(takePictureIntent, TAKE_PICTURE);
            }
        }
    }

    //TODO manage return from camera and other activities
    // TODO handle edge cases as well (no pic taken)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //TODO get photo
        if (requestCode == TAKE_PICTURE && resultCode == RESULT_OK) {
            setImage();
            scanSavedMediaFile(originalImagePath);
        }

        //TODO set the myImage equal to the camera image returned
        //TODO tell scanner to pic up this unaltered image
        //TODO save anything needed for later

    }

    /**
     * delete original and processed images, then rescan media paths to pick up that they are gone.
     */
    private void doReset() {
        // verify that app has permission to use file system
        //do we have needed permissions?
        if (!verifyPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            return;
        }
        //delete the files
        Camera_Helpers.delSavedImage(originalImagePath);
        Camera_Helpers.delSavedImage(processedImagePath);
        bmpThresholded = null;
        bmpOriginal = null;

        myImage.setImageResource(R.drawable.gutters);
        myImage.setScaleType(ImageView.ScaleType.FIT_CENTER);//what the hell? why both
        myImage.setScaleType(ImageView.ScaleType.FIT_XY);

        //worst case get from default image
        //save this for restoring
        bmpOriginal = BitMap_Helpers.copyBitmap(myImage.getDrawable());

        //TODO make media scanner pick up that images are gone

    }

    public void doSketch() {
        // verify that app has permission to use file system
        //do we have needed permissions?
        if (!verifyPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            return;
        }

        //sketchify the image
        if (bmpOriginal == null){
            Log.e(DEBUG_TAG, "doSketch: bmpOriginal = null");
            return;
        }
        bmpThresholded = BitMap_Helpers.thresholdBmp(bmpOriginal, bwPercent);

        //set image
        myImage.setImageBitmap(bmpThresholded);

        //save to file for possible email
        Camera_Helpers.saveProcessedImage(bmpThresholded, processedImagePath);
        scanSavedMediaFile(processedImagePath);
    }

    public void doColorize() {
        // verify that app has permission to use file system
        //do we have needed permissions?
        if (!verifyPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            return;
        }

        //colorize the image
        if (bmpOriginal == null){
            Log.e(DEBUG_TAG, "doColorize: bmpOriginal = null");
            return;
        }
        //if not thresholded yet then do nothing
        if (bmpThresholded == null){
            Log.e(DEBUG_TAG, "doColorize: bmpThresholded not thresholded yet");
            return;
        }

        //otherwise color the bitmap
        bmpThresholdedColor = BitMap_Helpers.colorBmp(bmpOriginal, saturation);

        //takes the thresholded image and overlays it over the color one
        //so edges are well defined
        BitMap_Helpers.merge(bmpThresholdedColor, bmpThresholded);

        //set background to new image
        myImage.setImageBitmap(bmpThresholdedColor);

        //save to file for possible email
        Camera_Helpers.saveProcessedImage(bmpThresholdedColor, processedImagePath);
        scanSavedMediaFile(processedImagePath);
    }

    public void doShare() {
        //verify that app has permission to use file system
        //do we have needed permissions?
        if (!verifyPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            return;
        }

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setData(outputFileUri);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
        startActivity(Intent.createChooser(shareIntent, "Share"));


        //TODO share the processed image with appropriate subject, text and file URI
        //TODO the subject and text should come from the preferences set in the Settings Activity

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle all of the appbar button clicks
        int id = item.getItemId();

        switch (id) {
            case R.id.settings:
                Toast.makeText(this, "settings goes here", Toast.LENGTH_SHORT).show();
                break;
            case R.id.reset:
                doReset();
                break;
            case R.id.share:
                doShare();
                break;
            case R.id.sketchy:
                doSketch();
                break;
            case R.id.colorize:
                doColorize();
                break;
            default:
                break;
        }
        return true;
    }

    //TODO set up pref changes
    @Override
    public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1) {
        //TODO reload prefs at this point
    }

    /**
     * Notifies the OS to index the new image, so it shows up in Gallery.
     * see https://www.programcreek.com/java-api-examples/index.php?api=android.media.MediaScannerConnection
     */
    private void scanSavedMediaFile( final String path) {
        // silly array hack so closure can reference scannerConnection[0] before it's created
        final MediaScannerConnection[] scannerConnection = new MediaScannerConnection[1];
        try {
            MediaScannerConnection.MediaScannerConnectionClient scannerClient = new MediaScannerConnection.MediaScannerConnectionClient() {
                public void onMediaScannerConnected() {
                    scannerConnection[0].scanFile(path, null);
                }

                @Override
                public void onScanCompleted(String path, Uri uri) {

                }

            };
            scannerConnection[0] = new MediaScannerConnection(this, scannerClient);
            scannerConnection[0].connect();
        } catch (Exception ignored) {
        }
    }
}

