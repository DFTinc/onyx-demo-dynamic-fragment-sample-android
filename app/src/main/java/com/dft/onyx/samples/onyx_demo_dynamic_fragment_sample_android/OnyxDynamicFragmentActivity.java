
package com.dft.onyx.samples.onyx_demo_dynamic_fragment_sample_android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.dft.onyx.FingerprintTemplate;
import com.dft.onyx.core;
import com.dft.onyx.enroll.util.CaptureAnimationCallbackUtil;
import com.dft.onyxcamera.licensing.License;
import com.dft.onyxcamera.licensing.LicenseException;
import com.dft.onyxcamera.ui.CaptureConfiguration;
import com.dft.onyxcamera.ui.CaptureConfigurationBuilder;
import com.dft.onyxcamera.ui.CaptureMetrics;
import com.dft.onyxcamera.ui.OnyxFragment;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

public class OnyxDynamicFragmentActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_container);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new OnyxDynamicFragment()).commit();
        }
    }

    /**
     * A simple {@link android.app.Fragment} subclass.
     */
    public static class OnyxDynamicFragment extends Fragment {
        private final static String TAG = OnyxDynamicFragment.class.getSimpleName();
        private final static String ENROLL_FILENAME = "enrolled_template.bin";

        private ImageView mFingerprintView;
        private Animation mFadeIn;
        private Animation mFadeOut;
        private OnyxFragment mFragment;
        private FingerprintTemplate mCurrentTemplate = null;
        private double mCurrentFocusQuality = 0.0;
        private FingerprintTemplate mEnrolledTemplate = null;

        static {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "Unable to load OpenCV!");
            } else {
                Log.i(TAG, "OpenCV loaded successfully");
                core.initOnyx();
            }
        }

        public OnyxDynamicFragment() {
            // Required empty public constructor
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            if (null != getActivity().getActionBar()) {
                getActivity().getActionBar().hide();
            }
            View v = inflater.inflate(R.layout.base_layout, container, false);
            return v;
        }

        @Override
        public void onResume() {
            super.onResume();
            // Handle the Onyx licensing
            License lic = License.getInstance(getActivity());
            try {
                lic.validate(getString(R.string.onyx_license));
            } catch (LicenseException e) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("License error")
                        .setMessage(e.getMessage())
                        .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                builder.create().show();
            }
            // Load up an enrolled fingerprint template if one exists
            loadEnrolledTemplateIfExists();

            // Add the OnyxFragment programatically
            mFragment = new OnyxFragment();
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_content, mFragment, TAG);
            ft.commit();

            CaptureConfiguration captureConfig = new CaptureConfigurationBuilder()
                    .setProcessedBitmapCallback(mProcessedCallback)
                    .setWsqCallback(mWsqCallback)
                    .setFingerprintTemplateCallback(mTemplateCallback)
                    .setShouldInvert(true)
                    .setFlip(CaptureConfiguration.Flip.VERTICAL)
                    .buildCaptureConfiguration();
            mFragment.setCaptureConfiguration(captureConfig);
            mFragment.setErrorCallback(mErrorCallback);
            mFragment.startOneShotAutoCapture();

            createFadeInAnimation();
            createFadeOutAnimation();

            LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            mFingerprintView = new ImageView(getActivity());
            getActivity().addContentView(mFingerprintView, layoutParams);
        }

        /**
         * You can then specify the callbacks you'd like to receive
         */
        // The CaptureanimationCallbackUtil creates the spinner animation that shows during
        // the capture processed
        private OnyxFragment.CaptureAnimationCallback mCaptureAnimationCallback =
                new CaptureAnimationCallbackUtil().createCaptureAnimationCallback(getActivity());

        private OnyxFragment.ProcessedBitmapCallback mProcessedCallback =
                new OnyxFragment.ProcessedBitmapCallback() {

            @Override
            /**
             * This method handles the ProcessedBitmapReady event.
             * @param processedBitmap the Bitmap containing the processed fingerprint image.
             * @param metrics the metrics associated with this fingerprint image capture.
             */
            public void onProcessedBitmapReady(Bitmap processedBitmap, CaptureMetrics metrics) {
                mCurrentFocusQuality = metrics.getFocusQuality();
                mFingerprintView.setImageBitmap(processedBitmap);
                mFingerprintView.startAnimation(mFadeIn);
            }
        };

        private OnyxFragment.WsqCallback mWsqCallback = new OnyxFragment.WsqCallback() {

            @Override
            /**
             * This method handles the WsqReady event.
             * @param wsqData a byte array containing the compressed WSQ data of the fingerprint image.
             * @param metrics the metrics associated with this fingerprint image capture.
             */
            public void onWsqReady(byte[] wsqData, CaptureMetrics metrics) {
                // TODO Do something with WSQ data
                Log.d(TAG, "NFIQ: " + metrics.getNfiqMetrics().getNfiqScore() + ", MLP: " +
                        metrics.getNfiqMetrics().getMlpScore());
            }

        };

        /**
         * Finally, the callback FingerprintTemplateCallback() returns the generated
         * fingerprint template that is used for matching.
         */
        private OnyxFragment.FingerprintTemplateCallback mTemplateCallback =
                new OnyxFragment.FingerprintTemplateCallback() {

            @Override
            /**
             * This method handles the FingerprintTemplateReady event.
             * @param fingerprintTemplate the returned fingerprint template used for matching purposes.
             */
            public void onFingerprintTemplateReady(FingerprintTemplate fingerprintTemplate) {
                mCurrentTemplate = fingerprintTemplate;

                Log.d(TAG, "Template quality: " + mCurrentTemplate.getQuality());

                if (mEnrolledTemplate != null) {
                    VerifyTask verifyTask = new VerifyTask(getActivity());
                    verifyTask.execute(mCurrentTemplate, mEnrolledTemplate);
                }
            }

        };

        private OnyxFragment.ErrorCallback mErrorCallback = new OnyxFragment.ErrorCallback() {

            @Override
            /**
             * This method handles the errors that can be produced by the OnyxFragment.
             * @param error the specific error enumeration that occurred.
             * @param errorMessage the associated error message.
             * @param exception if not null, this is the exception that occurred.
             */
            public void onError(Error error, String errorMessage, Exception exception) {
                switch (error) {
                    case AUTOFOCUS_FAILURE:
                        mFragment.startOneShotAutoCapture();
                        break;
                    default:
                        Log.d(TAG, "Error occurred: " + errorMessage);
                        break;
                }
            }

        };

        private void createFadeInAnimation() {
            mFadeIn = new AlphaAnimation(0.0f, 1.0f);
            mFadeIn.setDuration(500);
            mFadeIn.setAnimationListener(new AnimationListener() {

                @Override
                public void onAnimationEnd(Animation animation) {
                    new CountDownTimer(1000, 1000) {

                        @Override
                        public void onFinish() {
                            mFingerprintView.startAnimation(mFadeOut);
                        }

                        @Override
                        public void onTick(long millisUntilFinished) {
                        }

                    }.start();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationStart(Animation animation) {
                    mFingerprintView.setVisibility(View.VISIBLE);
                }
            });
        }

        private void createFadeOutAnimation() {
            mFadeOut = new AlphaAnimation(1.0f, 0.0f);
            mFadeOut.setDuration(500);
            mFadeOut.setAnimationListener(new AnimationListener() {

                @Override
                public void onAnimationEnd(Animation animation) {
                    mFingerprintView.setVisibility(View.INVISIBLE);
                    if (mEnrolledTemplate == null) {
                        createEnrollQuestionDialog();
                    } else {
                        mFragment.startOneShotAutoCapture();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationStart(Animation animation) {
                }
            });
        }

        private void createEnrollQuestionDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.enroll_title);
            String enrollQuestion = getResources().getString(R.string.enroll_question);
            builder.setMessage(enrollQuestion + "\n\n" +
                    "(Quality is " + (int) mCurrentFocusQuality + ")");
            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    enrollCurrentTemplate();
                    dialog.dismiss();
                    mFragment.startOneShotAutoCapture();
                }
            });

            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    mFragment.startOneShotAutoCapture();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        }

        private void enrollCurrentTemplate() {
            mEnrolledTemplate = mCurrentTemplate;
            deleteEnrolledTemplateIfExists();

            try {
                FileOutputStream enrollStream = getActivity().openFileOutput(ENROLL_FILENAME, MODE_PRIVATE);
                ObjectOutputStream oos = new ObjectOutputStream(enrollStream);
                oos.writeObject(mEnrolledTemplate);
                oos.close();
            } catch (FileNotFoundException e) {
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        private void deleteEnrolledTemplateIfExists() {
            File enrolledFile = getActivity().getFileStreamPath(ENROLL_FILENAME);
            if (enrolledFile.exists()) {
                enrolledFile.delete();
            }
        }

        /**
         * This method loads the fingerprint template if it exists.
         */
        private void loadEnrolledTemplateIfExists() {
            File enrolledFile = getActivity().getFileStreamPath(ENROLL_FILENAME);
            if (enrolledFile.exists()) {
                try {
                    FileInputStream enrollStream = getActivity().openFileInput(ENROLL_FILENAME);
                    ObjectInputStream ois = new ObjectInputStream(enrollStream);
                    mEnrolledTemplate = (FingerprintTemplate) ois.readObject();
                } catch (FileNotFoundException e) {
                    Log.e(TAG, e.getMessage());
                } catch (StreamCorruptedException e) {
                    Log.e(TAG, e.getMessage());
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }

        @Override
        /**
         * Handles the Settings Menu options.
         * @param item the MenuItem selected by the user.
         * @return true if selection was valid, false otherwise.
         */
        public boolean onOptionsItemSelected(MenuItem item) {
            // Handle item selection
            switch (item.getItemId()) {
                case R.id.clear_enrollment:
                    menuClearEnrollment();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        /**
         * This method clears the currently enrolled fingerprint template.
         */
        private void menuClearEnrollment() {
            Toast.makeText(getActivity(), "Clearing enrolled fingerprint.", Toast.LENGTH_SHORT).show();
            mEnrolledTemplate = null;
            deleteEnrolledTemplateIfExists();
        }
    }

    /**
     * Creates the default Settings menu.
     * @return true when the menu is successfully created.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }
}
