package com.dRecharge.modem;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.airbnb.lottie.LottieAnimationView;
import com.dRecharge.modem.helper.AppPermissionSupport;

/**
 * Premium Splash Screen for dRecharge Modem Application
 * Implements modern Android 12+ SplashScreen API with backward compatibility
 * Features: Lottie animation, gradient background, smooth transitions
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_DURATION = 3000; // 3 seconds
    private static final int ANIMATION_FADE_DURATION = 800;
    
    private LottieAnimationView lottieAnimation;
    private TextView appNameText;
    private TextView taglineText;
    private View loadingIndicator;
    
    private Handler mainHandler;
    private boolean isContentReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install Android 12+ splash screen (gracefully handles older versions)
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Initialize handler
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize views
        initializeViews();
        
        // Start animations
        animateContent();
        
        // Navigate after delay
        scheduleNavigation();
    }

    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        lottieAnimation = findViewById(R.id.lottie_animation);
        appNameText = findViewById(R.id.app_name_text);
        taglineText = findViewById(R.id.tagline_text);
        loadingIndicator = findViewById(R.id.loading_indicator);
        
        // Set initial alpha to 0 for fade-in effect
        lottieAnimation.setAlpha(0f);
        appNameText.setAlpha(0f);
        taglineText.setAlpha(0f);
        loadingIndicator.setAlpha(0f);
        
        // Configure Lottie animation
        configureLottieAnimation();
    }

    /**
     * Configure Lottie animation properties
     */
    private void configureLottieAnimation() {
        if (lottieAnimation != null) {
            // Set animation speed (1.0 = normal, 0.5 = half speed)
            lottieAnimation.setSpeed(0.8f);
            
            // Loop indefinitely (using ValueAnimator.INFINITE constant)
            lottieAnimation.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
            
            // Start animation
            lottieAnimation.playAnimation();
        }
    }

    /**
     * Animate content with staggered fade-in effects
     */
    private void animateContent() {
        // Animate Lottie (immediate)
        fadeInView(lottieAnimation, 0, ANIMATION_FADE_DURATION);
        
        // Animate app name (delayed)
        fadeInView(appNameText, 400, ANIMATION_FADE_DURATION);
        
        // Animate tagline (more delayed)
        fadeInView(taglineText, 600, ANIMATION_FADE_DURATION);
        
        // Animate loading indicator (last)
        fadeInView(loadingIndicator, 800, ANIMATION_FADE_DURATION);
        
        // Mark content as ready after animations complete
        mainHandler.postDelayed(() -> isContentReady = true, 1000);
    }

    /**
     * Fade in animation helper
     */
    private void fadeInView(View view, long startDelay, int duration) {
        if (view != null) {
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            fadeIn.setDuration(duration);
            fadeIn.setStartDelay(startDelay);
            fadeIn.setInterpolator(new DecelerateInterpolator());
            fadeIn.start();
        }
    }

    /**
     * Schedule navigation to main activity
     */
    private void scheduleNavigation() {
        mainHandler.postDelayed(() -> {
            // Check if content is ready (can be replaced with actual initialization checks)
            if (isContentReady) {
                navigateToNextScreen();
            } else {
                // Wait a bit more if content isn't ready
                mainHandler.postDelayed(this::navigateToNextScreen, 500);
            }
        }, SPLASH_DISPLAY_DURATION);
    }

    /**
     * Navigate to the next screen with fade-out animation
     */
    private void navigateToNextScreen() {
        // Fade out animation before navigation
        View rootView = findViewById(R.id.splash_root);
        
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(rootView, "alpha", 1f, 0f);
        fadeOut.setDuration(400);
        fadeOut.setInterpolator(new DecelerateInterpolator());
        
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // If the user already completed setup and runtime permissions are still
                // granted, go directly to MainActivity — no need to visit PermissionActivity.
                Intent intent;
                if (AppPermissionSupport.wasSetupCompletedBefore(SplashActivity.this)
                        && AppPermissionSupport.hasAllRuntimePermissions(SplashActivity.this)) {
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                } else {
                    intent = new Intent(SplashActivity.this, PermissionActivity.class);
                }
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
        
        fadeOut.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handler callbacks
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        
        // Stop Lottie animation
        if (lottieAnimation != null) {
            lottieAnimation.cancelAnimation();
        }
    }

    @Override
    public void onBackPressed() {
        // Disable back button during splash
        // Do nothing
    }
}
