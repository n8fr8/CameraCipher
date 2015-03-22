package info.guardianproject.iocipher.camera;

import info.guardianproject.cacheword.CacheWordHandler;
import info.guardianproject.cacheword.ICacheWordSubscriber;

import java.security.GeneralSecurityException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class LockScreenActivity extends Activity implements ICacheWordSubscriber {
    private static final String TAG = "LockScreenActivity";

    private EditText mEnterPassphrase;
    private EditText mNewPassphrase;
    private EditText mConfirmNewPassphrase;
    private View mViewCreatePassphrase;
    private View mViewEnterPassphrase;
    private Button mBtnOpen;
    private String mPasswordError;
    private TwoViewSlider mSlider;

    private CacheWordHandler mCacheWord;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_screen);
       
        mViewCreatePassphrase = findViewById(R.id.llCreatePassphrase);
        mViewEnterPassphrase = findViewById(R.id.llEnterPassphrase);

        mEnterPassphrase = (EditText) findViewById(R.id.editEnterPassphrase);
        mNewPassphrase = (EditText) findViewById(R.id.editNewPassphrase);
        mConfirmNewPassphrase = (EditText) findViewById(R.id.editConfirmNewPassphrase);
        ViewFlipper vf = (ViewFlipper) findViewById(R.id.viewFlipper1);
        LinearLayout flipView1 = (LinearLayout) findViewById(R.id.flipView1);
        LinearLayout flipView2 = (LinearLayout) findViewById(R.id.flipView2);

        mSlider = new TwoViewSlider(vf, flipView1, flipView2, mNewPassphrase, mConfirmNewPassphrase);
        
        mCacheWord = new CacheWordHandler(this, this);
        mCacheWord.connectToService(); 
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCacheWord.detach();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCacheWord.reattach();
    }

    @Override
    public void onCacheWordUninitialized() {
        initializePassphrase();
    }

    @Override
    public void onCacheWordLocked() {
        promptPassphrase();
    }

    @Override
    public void onCacheWordOpened() {

    	StorageManager.mountStorage(this, null, mCacheWord.getEncryptionKey());
    	
    	Intent intent = new Intent(this,GalleryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    private boolean newEqualsConfirmation() {
        return mNewPassphrase.getText().toString()
                .equals(mConfirmNewPassphrase.getText().toString());
    }

    private void showValidationError() {
        Toast.makeText(LockScreenActivity.this, mPasswordError, Toast.LENGTH_LONG).show();
        mNewPassphrase.requestFocus();
    }

    private void showInequalityError() {
        Toast.makeText(LockScreenActivity.this,
                R.string.lock_screen_passphrases_not_matching,
                Toast.LENGTH_SHORT).show();
        clearNewFields();
    }

    private void clearNewFields() {
        mNewPassphrase.getEditableText().clear();
        mConfirmNewPassphrase.getEditableText().clear();
    }

    private boolean isPasswordValid() {
    	boolean valid = mNewPassphrase.getText().toString().length() > 4;
    	if(!valid)
    		mPasswordError = getString(R.string.pass_err_length);
        return valid;
    }

    private boolean isConfirmationFieldEmpty() {
        return mConfirmNewPassphrase.getText().toString().length() == 0;
    }

    private void initializePassphrase() {
        // Passphrase is not set, so allow the user to create one

        mViewCreatePassphrase.setVisibility(View.VISIBLE);
        mViewEnterPassphrase.setVisibility(View.GONE);

        mNewPassphrase.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!isPasswordValid())
                        showValidationError();
                    else
                        mSlider.showConfirmationField();
                }
                return false;
            }
        });

        mConfirmNewPassphrase.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!newEqualsConfirmation()) {
                        showInequalityError();
                        mSlider.showNewPasswordField();
                    }
                }
                return false;
            }
        });

        Button btnCreate = (Button) findViewById(R.id.btnCreate);
        btnCreate.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v) {
                // validate
                if (!isPasswordValid()) {
                    showValidationError();
                    mSlider.showNewPasswordField();
                } else if (isConfirmationFieldEmpty()) {
                    mSlider.showConfirmationField();
                } else if (!newEqualsConfirmation()) {
                    showInequalityError();
                    mSlider.showNewPasswordField();
                } else {
                    try {
                        mCacheWord.setPassphrase(mNewPassphrase.getText().toString().toCharArray());                        
                    } catch (GeneralSecurityException e) {
                        Log.e(TAG, "Cacheword pass initialization failed: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void promptPassphrase() {
        mViewCreatePassphrase.setVisibility(View.GONE);
        mViewEnterPassphrase.setVisibility(View.VISIBLE);

        mBtnOpen = (Button) findViewById(R.id.btnOpen);
        mBtnOpen.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEnterPassphrase.getText().toString().length() == 0)
                    return;
                // Check passphrase
                try {
                    mCacheWord.setPassphrase(mEnterPassphrase.getText().toString().toCharArray());
                } catch (GeneralSecurityException e) {
                    mEnterPassphrase.setText("");
                    Log.e(TAG, "Cacheword pass verification failed: " + e.getMessage());
                    return;
                }
            }
        });

        mEnterPassphrase.setOnEditorActionListener(new OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
            {
                if (actionId == EditorInfo.IME_NULL || actionId == EditorInfo.IME_ACTION_GO)
                {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    Handler threadHandler = new Handler();
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0, new ResultReceiver(
                            threadHandler)
                    {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            super.onReceiveResult(resultCode, resultData);
                            mBtnOpen.performClick();
                        }
                    });
                    return true;
                }
                return false;
            }
        });
    }

    public class TwoViewSlider {

        private boolean firstIsShown = true;
        private ViewFlipper flipper;
        private LinearLayout container1;
        private LinearLayout container2;
        private View firstView;
        private View secondView;
        
        public TwoViewSlider(ViewFlipper flipper, LinearLayout container1, LinearLayout container2,
                View view1, View view2) {
            this.flipper = flipper;
            this.container1 = container1;
            this.container2 = container2;
            this.firstView = view1;
            this.secondView = view2;

        }

        public void showNewPasswordField() {
            if (firstIsShown)
                return;

            flip();
        }

        public void showConfirmationField() {
            if (!firstIsShown)
                return;

            flip();
        }

        private void flip() {
            if (firstIsShown) {
                firstIsShown = false;
                container2.removeAllViews();
                container2.addView(secondView);
            } else {
                firstIsShown = true;
                container1.removeAllViews();
                container1.addView(firstView);
            }
            flipper.showNext();
        }
    }
}
